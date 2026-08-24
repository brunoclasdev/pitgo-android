# Research: Corrigir cadastro de veículos e erros por campo

## 1. Restauração aditiva das RPCs

**Decision**: criar `supabase/migrations/20260823000000_restore_vehicle_rpcs.sql` com
`create or replace function` para as assinaturas existentes de `create_vehicle` e
`set_primary_vehicle`. A migration também reaplica os `REVOKE`/`GRANT` específicos e não modifica
`20260819010000_auth_session_vehicles.sql`. Ao final, envia `NOTIFY pgrst, 'reload schema'` para
que a API descarte o cache da assinatura restaurada.

**Rationale**: o código Android já chama essas assinaturas e a migration histórica as contém, mas
o ambiente remoto responde que `create_vehicle` não existe. Uma migration nova expressa o estado
desejado para ambientes que aplicaram a história parcialmente e preserva auditoria/reprodutibilidade.
O Supabase documenta `create or replace function` para gerenciar funções de banco e recomenda
controle explícito do privilégio de execução.

**Alternatives considered**:
- Editar a migration antiga: rejeitado porque ambientes que já a registraram não reaplicarão a
  alteração e o histórico deixaria de representar o que foi executado.
- Fazer `insert`/`update` direto pelo APK: rejeitado porque amplia grants, duplica transação e
  permite ownership manipulável.
- Executar SQL somente no Dashboard: rejeitado porque não torna a correção reproduzível.

## 2. Autorização e ownership dentro das funções

**Decision**: as duas funções exigem `auth.uid()` não nulo, `private.has_account_type('CLIENT')` e
um `public.profiles` ativo com o mesmo ID. `create_vehicle` nunca recebe `user_id`; o ID da sessão é
usado no `insert`. `set_primary_vehicle` só seleciona veículo ativo cujo `user_id` seja o chamador.
Ambas usam `security definer set search_path = ''`, referências totalmente qualificadas e execução
revogada de `public`/`anon`, com `grant execute` somente para `authenticated`.

**Rationale**: `account_roles` é a autoridade atual para distinguir CLIENT de PROVIDER; consultar
apenas `profiles` manteria um contrato legado implícito. A checagem interna é obrigatória porque
`SECURITY DEFINER` pode ignorar as permissões do chamador. A orientação oficial do Supabase exige
`search_path` seguro e grants explícitos para funções definer.

**Alternatives considered**:
- Confiar apenas no gate de navegação: rejeitado porque o APK e as rotas não são fronteiras de
  segurança.
- Aceitar `user_id` como parâmetro e comparar com JWT: rejeitado porque é dado redundante e
  manipulável.
- Usar `service_role` no app: rejeitado porque contorna RLS e expõe credencial administrativa.

## 3. Unicidade do veículo principal

**Decision**: adquirir `pg_advisory_xact_lock` por `auth.uid()` antes de calcular ou trocar o
principal. Em criação, o novo veículo será principal quando for o primeiro ativo ou quando
`p_make_primary=true`; nesse caso, os anteriores são desmarcados dentro da mesma transação. Em
`set_primary_vehicle`, o veículo próprio é bloqueado/validado, os demais são desmarcados e o alvo é
marcado. O índice parcial `one_primary_vehicle_per_user` continua como defesa estrutural.

**Rationale**: o lock serializa duas criações/trocas concorrentes da mesma conta, enquanto a
transação reverte a desmarcação caso o insert/update posterior falhe. Constraints e índice
protegem também outros escritores do banco.

**Alternatives considered**:
- Apenas confiar no índice único: rejeitado porque uma troca pode falhar no meio da sequência e
  devolver uma experiência evitável de conflito.
- Lock global: rejeitado por serializar contas independentes.
- Duas chamadas RPC no Android: rejeitado por criar janela sem principal ou com resultado parcial.

## 4. Normalização e validação definitiva

**Decision**: normalizar no Android para UX e repetir no SQL como fronteira definitiva. Marca e
modelo usam `btrim` e não podem ficar vazios. Opcionais textuais viram `null` quando vazios. Placa
é armazenada sem separadores e em maiúsculas, aceitando `AAA9999` e `AAA9A99`; Renavam aceita
exatamente 11 dígitos; chassi usa 17 caracteres maiúsculos sem I, O ou Q; anos ficam entre 1900 e
`extract(year from current_date)+1`; quilometragem é inteira não negativa.

**Rationale**: validação local reduz round trips e permite mensagens imediatas, mas o banco não
pode confiar no cliente. A mesma representação canônica também torna os índices únicos de placa,
Renavam e chassi previsíveis. O máximo de ano é calculado em cada tentativa para não envelhecer.

**Alternatives considered**:
- Validar apenas no app: rejeitado porque outros clientes/RPCs poderiam gravar dados inválidos.
- Persistir strings vazias: rejeitado porque mistura ausência com valor e conflita com a spec.
- Validar dígito do Renavam: rejeitado porque o requisito atual pede apenas 11 dígitos.

## 5. Contrato estável de erros remotos

**Decision**: a função usa SQLSTATEs próprios fechados para cada entrada esperada: `P2001` Marca,
`P2002` Modelo, `P2003` Placa, `P2004` Renavam, `P2005` Chassi, `P2006` Ano de fabricação,
`P2007` Ano do modelo e `P2008` Quilometragem. Conflitos únicos são capturados dentro da função;
`GET STACKED DIAGNOSTICS ... CONSTRAINT_NAME` compara somente as três constraints permitidas e
relança `P2011` Placa, `P2012` Renavam ou `P2013` Chassi sem detalhes do valor. O Repository
classifica somente os códigos permitidos; qualquer `23505` não traduzido permanece falha geral.

`PGRST202`, `PGRST203`, `42883` ou a resposta equivalente de função ausente/incompatível tornam-se
`RpcUnavailable`; `401`/`403`/`42501`, timeout, offline e 5xx continuam falhas gerais. O objeto de
exceção, mensagem, detalhes, hint, URL e payload nunca são enviados à UI nem registrados.

**Rationale**: códigos próprios eliminam parsing de texto livre e permitem feedback específico
sem revelar dados ou mensagens técnicas. Traduzir `unique_violation` no servidor também impede que
o detalhe padrão do PostgreSQL, que pode conter o identificador completo, atravesse a fronteira.
Falha de contrato da RPC é operacional, não erro do formulário.

**Alternatives considered**:
- Mostrar `PostgrestRestException`: rejeitado por expor detalhes internos e potencialmente dados
  dos identificadores.
- Marcar todos os erros `22023` como erro geral: rejeitado porque descumpre o feedback por campo.
- Inferir campo por qualquer substring: rejeitado porque mensagens inesperadas poderiam ser
  classificadas incorretamente.

## 6. Estado e correção progressiva do formulário

**Decision**: manter `VehicleFormOperation` como estado mutuamente exclusivo e ampliar
`VehicleMutationResult` para carregar `FieldFailure(field, error)`. A submissão valida todos os
campos em ordem visual, grava o mapa completo de erros e incrementa um `focusRequestId`. Durante
`Submitting`, eventos e novos submits são no-op. Ao editar um campo que já possui erro, o
ViewModel revalida apenas o resultado daquele campo: substitui o tipo do erro se ainda inválido e
só o remove quando o valor se torna válido. Dados e erros alheios permanecem.

**Rationale**: o estado atual já usa `StateFlow`, mas remove o erro assim que qualquer caractere é
digitado. Revalidar somente campos já tocados evita mensagens prematuras e cumpre a permanência do
erro até correção. Não há razão para introduzir reducer/MVI ou persistir o formulário.

**Alternatives considered**:
- Limpar erro em todo `onValueChange`: rejeitado porque um valor ainda inválido parece corrigido.
- Validar e mostrar todos os campos a cada tecla: rejeitado por ruído antes da primeira tentativa.
- Guardar draft no `SavedStateHandle`: rejeitado por não haver requisito de restauração após morte
  de processo e por ampliar persistência de identificadores.

## 7. Rolagem, foco e mensagens acessíveis

**Decision**: manter o formulário compartilhado atual e associar um `BringIntoViewRequester` e um
`FocusRequester` a cada `VehicleField`. Quando `focusRequestId` muda, a tela solicita foco e traz o
controle para a viewport depois que o layout está pronto. Cada `OutlinedTextField` usa `isError`,
supporting text específico e semântica `error`; o banner geral usa live region. O botão continua
desabilitado e com progresso durante o salvamento. A linha de principal usa um único alvo
`toggleable`, e o back não descarta o destino durante `Submitting`.

**Rationale**: pedir foco em um controle fora da viewport sem trazê-lo explicitamente não garante
visibilidade. Todos os campos já são compostos no item atual, então `BringIntoViewRequester`
cumpre o requisito com menor refatoração que remodelar toda a lista. Material já oferece borda,
supporting text e acessibilidade necessários.

**Alternatives considered**:
- Dividir o formulário em uma nova estrutura de linhas/índices: rejeitado por ampliar o diff sem
  benefício, já que os requesters resolvem foco/visibilidade na composição existente.
- Snackbar para erro de campo: rejeitado por ser efêmero e não localizar o problema.
- Criar framework genérico de formulários: rejeitado por YAGNI.

## 8. Conclusão e navegação por origem

**Decision**: a ação “Cadastrar veículo” da Route navega diretamente e não resolve a pendência nem
grava um evento persistente no estado do `VehicleOnboardingViewModel`; o ViewModel continua dono
apenas da operação de pular/resolver. A pendência de cadastro é resolvida após `create_vehicle`
retornar sucesso (ou no fluxo explícito de pular). Só depois o formulário entra em `Success`. O
onboarding abre o formulário com
`navigate`, permitindo voltar antes do sucesso. Para `ONBOARDING`, o NavHost navega com raiz para
Home; para `GARAGE`, faz `popBackStack()` para a Garagem, cujo `ON_RESUME` recarrega a lista. O
formulário concluído sai da pilha nos dois casos. O `SessionRepository` aceita uma atualização
local restrita do `primaryVehicle` somente com a linha autoritativa retornada pela RPC, desde que
pertença ao cliente atual e esteja ativa/principal; `GarageViewModel` aplica a mesma projeção após
`set_primary_vehicle`, mantendo a Home atualizada sem colocar a sessão global em `Verifying`.

**Rationale**: hoje o onboarding é marcado como concluído antes do salvamento e o callback de
sucesso apenas solicita refresh, sem garantir navegação para Home. Associar a resolução ao sucesso
preserva a regra de negócio e a origem tipada já existente. O gate também considera onboarding
concluído quando o snapshot remoto já contém veículo principal, recuperando commit confirmado
seguido de encerramento do processo antes da limpeza local do DataStore.

**Alternatives considered**:
- Resolver antes de abrir o formulário: rejeitado porque voltar ou falhar deixa onboarding
  falsamente concluído.
- Sempre navegar para Home ou sempre para Garagem: rejeitado pela clarificação de produto.
- Fazer Repository de veículo controlar navegação/DataStore: rejeitado por misturar persistência
  remota com apresentação e fluxo.
- Manter `Completed(VEHICLE_FORM)` como estado persistente: rejeitado porque voltar ao onboarding
  poderia disparar a navegação novamente sem uma operação consumível.
- Executar `SessionRepository.refresh()` após toda mutação na Garagem: rejeitado porque o estado
  `Verifying` aciona o SessionGate; aplicar a resposta autoritativa é menor e imediata.

## 9. Falha ambígua e atualização posterior

**Decision**: não fazer retry automático de `create_vehicle`. Em timeout ou perda de resposta, o
draft permanece e a falha geral orienta nova tentativa; ao sair e reabrir a Garagem, a lista
autoritativa é consultada e um veículo já confirmado aparece. Uma resposta de sucesso dispara
refresh da sessão/Home ou da Garagem, conforme a origem.

**Rationale**: a RPC atual não recebe chave de idempotência e Marca/Modelo não formam uma chave
natural. Retry automático poderia duplicar um veículo sem identificadores após commit com resposta
perdida. Consultar a fonte remota ao reabrir satisfaz a recuperação sem inventar infraestrutura.

**Alternatives considered**:
- Retry automático: rejeitado pelo risco de duplicidade.
- Deduplicar por Marca/Modelo: rejeitado porque uma conta pode possuir veículos iguais.
- Adicionar tabela/chave de idempotência: rejeitado por complexidade fora do requisito atual.

## Fontes primárias

- [Supabase Database Functions](https://supabase.com/docs/guides/database/functions)
- [Supabase Row Level Security](https://supabase.com/docs/guides/database/postgres/row-level-security)
- [Supabase Kotlin RPC](https://supabase.com/docs/reference/kotlin/rpc)
- [PostgreSQL Constraints](https://www.postgresql.org/docs/current/ddl-constraints.html)
- [PostgreSQL Explicit Locking](https://www.postgresql.org/docs/current/explicit-locking.html)
