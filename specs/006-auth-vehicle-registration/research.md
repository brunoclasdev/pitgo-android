# Phase 0 Research: Sessão autenticada e cadastro de veículos

## 1. Estado central de sessão

**Decision**: criar um `SessionRepository` process-scoped, exposto por um `SessionViewModel` no
nível do `PitGoNavHost`. O estado raiz será selado e mutuamente exclusivo: `Initializing`,
`Verifying`, `Unauthenticated`, `OfflineVerification`, `ProfileBlocked` e `Authenticated`. Apenas
`Authenticated` carrega o `AccountSnapshot` autorizado; os demais estados não retêm dados pessoais
renderizáveis.

O repositório será a única fronteira que chama Supabase Auth para inicialização, login, validação,
renovação e logout. Login, cadastro e destinos privados usarão essa mesma instância através do
container da aplicação. O SDK não decidirá a navegação diretamente: seus estados serão traduzidos
para o modelo do aplicativo.

**Rationale**: a constituição exige autenticação centralizada e proíbe verificações independentes
por tela. Uma única máquina de estados impede combinações como Home visível durante validação ou
perfil antigo após logout. A versão instalada do SDK oferece `awaitInitialization()`,
`sessionStatus`, `retrieveUserForCurrentSession()` e `clearSession()`, suficientes para essa
fronteira.

**Alternatives considered**:

- verificar `currentSessionOrNull()` em cada ViewModel: rejeitado por duplicar regras e permitir
  divergência entre telas;
- usar apenas a rota inicial estática `Welcome`: rejeitado porque produz flash do fluxo não
  autenticado durante restauração;
- adicionar Hilt/Koin: rejeitado porque um container manual pequeno resolve as dependências atuais.

## 2. Restauração, validação remota e logout

**Decision**: na inicialização, aguardar o carregamento local do Auth e, havendo sessão, validá-la
remotamente antes de liberar conteúdo. Depois da identidade válida, carregar perfil e veículo
principal. Uma falha de rede leva a `OfflineVerification`, preserva a sessão local e oferece retry;
401/sessão ausente leva a `Unauthenticated`; perfil ausente, inativo ou inconsistente leva a
`ProfileBlocked`, com retry e logout, sem escrita corretiva.

Depois de autenticado, observar `SessionStatus.NotAuthenticated` e `RefreshFailure`. Qualquer
transição que retire a autorização elimina imediatamente o snapshot da memória e retira os destinos
privados da interface. Logout usa explicitamente o escopo local; mesmo que a revogação remota falhe
por ausência de rede, a sessão local e todos os estados personalizados são limpos.

**Rationale**: satisfaz as clarificações sem tratar uma sessão armazenada como autorização
suficiente. O SDK 3.2.6 persiste e atualiza a sessão automaticamente, mas a validação do perfil
continua sendo regra do aplicativo. Limpar localmente no logout garante a ação do usuário sem
depender da rede.

**Alternatives considered**:

- abrir Home com dados em cache durante offline: rejeitado pela clarificação;
- fazer logout automático quando a rede falhar: rejeitado porque destrói uma sessão potencialmente
  válida;
- recriar perfil a partir de `raw_user_meta_data`: rejeitado porque metadata do usuário não é fonte
  autoritativa e a clarificação proíbe correção automática.

## 3. Bootstrap da conta e Home

**Decision**: separar a autenticação da leitura dos dados. `AccountRepository` consulta o perfil
exato do `auth.users.id` e o único veículo ativo principal. Após a validação remota da identidade,
perfil e veículo podem ser buscados em paralelo com filtros explícitos de `id`/`user_id`, além do
RLS. O resultado forma `AccountSnapshot(profile, primaryVehicle?)` usado pela sessão e pela Home.

A Home deixa de aceitar modelos demonstrativos de pessoa/veículo. Seus estados serão `Loading`,
`Content`, `NoVehicle` e `Failure`; endereço será sempre o texto neutro definido na spec. Retorno da
Garagem provoca refresh do snapshot, evitando Realtime nesta entrega.

**Rationale**: duas consultas simples mantêm mapeamento de falhas claro e atendem a meta de dois
segundos sem uma seleção relacional PostgREST frágil. Filtrar no cliente e no RLS segue defesa em
profundidade. Atualizar ao retomar a Home é suficiente para o fluxo de navegação solicitado.

**Alternatives considered**:

- manter `DemoHomeProfile`/`DemoHomeVehicle`: rejeitado por risco de mostrar dados falsos;
- instalar Realtime para sincronizar Home e Garagem: rejeitado por não haver atualização externa
  contínua nesta feature;
- criar cache de perfil/veículo: rejeitado porque a experiência offline foi explicitamente
  descartada e cache acrescentaria invalidação e risco de privacidade.

## 4. Convite pós-cadastro persistente

**Decision**: usar Preferences DataStore 1.2.1, com uma única instância no processo, para manter um
conjunto de identificadores de usuário cujo convite está pendente. O cadastro só conclui depois de
registrar o `userId` como pendente. Escolher qualquer uma das duas ações remove esse identificador
antes da navegação. Contas existentes que nunca foram marcadas não recebem convite retroativo.

O convite será um destino dedicado, sem conteúdo da Home por trás e sem fechamento silencioso pelo
botão Voltar. Escolher cadastrar resolve a decisão e abre o formulário; se o formulário for
abandonado depois, a Home fica disponível com estado sem veículo.

**Rationale**: DataStore é a solução constitucional para preferência simples e sobrevive a
recriação de Activity/processo sem adicionar coluna ao schema de negócio. Um conjunto, em vez de um
único UUID, preserva corretamente decisões pendentes de contas diferentes usadas no mesmo aparelho.

**Alternatives considered**:

- inferir pendência pela ausência de veículos: rejeitado porque usuários antigos sem carro
  receberiam onboarding indevido;
- coluna nova em `profiles`: rejeitada porque a clarificação exige persistência no aplicativo, não
  sincronização entre dispositivos;
- `rememberSaveable`/estado do NavHost: rejeitado porque não sobrevive ao encerramento do processo.

## 5. Operações atômicas de veículo

**Decision**: adicionar uma migração incremental que reconcilia a tabela `vehicles` já criada no
projeto remoto e passa a versioná-la no repositório. Escritas diretas de `authenticated` serão
revogadas; apenas SELECT próprio permanece. Duas funções RPC `SECURITY DEFINER`, com
`search_path = ''`, objetos totalmente qualificados, validação de `auth.uid()` e EXECUTE somente
para `authenticated`, executarão:

1. `create_vehicle`: normaliza/valida a entrada, serializa operações do mesmo usuário com
   `pg_advisory_xact_lock`, detecta se é o primeiro veículo, remove o principal anterior quando
   solicitado e insere exatamente um veículo ativo;
2. `set_primary_vehicle`: serializa pelo usuário, confirma que o alvo ativo pertence à sessão,
   desmarca o principal anterior e marca o alvo na mesma transação.

O índice parcial de principal e as constraints continuam como última barreira. A migração também
garante um único trigger seguro de criação de perfil e usa exclusivamente a coluna `active`.

**Rationale**: duas chamadas PostgREST independentes poderiam falhar entre desmarcar e marcar,
deixando zero principal. Uma função PostgreSQL é transacional, e o lock por usuário cobre dois taps
ou dois dispositivos concorrentes. `SECURITY DEFINER` é justificado porque as escritas diretas
ficam fechadas; a função aplica explicitamente identidade, propriedade e campos controlados.

**Alternatives considered**:

- `update antigo` seguido de `insert/update novo` no Android: rejeitado por não ser atômico;
- permitir INSERT/UPDATE direto com RLS: rejeitado porque um cliente poderia ignorar a regra do
  primeiro principal;
- Edge Function: rejeitada porque adiciona implantação e credencial de servidor sem melhorar a
  atomicidade já oferecida pelo PostgreSQL.

## 6. Contrato e validação dos veículos

**Decision**: manter no estado do formulário somente texto digitado em memória; nenhum rascunho vai
para SavedState/DataStore. No submit, produzir `VehicleDraft` normalizado: marca/modelo com trim;
opcionais vazios como null; placa e chassi em caixa alta; placa sem separador visual; Renavam apenas
dígitos preservando zeros; anos/quilometragem convertidos com segurança.

Validar localmente marca/modelo, placa brasileira legada ou Mercosul, Renavam com 11 dígitos,
chassi VIN com 17 caracteres sem I/O/Q, anos de 1900 até o próximo ano e quilometragem não negativa.
O RPC repete as invariantes decisivas. Conflitos `23505` só serão classificados pelos nomes
allowlisted `vehicles_plate_unique`, `vehicles_renavam_unique` e `vehicles_chassis_unique`; detalhes
e valores das exceções nunca serão registrados.

**Rationale**: estado normalizado evita diferenças entre colagem e máscara; validação dupla trata o
cliente como não confiável. O descarte integral do ViewModel quando a sessão expira cumpre a decisão
de privacidade.

**Alternatives considered**:

- armazenar a máscara no banco: rejeitado por tornar unicidade inconsistente;
- validar somente no Android: rejeitado porque requisições podem contornar o aplicativo;
- persistir o rascunho para recuperação: rejeitado pela clarificação explícita.

## 7. Navegação e superfícies Compose

**Decision**: centralizar novas rotas para gate de sessão, login, convite, Garagem, novo veículo e
Perfil. Login é um formulário Material com e-mail/senha, loading e falhas tipadas. Garagem usa
`LazyColumn`, estado vazio, lista de cards e seleção acessível de principal. O formulário de veículo
usa `Scaffold`, `TopAppBar`, uma única lista rolável com `imePadding`, campos Material e CTA de 56dp.
Perfil é a superfície mínima necessária para mostrar a conta atual e a ação `Sair`.

Home, Garagem e Perfil reutilizam a mesma bottom navigation; somente itens implementados navegam
nesta entrega. A origem do formulário (`ONBOARDING` ou `GARAGE`) é o único argumento não sensível;
`userId` sempre vem da sessão. Estados privados são guardados pelo estado central, não apenas pela
pilha de navegação.

**Rationale**: preserva o design system existente, torna logout e Garagem descobertos e evita
passar identidade pela rota. Uma lista rolável única é previsível com teclado e fonte ampliada.

**Alternatives considered**:

- abrir logout por menu oculto no avatar: rejeitado por baixa descoberta;
- duplicar a bottom navigation por tela: rejeitado por risco de divergência;
- passar `userId` como argumento: rejeitado porque a identidade deve vir exclusivamente da sessão.

## 8. Avatar remoto

**Decision**: usar Coil 3.3.0 (`coil-compose` + `coil-network-ktor3`) apenas quando `avatar_url` for
não vazio, com dimensões limitadas, cache padrão, placeholder e fallback imediato para iniciais. A
versão 3.3.0 foi compilada com Kotlin 2.2.0 e é compatível com o compilador 2.2.10 atual; Coil 3.5.0
foi compilado com Kotlin 2.4.0 e exigiria upgrade amplo fora do escopo.

**Rationale**: a constituição exige carregamento assíncrono/cache/fallback para imagem remota, e a
spec exige usar avatar existente. Coil evita reimplementar download, cancelamento e cache.

**Alternatives considered**:

- ignorar `avatar_url`: rejeitado por FR-008;
- carregar bitmap manualmente com Ktor: rejeitado por duplicar pipeline, cache e lifecycle;
- atualizar todo o toolchain para Coil 3.5.0: rejeitado pelo risco não relacionado à feature.

## 9. Persistência e backup da sessão

**Decision**: manter o `SessionManager` padrão do Supabase Auth, que salva a sessão em
SharedPreferences privados do aplicativo, mas excluir `sharedpref` de cloud backup e device transfer
nas duas versões das regras Android. DataStore do onboarding não contém token, senha, documento ou
rascunho. Nenhum Throwable do SDK será enviado a log/crash reporting; somente categorias de domínio
e códigos allowlisted podem ser registrados em debug.

**Rationale**: o SDK já implementa rotação e restauração da sessão. Excluir seu armazenamento de
backup impede que tokens sejam transferidos para outra instalação; a própria documentação Android
inclui SharedPreferences no backup por padrão.

**Alternatives considered**:

- aceitar o backup padrão: rejeitado pelo risco de restaurar token emitido para outra instalação;
- criar criptografia/Keystore própria nesta feature: rejeitado porque substituir o SessionManager
  oficial amplia muito o escopo; armazenamento privado sem backup e tokens curtos/renováveis é a
  solução proporcional atual;
- persistir senha para reautenticação: rejeitado; senha existe apenas na memória do formulário.

## 10. Migração e validação do ambiente Supabase

**Decision**: tratar a migração como incremental e conciliadora porque a tabela `vehicles` foi
criada manualmente no projeto remoto, enquanto o repositório ainda não a contém. A implementação
deve capturar/revisar o estado remoto antes de `db push`, manter nomes existentes quando seguros e
adicionar tabela/constraints/functions/policies de forma repetível para uma base local limpa. Não
executar migração remota automaticamente.

O CLI Supabase não está instalado na máquina atual; `npx supabase` ou instalação oficial será
pré-requisito do fluxo local. A validação Android permanece sem testes automatizados, conforme a
constituição, usando assemble/lint e cenários manuais.

**Rationale**: aplicar um `CREATE TABLE` simples contra a base já configurada falharia e poderia
deixar histórico divergente. Reconciliar antes preserva os dados existentes e transforma migrations
em fonte de verdade daqui em diante.

**Alternatives considered**:

- continuar mantendo SQL somente no Dashboard: rejeitado por não ser reproduzível;
- apagar/recriar tabelas remotas: rejeitado por ser destrutivo e desnecessário;
- aplicar `db push` durante implementação sem revisão: rejeitado porque é mudança externa material.

## Primary sources

- Supabase Kotlin Auth 3.2.6 (`Auth`, persistência e status):
  https://github.com/supabase-community/supabase-kt/tree/3.2.6/Auth
- Supabase Kotlin login/logout/session events:
  https://supabase.com/docs/reference/kotlin/v2/auth-api
- Supabase Kotlin PostgREST select:
  https://supabase.com/docs/reference/kotlin/select
- Supabase Database Functions e segurança de funções:
  https://supabase.com/docs/guides/database/functions
- Supabase RLS e grants:
  https://supabase.com/docs/guides/database/postgres/row-level-security
- PostgreSQL locks transacionais:
  https://www.postgresql.org/docs/current/explicit-locking.html
- Android Preferences DataStore 1.2.1:
  https://developer.android.com/jetpack/androidx/releases/datastore
- Android Auto Backup e regras de exclusão:
  https://developer.android.com/identity/data/autobackup
- Coil Compose e compatibilidade 3.3.0:
  https://coil-kt.github.io/coil/compose/ e
  https://github.com/coil-kt/coil/blob/3.3.0/gradle/libs.versions.toml
