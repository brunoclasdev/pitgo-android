# Research: Cadastro de empresa prestadora

## 1. Stack e fronteiras existentes

**Decision**: Reutilizar Kotlin/Compose/MVVM, `SessionRepository`, `PitGoAppContainer` e o cliente
Supabase 3.2.6 já configurado com Auth e Postgrest. Criar um único
`ProviderRegistrationRepository`, sem nova dependência, Use Case, DataSource, Hilt ou cache.

**Rationale**: O repositório já usa esse desenho em `VehicleRepository`: DTO serializável, chamada
RPC, resultado de domínio selado e erro sanitizado. A feature possui acesso real a catálogo e banco,
portanto Repository tem responsabilidade concreta; camadas adicionais apenas repassariam chamadas.

**Alternatives considered**: Acesso Supabase no ViewModel foi rejeitado por violar MVVM;
DataSource/Use Case foram rejeitados por não acrescentarem regra; Edge Function foi rejeitada por
introduzir backend e credencial sem melhorar a transação PostgreSQL.

## 2. Entrada autenticada e continuidade do CTA público

**Decision**: A entrada autenticada principal será “Área da empresa” no Perfil. O CTA existente da
Welcome “Cadastrar empresa” abrirá o Sign Up com argumento fechado `SignUpOrigin.PROVIDER`. Depois
que a sessão se tornar autenticada, o NavHost consumirá apenas esse enum conhecido e abrirá
`ProviderRegistration`; o cadastro comum continuará no fluxo atual. A rota de empresa não recebe
ID, e-mail, CNPJ ou destino arbitrário.

**Rationale**: Usuários autenticados não voltam à Welcome, então precisam de entrada na área da
conta. O CTA público não pode abrir diretamente uma tela protegida; usar a origem tipada preserva a
intenção após criar a conta sem permitir open redirect. Uma pessoa física também pode representar
uma empresa, portanto a origem não força o tipo jurídico do perfil.

**Alternatives considered**: Abrir o formulário pela Welcome foi rejeitado por FR-001; usar a aba
Serviços foi rejeitado porque ela ainda não possui um hub; passar `providerId` foi rejeitado porque o
owner vem da sessão; usar string livre de destino foi rejeitado por ampliar a superfície de navegação.

## 3. Destino único para formulário e consulta

**Decision**: `ProviderRegistration` terá estados exclusivos para carregamento, falha inicial,
formulário, empresa existente, sucesso e sessão expirada. Se já houver provider completo, a própria
tela mostrará resumo da empresa, endereço e ofertas. Após criação, mostrará confirmação e o mesmo
resumo; a ação final retorna ao Perfil removendo o rascunho concluído do back stack.

**Rationale**: Isso satisfaz o redirecionamento de conta já cadastrada e disponibiliza os dados para
consulta sem criar um segundo destino quase idêntico. O resumo também resolve perda de resposta: um
retry que encontra o agregado completo é sucesso reconhecido, não uma segunda criação.

**Alternatives considered**: Snackbar seguida de Home foi rejeitada porque os dados não estariam
consultáveis; uma tela de detalhes separada foi rejeitada por duplicar shell, loading e queries;
sobrescrever provider existente foi rejeitado porque transformaria criação em edição fora do escopo.

## 4. UI longa, responsiva e acessível

**Decision**: Usar `Scaffold` com `TopAppBar` fixa e uma única `LazyColumn`, sem bottom navigation,
com largura máxima de 600dp, `imePadding` e seções “Dados da empresa”, “Endereço” e “Serviços
oferecidos”. Campos permanecem em uma coluna; coordenadas opcionais ficam em seção expansível. As
ofertas são itens de nível raiz da lista lazy, com chaves locais estáveis, e usam componentes Material
oficiais. Erros focam e trazem ao viewport o primeiro campo inválido.

**Rationale**: Uma coluna reflowa corretamente em smartphone compacto, paisagem e fonte 200%.
`LazyColumn` evita compor antecipadamente uma quantidade alta de ofertas e não exige scroll aninhado.
O padrão já existe em Sign Up e Vehicle Form.

**Alternatives considered**: Wizard foi rejeitado por acrescentar navegação/estado e dificultar o
primeiro erro global; duas colunas fixas foram rejeitadas por cortar labels com fonte ampliada;
accordion por oferta foi rejeitado porque esconderia erros e adicionaria estado sem necessidade.

Fontes: [listas lazy e chaves estáveis](https://developer.android.com/develop/ui/compose/lists),
[insets/IME](https://developer.android.com/develop/ui/compose/system/insets-ui),
[foco](https://developer.android.com/develop/ui/compose/touch-input/focus/change-focus-behavior) e
[acessibilidade Material](https://developer.android.com/develop/ui/compose/accessibility/api-defaults).

## 5. Catálogo e seletores dependentes

**Decision**: O Repository consulta categorias ordenadas e carrega serviços por categoria sob
demanda, paginando até página curta para não transformar o limite técnico da Data API em limite de
produto. O ViewModel mantém `LoadState` explícito e cache por `categoryId` enquanto o destino vive.
Mudar categoria limpa o serviço daquela oferta. Serviços já escolhidos em outra oferta ficam
indisponíveis no seletor e a validação definitiva continua no submit e no UNIQUE do banco.

**Rationale**: O app não cria nem altera catálogo e não impõe limite total de ofertas. Cache por
categoria evita chamadas repetidas quando várias ofertas usam a mesma categoria, enquanto paginação
evita catálogo silenciosamente truncado. Categoria é informação de seleção; o payload envia apenas
`service_id`, e o banco deriva a relação real.

**Alternatives considered**: Carregar um limite fixo foi rejeitado por truncar dados; copiar a lista
de serviços para cada oferta foi rejeitado por custo e divergência; enviar `category_id` à RPC foi
rejeitado porque é derivável e poderia contradizer o serviço.

## 6. Schema canônico e reconciliação

**Decision**: Versionar `providers`, `addresses`, `service_categories`, `services` e
`provider_services` na migration 007, preservando dados existentes. O nome canônico da empresa será
`providers`, coerente com as demais tabelas plurais; se apenas `public.provider` existir, a migration
deve renomeá-la após preflight, e abortar se as duas existirem. A relação de serviço será
`services.category_id`; uma variante legada só poderá ser renomeada após conferir que não criará uma
segunda relação.

O campo físico de bairro permanecerá exatamente `addresses.neighbornhood`, contrato fornecido para
o ambiente atual. Kotlin expõe `neighborhood` e serializa para a grafia física. A migration falha se
as duas grafias existirem, em vez de escolher ou apagar valores. Nenhuma segunda coluna equivalente
é criada e uma renomeação potencialmente incompatível fica fora desta feature.

`providers.id` é PK/FK para `auth.users.id`; não existe `user_id` redundante. O RPC ainda exige
profile ativo antes de criar o provider, então a regra de negócio "só empresa com profile" continua
valendo mesmo sem a FK apontar para `profiles.id`. Endereço é único por
provider neste cadastro. `provider_services` possui UNIQUE `(provider_id, service_id)`. A opção
`is_free` existe apenas no comando: a RPC exige a escolha explícita e grava `price=0`; não será criada
uma coluna redundante, pois preço zero já é a representação canônica de oferta gratuita.
Categorias possuem nome normalizado e único sem diferenciar caixa; serviços possuem nome único por
categoria sem diferenciar caixa. A migration faz preflight de duplicatas antes de criar esses
índices e aborta em conflito, sem escolher registros automaticamente.

**Rationale**: O repo não contém DDL dessas tabelas; a migration deve tornar o contrato reproduzível
e falhar em ambiguidades, nunca apagar dados. Preservar o typo explicitamente informado evita quebrar
outros consumidores. PK/FK/UNIQUE eliminam ownership e associações inconsistentes mesmo fora do app.

**Alternatives considered**: `CREATE TABLE IF NOT EXISTS` isolado foi rejeitado porque não reconcilia
colunas/constraints; manter `provider` e `providers` foi rejeitado por duplicidade; renomear bairro
agora foi rejeitado pelo risco de quebra externa; persistir `is_free` foi rejeitado por duplicar um
estado já determinado por `price`.

## 7. Operação PostgreSQL atômica

**Decision**: Expor uma única RPC `public.create_provider_registration(p_provider jsonb,
p_address jsonb, p_offers jsonb)`. Ela será `SECURITY DEFINER`, `SET search_path=''`, derivará
`auth.uid()`, exigirá profile ativo, adquirirá `pg_advisory_xact_lock` por usuário, validará todo o
JSON antes do primeiro INSERT e criará provider, um endereço e todas as ofertas na mesma execução.
`provider_id`, `address_id`, `category_id`, owner, status e timestamps não entram no payload.

Se um agregado completo já existir para `auth.uid()`, a função retorna `ALREADY_REGISTERED` sem
alterá-lo. Se encontrar agregado legado parcial, falha de forma neutra. Exceções não são capturadas
dentro da função; PostgreSQL reverte a execução inteira. A chave natural da conta, o lock, endereço
único e provider/service UNIQUE tornam toque concorrente e retry após resposta perdida idempotentes,
sem tabela de idempotência.

**Rationale**: Três chamadas PostgREST no Android não podem garantir sucesso completo ou nenhum
registro. Uma função PostgreSQL executa perto dos dados, valida catálogo atual e mantém a transação.
A conta já é uma chave vitalícia única, portanto request UUID seria infraestrutura duplicada.

**Alternatives considered**: Inserts separados foram rejeitados por estado parcial; uma RPC por
oferta foi rejeitada por não ser atômica; upsert que altera provider foi rejeitado por editar dados
fora do escopo; Edge Function/service role foi rejeitada por ampliar backend e segredo.

Fontes: [funções de banco Supabase](https://supabase.com/docs/guides/database/functions),
[RPC Kotlin](https://supabase.com/docs/reference/kotlin/rpc),
[JSON recordsets PostgreSQL](https://www.postgresql.org/docs/current/functions-json.html) e
[advisory locks transacionais](https://www.postgresql.org/docs/current/explicit-locking.html).

## 8. RLS e privilégio mínimo

**Decision**: Habilitar RLS nas cinco tabelas e retirar DML direto de `anon` e `authenticated`.
`authenticated` recebe apenas SELECT: catálogo existente e linhas próprias de provider/endereço/
ofertas. Policies próprias usam `(select auth.uid())`; a RPC é executável apenas por
`authenticated`. Toda relação e função dentro da RPC é qualificada. A chave service role nunca entra
no Android.

**Rationale**: RLS e grants são camadas complementares. A função precisa de privilégios elevados
para a transação, mas sua superfície é pequena, valida a sessão internamente e não recebe owner.

**Alternatives considered**: Policies de INSERT direto foram rejeitadas porque permitiriam contornar
a atomicidade; confiar só no filtro do app foi rejeitado porque dados do cliente não são confiáveis;
usar metadata do usuário para autorização foi rejeitado por ser editável.

Fontes: [RLS Supabase](https://supabase.com/docs/guides/database/postgres/row-level-security),
[segurança da Data API](https://supabase.com/docs/guides/database/secure-data) e
[grants da Data API](https://supabase.com/docs/reference/kotlin/installing#enable-data-api-access).

## 9. Normalização e validação em duas fronteiras

**Decision**: Extrair telefone/CNPJ compartilhados do Sign Up para
`BrazilianInputValidation`; adicionar CEP e UFs. O ViewModel mantém entradas editáveis, e
`ProviderRegistrationValidation` produz um comando normalizado: nome/endereços com trim, e-mail em
lowercase, telefone/CEP só dígitos, CNPJ uppercase alfanumérico, UF uppercase, país `BR`, preço como
`BigDecimal`/string decimal canônica, duração `Int` e coordenadas `BigDecimal`. Máscaras são apenas
visuais.

CNPJ aceita 12 caracteres alfanuméricos mais dois DVs numéricos e o algoritmo oficial ASCII-48 já
presente em `private.is_valid_cnpj`. Preço não gratuito deve ser maior que zero e ter até duas casas;
gratuito envia explicitamente `is_free=true` e preço zero. Coordenadas são ambas nulas ou ambas
válidas. As mesmas invariantes são repetidas em CHECKs/RPC.

**Rationale**: A validação local oferece orientação e foco; somente o banco é autoridade definitiva.
`BigDecimal` evita perda de centavos e `Double` não entra no contrato monetário. O CNPJ alfanumérico
já está em produção na data da feature.

**Alternatives considered**: Armazenar máscara foi rejeitado por dificultar unicidade; aceitar somente
CNPJ numérico foi rejeitado por estar obsoleto; `Double` foi rejeitado por arredondamento; validar
apenas no Android foi rejeitado porque o cliente não é confiável.

Fontes: [implantação do CNPJ alfanumérico](https://www.gov.br/receitafederal/pt-br/acesso-a-informacao/acoes-e-programas/programas-e-atividades/cnpj-alfanumerico)
e [manual oficial de DV](https://www.gov.br/receitafederal/pt-br/centrais-de-conteudo/publicacoes/documentos-tecnicos/cnpj/manual-dv-cnpj.pdf/%40%40download/file).

## 10. Estado, retry e privacidade

**Decision**: O ViewModel captura o usuário esperado no bootstrap, observa o `SessionRepository` e
descarta draft/cache/resultados tardios se a sessão sair de Authenticated ou trocar de usuário.
Loading, edição, envio, falha, existente, sucesso e expirada são tipos exclusivos. Um estado
Submitting e um job único bloqueiam toques duplicados. Falhas temporárias preservam o draft somente
em memória; conflito de e-mail/CNPJ/serviço marca o campo correspondente; sessão expirada zera tudo.

O Repository mapeia apenas classes/status/códigos e nomes de constraints numa allowlist. Nunca
registra ou propaga `Throwable`, mensagem PostgREST, headers, payload, IDs ou CNPJ completo. A UI
mostra CNPJ mascarado no resumo.

**Rationale**: A sessão central já cobre refresh/expiração e não deve ser duplicada. Estado em memória
preserva o trabalho durante falha transitória sem persistir documento/endereço. Exceções do SDK podem
conter dados técnicos sensíveis, então o domínio recebe apenas erros fechados.

**Alternatives considered**: DataStore/SavedStateHandle foram rejeitados por persistirem PII sem
requisito; Channel para estado renderizável foi rejeitado por perda de evento; logs mascarando parte
do payload foram rejeitados porque não há necessidade operacional de registrar o payload.
