# Research: Criar conta de usuário

## 1. Módulos Supabase e compatibilidade do toolchain

**Decision**: preservar `supabase-kt` 3.2.6 e Ktor Android 3.3.1 já validados no projeto e
adicionar, na mesma versão, somente `auth-kt` e `postgrest-kt`. Instalar `Auth` e `Postgrest` uma
única vez no `SupabaseClientProvider`. Adicionar o plugin Kotlin Serialization 2.2.10, o runtime
JSON 1.9.0 já alinhado ao SDK e DTOs internos `@Serializable` apenas para ler/inserir `profiles`;
não adicionar Storage, Realtime, Functions, Compose Auth, Hilt ou outra biblioteca.

**Rationale**: Auth executa cadastro, sessão e recuperação com credenciais; PostgREST é necessário
para confirmar o perfil e reparar de forma idempotente uma sessão autenticada cujo perfil esteja
ausente. Usar a versão já compilada no app evita o upgrade incompatível de Kotlin identificado na
feature 004. O plugin de serialização tem a mesma versão do compilador Kotlin do app e elimina
parsing manual de resposta. Fontes: [instalação do SDK](https://github.com/supabase-community/supabase-kt/blob/3.2.6/README.md),
[inicialização Kotlin](https://supabase.com/docs/reference/kotlin/initializing) e
[Kotlin Serialization](https://kotlinlang.org/docs/serialization.html).

**Alternatives considered**: usar somente Auth e confiar no trigger sem conferir o perfil foi
rejeitado porque não cobre a recuperação esclarecida; inserir perfil somente pelo cliente foi
rejeitado pela janela entre identidade e perfil; atualizar o SDK/toolchain foi rejeitado por
ampliar o escopo; usar `JsonObject` em toda a camada de dados foi rejeitado porque perde contrato
tipado sem reduzir dependências efetivas.

## 2. Cadastro, sessão imediata e metadata

**Decision**: após `Auth.awaitInitialization()`, o Repository chama `signUpWith(Email)` com e-mail
normalizado, senha e somente `name`, `phone` e exatamente um entre `cpf` e
`cnpj` normalizados em `raw_user_meta_data`. O e-mail do perfil sempre vem de
`auth.users.email`, nunca da metadata. O retorno anulável do signup não decide sucesso: exigir
`currentSessionOrNull()` e confirmar o perfil do mesmo UUID. O ambiente Supabase deve manter
**Confirm email** desabilitado; se o signup não produzir sessão, retornar falha de configuração e
não abrir a Home.

**Rationale**: a API Kotlin permite metadata no cadastro e, com confirmação desabilitada, autentica
imediatamente. A exigência do ambiente é necessária para cumprir a decisão de produto sem adicionar
deep links ou tela de confirmação. Metadata serve apenas de entrada do trigger e continua sujeita
às constraints; não é usada como fonte de autorização. Fontes: [signup Kotlin](https://supabase.com/docs/reference/kotlin/v2/auth-signup),
[sessão atual](https://supabase.com/docs/reference/kotlin/v2/auth-getsession) e
[RLS/metadata](https://supabase.com/docs/guides/database/postgres/row-level-security#helper-functions).

**Alternatives considered**: confirmar e-mail antes da Home contradiz a clarificação; enviar email
também como metadata duplica fonte de verdade; usar metadata para autorização foi rejeitado porque
`raw_user_meta_data` pode ser alterado pelo usuário.

## 3. Criação atômica e recuperação idempotente

**Decision**: uma migration cria função `security definer set search_path = ''` e trigger
`AFTER INSERT ON auth.users`. O trigger normaliza a metadata, insere o perfil com o mesmo UUID e
deixa `active` assumir `true`. Falha de constraint ou inserção propaga erro e bloqueia/reverte o
signup na mesma transação. Após signup, o Repository consulta o perfil próprio. Se existir sessão
sem perfil, faz um único insert idempotente com o UUID autenticado e o rascunho normalizado, sem
enviar `active`, e consulta novamente. Em resposta de rede ambígua, verifica primeiro a sessão;
numa nova tentativa que encontre e-mail já registrado, tenta autenticar uma vez com as mesmas
credenciais e só trata como recuperação quando a sessão e o perfil forem confirmados.

**Rationale**: o trigger é o padrão oficial do Supabase para espelhar `auth.users` e transforma a
criação normal em operação transacional. A consulta/reparação cobre sessão persistida, resposta
perdida e estado parcial legado sem criar outra identidade. UUID primário, `ON CONFLICT` e uniques
fazem repetição convergir para uma linha. A Home nunca é liberada antes da verificação. Fonte:
[gerenciamento de dados de usuário](https://supabase.com/docs/guides/auth/managing-user-data).

**Alternatives considered**: duas chamadas cliente sem trigger deixam estado parcial; Edge Function
com service-role acrescenta deploy e credencial privilegiada e ainda não compartilha a transação
de Auth; apagar a identidade pelo app requer privilégio administrativo proibido; navegar e reparar
em background viola o requisito de consistência.

## 4. Schema e invariantes de `profiles`

**Decision**: criar `public.profiles` com:

- `id uuid primary key references auth.users(id) on delete cascade`;
- `name text not null`;
- `email text not null unique`;
- `phone text not null`;
- `cpf text unique null`;
- `cnpj text unique null`;
- `active boolean not null default true` — status booleano controlado pelo sistema.

Checks nomeados exigem nome/e-mail/telefone canônicos, telefone brasileiro com 10 ou 11 dígitos,
CPF válido ou CNPJ válido e XOR entre documentos. O tipo PF/PJ não vira coluna: é inferido por
qual documento não é nulo. Identificadores permanecem `text` para preservar zeros e o CNPJ
alfanumérico. Não adicionar timestamps, perfil de empresa separado ou campos futuros.

**Rationale**: UUID PK/FK entrega relação 1:1 e cascade evita órfão. Em PostgreSQL, `UNIQUE` permite
vários `NULL` e rejeita repetição dos valores presentes, exatamente o necessário para CPF/CNPJ
alternativos. A validação no banco protege contra clientes modificados e escrita direta. Fontes:
[tabelas públicas ligadas a Auth](https://supabase.com/docs/guides/auth/managing-user-data) e
[constraints PostgreSQL](https://www.postgresql.org/docs/current/ddl-constraints.html).

**Alternatives considered**: armazenar documentos numéricos perde zeros e não aceita novo CNPJ;
guardar tipo de pessoa duplica informação; uma coluna única `document` perde restrições/consulta
por tipo e diverge do schema pedido; validar somente no Android confia em cliente não confiável.

## 5. CPF e CNPJ vigentes

**Decision**: CPF aceita exatamente 11 dígitos, rejeita sequências repetidas e valida os dois DVs
por módulo 11. CNPJ aceita tanto o legado numérico quanto o formato alfanumérico vigente: 12
caracteres `[0-9A-Z]` seguidos de dois DVs numéricos, sem máscara e em maiúsculas no armazenamento.
Os valores alfanuméricos usam `ASCII - 48` e os pesos oficiais para os DVs. Implementar a mesma
regra em funções SQL imutáveis e em validador Kotlin feature-local. A UI mascara ambos como
`##.###.###/####-##`, mas usa teclado numérico somente para CPF; CNPJ aceita letras e números.

**Rationale**: a Receita Federal iniciou a atribuição do CNPJ alfanumérico em julho de 2026. Como
a data atual é posterior, restringir a dígitos rejeitaria documentos válidos. Dupla validação dá
feedback imediato e integridade definitiva. Fontes: [CNPJ alfanumérico](https://www.gov.br/receitafederal/pt-br/acesso-a-informacao/acoes-e-programas/programas-e-atividades/cnpj-alfanumerico)
e [manual oficial de DVs](https://www.gov.br/receitafederal/pt-br/centrais-de-conteudo/publicacoes/documentos-tecnicos/cnpj/manual-dv-cnpj.pdf/@@download/file).

**Alternatives considered**: aceitar apenas CNPJ numérico foi rejeitado por estar obsoleto;
aceitar regex sem DV foi rejeitado por não validar o documento; depender de API externa adiciona
I/O, rastreamento de documento e disponibilidade sem necessidade.

## 6. RLS, grants e funções do banco

**Decision**: habilitar RLS, revogar privilégios herdados de `anon`/`authenticated` e conceder a
`authenticated` somente `SELECT` e `INSERT`. Policy de SELECT usa
`(select auth.uid()) = id`; policy de INSERT exige a mesma igualdade. Não conceder UPDATE/DELETE,
pois edição e desativação estão fora do escopo e `active` é controlado pelo sistema. Revogar
EXECUTE das funções auxiliares/trigger para papéis cliente e usar nomes totalmente qualificados
com `search_path` vazio.

**Rationale**: grants limitam operações disponíveis e RLS limita linhas, formando defesa em
profundidade com a chave publishable. A policy de INSERT existe apenas para recuperação autenticada;
o caminho normal é o trigger. Fonte: [RLS Supabase](https://supabase.com/docs/guides/database/postgres/row-level-security)
e [segurança da Data API](https://supabase.com/docs/guides/api/securing-your-api).

**Alternatives considered**: perfil público ou SELECT global contradiz a clarificação; policy sem
grant não habilita operação e grant sem policy expõe dados; permitir UPDATE para facilitar reparo
amplia o escopo e permitiria alterar `active`.

## 7. Repository, resultados e tratamento de falhas

**Decision**: `SignUpRepository` recebe `SupabaseClient` por construtor e expõe uma única operação
suspensa de registro/recuperação com request normalizado. Retorna resultado de domínio tipado:
sucesso, e-mail indisponível, documento indisponível, offline/timeout, serviço indisponível,
inconsistência de perfil ou desconhecido. `CancellationException` sempre é relançada. Exceções do
SDK são classificadas centralmente sem imprimir corpo, metadata, senha, token ou documento. Quando
o backend não diferencia a unique violada com segurança, a mensagem de UI combina e-mail/documento
indisponível sem afirmar qual conta existe.

**Rationale**: Repository é obrigatório pela constituição porque há Auth/PostgREST reais. Tipos de
domínio impedem raw exceptions na UI e permitem mensagens acionáveis. Uma operação única mantém a
orquestração e a recuperação fora do ViewModel.

**Alternatives considered**: chamada Supabase no ViewModel/Composable viola arquitetura; Use Case
que apenas repassa seria boilerplate; parsing de mensagens localizado na UI expõe detalhes e
duplica regra; retry automático ilimitado pode criar rajadas e estado incerto.

**Security note**: na versão 3.2.6, mensagens de exceções REST podem conter URL e headers de request,
inclusive Authorization, e detalhes PostgREST podem conter o valor de uma unique violada. Nunca
registrar o Throwable, `message`, headers ou `details`; mapear somente classe, status, error code e
nomes de constraints em allowlist para um erro de domínio sanitizado. `AuthErrorCode.Conflict`
permanece genérico; somente códigos estáveis específicos de e-mail podem virar e-mail indisponível.

## 8. Estado MVVM e ciclo do formulário

**Decision**: `SignUpUiState` imutável contém `PersonType?`, `name`, `email`, `phone`, um único
`document`, senha/confirmação apenas em memória, erros tipados, solicitação de foco e um
`SignUpSubmissionState` selado: Editing, Submitting, Failure ou Success. ViewModel normaliza entrada,
valida todos os campos, foca o primeiro erro, bloqueia taps concorrentes e executa Repository em
`viewModelScope`. Success permanece no estado e a Route navega por `LaunchedEffect`; não usar
Channel/SharedFlow. Após falha remota, limpar senha/confirmação e preservar somente dados não
sensíveis. Nunca colocar senha em `SavedStateHandle`/`rememberSaveable`.

**Rationale**: sucesso durável não se perde durante recomposição e estados exclusivos evitam
booleans contraditórios. Um campo `document` torna impossível manter CPF e CNPJ simultaneamente.
Senha sobrevive somente à instância do ViewModel durante edição e é removida depois de I/O. Fonte:
[eventos na UI Android](https://developer.android.com/topic/architecture/ui-layer/events).

**Alternatives considered**: dois campos ocultos permitem payload contraditório; eventos de
navegação em Channel podem ser perdidos; reducer/MVI é cerimônia sem benefício; persistir senha
para restauração de processo é risco desnecessário.

## 9. UI, IME e acessibilidade

**Decision**: `SignUpRoute` coleta estado e conecta ViewModel; `SignUpScreen` stateless usa
`Scaffold`, TopAppBar fixa e uma `LazyColumn` sem bottom navigation, com `innerPadding`,
`imePadding` e largura máxima aproximada de 600dp. Tipo de pessoa é um grupo de seleção acessível
que empilha em largura/fonte restrita. Campos: nome dinâmico, e-mail, telefone, CPF ou CNPJ, senha e
confirmação. Máscaras são somente transformação visual com mapeamento de cursor; CTA tem mínimo
56dp e progresso. Erros ficam junto ao campo e banner remoto usa live region. Foco/scroll vão ao
primeiro erro; ações IME avançam e Done envia. Back/ícone compartilham confirmação de descarte
quando há rascunho e são bloqueados durante processamento.

**Rationale**: segue os padrões já usados em RequestService, preserva tema/tokens e mantém todos os
controles alcançáveis com teclado, fonte 200% e TalkBack. Sem bottom navigation, o cadastro continua
claramente fora da área autenticada. Fontes: [entrada de texto Compose](https://developer.android.com/develop/ui/compose/text/user-input),
[autofill Compose](https://developer.android.com/develop/ui/compose/text/autofill) e
[semântica](https://developer.android.com/develop/ui/compose/accessibility/semantics).

**Alternatives considered**: CTA fixa compete com IME; formulário em modal não comporta o fluxo;
TextFields compartilhados em `core/ui` não têm segundo consumidor; Compose Auth UI adiciona
dependência e não cobre CPF/CNPJ/modelo visual do app.

## 10. Navegação e back stack

**Decision**: adicionar rota sem argumentos `sign-up`. A ação `Criar conta` da Welcome navega com
guarda contra taps repetidos; Voltar retorna à Welcome. Success navega a Home com
`popUpTo(Welcome) { inclusive = true }` e `launchSingleTop`, removendo Welcome/SignUp e senhas da
pilha. Incluir SignUp no cálculo de ícones das system bars como superfície theme-aware.

**Rationale**: o callback já existe na Welcome e hoje é no-op. Remover o fluxo não autenticado após
sucesso impede que Back reabra formulário. Navigation permanece centralizada e a Screen não recebe
NavController.

**Alternatives considered**: manter Welcome atrás da Home permite voltar ao cadastro; NavController
na tela mistura responsabilidades; argumento de rota não é necessário.

## 11. Migration e validação operacional

**Decision**: versionar configuração Supabase local e migration em `supabase/config.toml` e
`supabase/migrations/<timestamp>_create_profiles.sql`. Validar schema localmente com Supabase CLI e
aplicar conscientemente a um projeto de desenvolvimento antes do teste Android. A confirmação de
e-mail é pré-requisito externo verificado no Dashboard, não uma migration SQL. Não executar push
remoto durante planejamento.

**Rationale**: schema, trigger, grants e policies precisam ser reproduzíveis e revisáveis. O
repositório ainda não possui diretório Supabase, então esta feature cria a fronteira mínima. Fonte:
[migrations Supabase](https://supabase.com/docs/guides/local-development/database-migrations).

**Alternatives considered**: SQL somente no Dashboard não é versionado; migration dentro de
`app/` mistura backend e Android; embutir service-role para criar schema é proibido.

## Resolution status

Todas as decisões do Technical Context foram resolvidas; não restam decisões pendentes.
