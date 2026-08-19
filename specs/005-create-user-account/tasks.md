---

description: "Dependency-ordered implementation tasks for creating a user account"
---

# Tasks: Criar conta de usuário

**Input**: Design documents from `/specs/005-create-user-account/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`,
`quickstart.md`

**Tests**: Nenhum teste automatizado será criado. A feature será verificada por migration
reset/lint, build/lint Android e pelos cenários manuais de `quickstart.md`.

**Organization**: Tasks are grouped by user story so each increment can be implemented and
validated before the next priority.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Pode executar em paralelo por alterar arquivos diferentes e não depender de tarefa
  incompleta no mesmo grupo.
- **[Story]**: História de usuário da especificação (`US1`, `US2`, `US3`).
- Todas as tarefas de implementação indicam caminhos exatos.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Preparar o workspace Supabase e as dependências mínimas do SDK.

- [X] T001 [P] Inicializar a configuração local versionada do Supabase CLI em `supabase/config.toml` e criar `supabase/migrations/` sem incluir project ref, token ou credencial real
- [X] T002 [P] Adicionar aliases/versionamento de Auth, PostgREST, Kotlin Serialization plugin e JSON runtime em `gradle/libs.versions.toml`, declarar o plugin apply-false em `build.gradle.kts` e aplicá-lo com as dependências no módulo em `app/build.gradle.kts`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Disponibilizar os plugins remotos e a fronteira segura de dados exigida por todas as
histórias.

**⚠️ CRITICAL**: Nenhuma história pode ser concluída antes desta fase.

- [X] T003 [P] Instalar uma única instância dos plugins `Auth` e `Postgrest` no cliente compartilhado em `app/src/main/java/com/trustarx/pitgo/core/network/SupabaseClientProvider.kt`
- [X] T004 [P] Criar a migration completa de `public.profiles` com helpers de CPF/CNPJ numérico e alfanumérico, UUID PK/FK, checks nomeados, uniques, `active=true`, trigger transacional, grants mínimos e RLS própria em `supabase/migrations/20260819000000_create_profiles.sql`
- [ ] T005 Validar que `supabase/config.toml` e `supabase/migrations/20260819000000_create_profiles.sql` aplicam do zero com `supabase db reset` e passam `supabase db lint`, corrigindo somente os artefatos Supabase quando necessário

**Checkpoint**: Auth/PostgREST e o contrato de banco estão prontos para as histórias.

---

## Phase 3: User Story 1 — Criar uma conta com perfil (Priority: P1) 🎯 MVP

**Goal**: Abrir o cadastro pela Welcome e concluir um cadastro válido de pessoa física ou jurídica
com sessão ativa, um perfil do mesmo UUID e navegação para Home.

**Independent Test**: Abrir **Criar conta**, selecionar PF ou PJ, informar dados válidos e confirmar
no projeto Supabase exatamente uma identidade e um perfil de mesmo ID; a Home abre imediatamente e
Back não retorna ao formulário.

### Implementation for User Story 1

- [X] T006 [P] [US1] Criar `PersonType`, rascunho/request normalizado, estados exclusivos, erros/resultados de domínio e invariantes de exatamente um documento em `app/src/main/java/com/trustarx/pitgo/features/signup/model/SignUpModels.kt`
- [X] T007 [P] [US1] Criar DTOs internos serializáveis de insert e leitura, omitindo `active` no payload e mapeando a grafia no retorno, em `app/src/main/java/com/trustarx/pitgo/features/signup/data/ProfileDtos.kt`
- [X] T008 [P] [US1] Adicionar textos base do cadastro e vetores genéricos de voltar/visibilidade em `app/src/main/res/values/strings.xml`, `app/src/main/res/drawable/ic_back.xml`, `app/src/main/res/drawable/ic_visibility.xml` e `app/src/main/res/drawable/ic_visibility_off.xml`, atualizando o uso do back em `app/src/main/java/com/trustarx/pitgo/features/requestservice/ui/RequestServiceScreen.kt`
- [X] T009 [US1] Implementar `SignUpRepository` com `Auth.awaitInitialization`, signup por e-mail/senha, metadata namespaced normalizada, exigência de sessão, seleção do perfil do mesmo UUID e retorno de domínio sem expor SDK em `app/src/main/java/com/trustarx/pitgo/features/signup/repository/SignUpRepository.kt`
- [X] T010 [US1] Implementar `SignUpViewModel` com `StateFlow`, eventos explícitos de campos/tipo, um único documento, submissão em `viewModelScope`, guarda contra reenvio e estado Success em `app/src/main/java/com/trustarx/pitgo/features/signup/viewmodel/SignUpViewModel.kt`
- [X] T011 [US1] Criar `SignUpRoute`/`SignUpScreen` stateless com Scaffold, TopAppBar, LazyColumn, seleção PF/PJ, campos dinâmicos, senha/confirmação e CTA Material em `app/src/main/java/com/trustarx/pitgo/features/signup/ui/SignUpScreen.kt`
- [X] T012 [US1] Adicionar a rota `sign-up`, conectar `WelcomeRoute.onCreateAccount`, voltar para Welcome e navegar Success para Home limpando a pilha em `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoRoutes.kt` e `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt`
- [ ] T013 [US1] Executar os fluxos PF e PJ das seções 5 e 6 de `specs/005-create-user-account/quickstart.md`, conferindo sessão imediata, UUID 1:1, documento alternativo nulo, `active=true` e back stack

**Checkpoint**: O MVP cria uma conta válida e um perfil consistente para PF ou PJ.

---

## Phase 4: User Story 2 — Corrigir dados inválidos antes do envio (Priority: P2)

**Goal**: Bloquear formulários inválidos, explicar todos os erros, focar o primeiro e aceitar
entrada brasileira com normalização/máscaras corretas sem criar registros indevidos.

**Independent Test**: Tentar todas as combinações ausentes/inválidas, troca PF/PJ, colagem com
máscara, CNPJ numérico/alfanumérico e conflitos; nenhum formulário inválido cria identidade/perfil,
e todos os campos relevantes recebem erro com foco no primeiro.

### Implementation for User Story 2

- [X] T014 [P] [US2] Implementar normalização e validação local de nome, e-mail, DDD/telefone, CPF, CNPJ numérico/alfanumérico oficial e senha de 8 caracteres em `app/src/main/java/com/trustarx/pitgo/features/signup/model/SignUpValidation.kt`
- [X] T015 [P] [US2] Implementar transformações visuais e `OffsetMapping` para telefone, CPF e CNPJ alfanumérico em `app/src/main/java/com/trustarx/pitgo/features/signup/ui/SignUpVisualTransformations.kt`
- [X] T016 [P] [US2] Adicionar labels dinâmicos, supporting texts e mensagens neutras de validação/conflito em `app/src/main/res/values/strings.xml`
- [X] T017 [P] [US2] Mapear somente códigos Auth e nomes de constraints allowlisted para e-mail/documento indisponível, descartando detalhes remotos, em `app/src/main/java/com/trustarx/pitgo/features/signup/repository/SignUpRepository.kt`
- [X] T018 [US2] Integrar validação em uma passada, limpeza do erro editado, troca de tipo com limpeza do documento e `FocusRequest` monotônico no estado em `app/src/main/java/com/trustarx/pitgo/features/signup/viewmodel/SignUpViewModel.kt`
- [X] T019 [US2] Integrar filtros/máscaras, teclado CPF/CNPJ adequado, `isError`/supporting text, foco/BringIntoView, ações IME, autofill e toggles independentes de senha em `app/src/main/java/com/trustarx/pitgo/features/signup/ui/SignUpScreen.kt`
- [ ] T020 [US2] Executar a matriz de formulário da seção 7 e os conflitos da seção 8 de `specs/005-create-user-account/quickstart.md`, confirmando zero criação remota para entrada inválida e ausência de exposição do proprietário

**Checkpoint**: Cadastro válido continua funcionando e todos os casos de correção são verificáveis.

---

## Phase 5: User Story 3 — Receber feedback seguro do cadastro (Priority: P3)

**Goal**: Representar processamento/falhas com segurança, impedir duplicidade, recuperar perfil
parcialmente ausente, proteger o descarte e comprovar RLS/atomicidade antes da Home.

**Independent Test**: Simular toque repetido, offline, timeout, rate limit, falha de perfil e sessão
com perfil ausente; a UI permanece coerente, senha/documentos não vazam, retry converge para uma
linha e acesso cruzado ao perfil é negado.

### Implementation for User Story 3

- [X] T021 [P] [US3] Adicionar textos de processamento, banners sanitizados, rate limit, falha de ambiente/perfil, retry e confirmação de descarte em `app/src/main/res/values/strings.xml`
- [X] T022 [US3] Completar recuperação idempotente com select-first, upsert por conflito de ID, confirmação pós-insert, reconciliação de resposta ambígua e classificação segura de rede/HTTP/Auth/PostgREST em `app/src/main/java/com/trustarx/pitgo/features/signup/repository/SignUpRepository.kt`
- [X] T023 [US3] Implementar transições CreatingAccount/EnsuringProfile/RecoveringProfile/Failure/Success, retry exclusivo de perfil, limpeza de senhas após I/O e bloqueio de taps concorrentes em `app/src/main/java/com/trustarx/pitgo/features/signup/viewmodel/SignUpViewModel.kt`
- [X] T024 [US3] Renderizar progresso, banner live-region, estado read-only de recuperação, retry, BackHandler e AlertDialog de descarte com semântica/acessibilidade em `app/src/main/java/com/trustarx/pitgo/features/signup/ui/SignUpScreen.kt`
- [ ] T025 [US3] Executar as seções 8, 9 e 10 de `specs/005-create-user-account/quickstart.md`, comprovando falhas sanitizadas, rollback do trigger, recuperação idempotente, nenhuma duplicidade e RLS própria/cross-user/anon

**Checkpoint**: Todas as histórias funcionam e as fronteiras de falha/segurança estão demonstradas.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Consolidar design system, regressão e evidências finais sem ampliar o escopo.

- [X] T026 Consolidar a altura mínima de CTA compartilhada e o ícone de back reutilizável sem criar componentes genéricos de formulário em `app/src/main/java/com/trustarx/pitgo/core/design/theme/Dimensions.kt`, `app/src/main/java/com/trustarx/pitgo/features/signup/ui/SignUpScreen.kt` e `app/src/main/java/com/trustarx/pitgo/features/requestservice/ui/RequestServiceScreen.kt`
- [X] T027 Executar `./gradlew :app:assembleDebug :app:lintDebug` e corrigir regressões somente nos arquivos listados em `specs/005-create-user-account/plan.md`
- [ ] T028 Executar as seções 4 a 11 de `specs/005-create-user-account/quickstart.md`, revisar o diff final e confirmar ausência de testes novos, service-role/admin key, credencial versionada, senha persistida, Throwable remoto em log e mudança fora do escopo

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: inicia imediatamente; T001 e T002 são paralelas.
- **Foundational (Phase 2)**: depende do setup; T003 depende de T002, T004 depende de T001 e ambas
  podem rodar em paralelo; T005 depende de T004.
- **US1 (Phase 3)**: depende de toda a fundação e entrega o MVP.
- **US2 (Phase 4)**: depende do cadastro funcional de US1 para integrar validação ao mesmo fluxo.
- **US3 (Phase 5)**: depende de US1 e US2, pois amplia os mesmos estados, Repository e tela com
  feedback/recuperação.
- **Polish (Phase 6)**: depende das três histórias desejadas.

### User Story Dependencies

- **US1 (P1)**: começa após Phase 2 e não depende de outra história.
- **US2 (P2)**: reutiliza os modelos, ViewModel, Repository e tela entregues por US1; permanece
  independentemente verificável pela matriz de formulário.
- **US3 (P3)**: estende o fluxo completo de US1/US2 com falhas e recovery; permanece
  independentemente verificável pela matriz de falhas/RLS.

### Within Each User Story

- Modelos/DTOs e recursos antes do Repository/ViewModel/UI que os consomem.
- Repository antes da orquestração no ViewModel.
- ViewModel antes da integração final de estado na tela.
- Tela antes da navegação e validação manual.
- Nenhuma tarefa de teste automatizado é necessária ou autorizada.

### Parallel Opportunities

- Setup: T001 e T002.
- Foundational: T003 e T004 após suas respectivas dependências de setup.
- US1: T006, T007 e T008; depois o fluxo converge em T009 → T010 → T011 → T012.
- US2: T014, T015, T016 e T017 após US1; depois T018 → T019.
- US3: T021 pode rodar em paralelo com T022; depois T023 → T024.
- Validações manuais e build permanecem após o código correspondente para gerar evidência real.

---

## Parallel Example: User Story 1

```text
Task T006: Criar modelos/estado em SignUpModels.kt
Task T007: Criar DTOs em ProfileDtos.kt
Task T008: Criar recursos base e vetores Android
```

## Parallel Example: User Story 2

```text
Task T014: Implementar validadores/normalização
Task T015: Implementar máscaras/OffsetMapping
Task T016: Adicionar mensagens de validação
Task T017: Mapear conflitos remotos no Repository
```

## Parallel Example: User Story 3

```text
Task T021: Adicionar mensagens de loading/falha/retry
Task T022: Implementar recovery e classificação de falhas no Repository
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Concluir Setup e Foundational.
2. Implementar T006–T012.
3. Parar e validar T013 com PF e PJ em ambiente de desenvolvimento aprovado.
4. Demonstrar sessão + perfil 1:1 + Home antes de ampliar o fluxo.

### Incremental Delivery

1. Setup + Foundational → SDK/schema seguro.
2. US1 → cadastro válido completo (MVP).
3. US2 → validação, máscaras e correção de entrada.
4. US3 → feedback, recovery, atomicidade e RLS demonstrados.
5. Polish → regressão, lint e auditoria final.

### Safe Execution Notes

- `supabase db push` não faz parte automática destas tarefas; aplicar a migration remotamente exige
  confirmação explícita do projeto de desenvolvimento alvo.
- Usar apenas fixtures não produtivas nos cenários manuais.
- Nunca imprimir URL com token, Authorization, exception message/details, CPF/CNPJ completo ou
  senha durante diagnóstico.
- Fazer commits pequenos por tarefa ou grupo lógico, sem incluir `local.properties` ou credenciais.
