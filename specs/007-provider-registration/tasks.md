# Tasks: Cadastro de empresa prestadora

**Input**: Design documents from `/specs/007-provider-registration/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`,
`quickstart.md`

**Tests**: Testes automatizados não foram solicitados e não serão gerados. Cada história possui um
critério de validação manual independente; a fase final executa o quickstart completo.

**Organization**: As tarefas estão agrupadas por história para permitir entrega incremental. Os
arquivos compartilhados e a fronteira transacional do banco ficam antes das histórias porque
bloqueiam todas elas.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode executar em paralelo com outras tarefas marcadas da mesma etapa porque usa arquivos
  diferentes e não depende de uma tarefa incompleta
- **[Story]**: história atendida (`US1`, `US2`, `US3`)
- Todos os paths são relativos à raiz do repositório

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Preparar recursos e componentes brasileiros que passam a ter reutilização real, sem
adicionar dependências ou criar arquivos vazios.

- [X] T001 [P] Adicionar textos do formulário, catálogo, ofertas, conflitos, sessão, descarte, confirmação, resumo, acessibilidade e CTA “Área da empresa” em `app/src/main/res/values/strings.xml`
- [X] T002 [P] Extrair normalização/validação reutilizável de e-mail, telefone, CPF/CNPJ alfanumérico, CEP e UFs para `app/src/main/java/com/trustarx/pitgo/core/common/validation/BrazilianInputValidation.kt` e fazer `app/src/main/java/com/trustarx/pitgo/features/signup/model/SignUpValidation.kt` delegar sem alterar seu comportamento
- [X] T003 [P] Mover máscaras de telefone/CNPJ para `app/src/main/java/com/trustarx/pitgo/core/ui/BrazilianVisualTransformations.kt`, adicionar transformação de CEP, atualizar imports em `app/src/main/java/com/trustarx/pitgo/features/signup/ui/SignUpScreen.kt` e remover `app/src/main/java/com/trustarx/pitgo/features/signup/ui/SignUpVisualTransformations.kt`

**Checkpoint**: validação e máscaras brasileiras compartilhadas preservam o Sign Up existente e
estão prontas para a nova feature.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Criar o contrato persistente, segurança, modelos e rotas que todas as histórias usam.

**⚠️ CRITICAL**: Nenhuma história deve iniciar antes de a migration, os modelos e as rotas desta
fase estarem definidos.

- [X] T004 Implementar preflight não destrutivo, reconciliação singular/plural e schema/constraints de `providers`, `addresses`, `service_categories`, `services` e `provider_services`, preservando `addresses.neighbornhood`, em `supabase/migrations/20260820000000_provider_registration.sql`
- [X] T005 Configurar RLS e least-privilege grants para leituras próprias, catálogo autenticado e ausência de DML direto em `supabase/migrations/20260820000000_provider_registration.sql`
- [X] T006 Implementar a RPC `create_provider_registration(p_provider jsonb, p_address jsonb, p_offers jsonb)` com `auth.uid()`, profile ativo, advisory lock, validação antes dos INSERTs, atomicidade, idempotência e outcomes fechados em `supabase/migrations/20260820000000_provider_registration.sql`
- [X] T007 [P] Criar entidades, drafts, comandos, summaries, campos/erros tipados, `LoadState`, estados sealed e transições da tela em `app/src/main/java/com/trustarx/pitgo/features/providerregistration/model/ProviderRegistrationModels.kt`
- [X] T008 Criar DTOs serializáveis e mapeamentos seguros para catálogo, agregado próprio e payload/resultado da RPC, incluindo decimal como string e `neighborhood` → `neighbornhood`, em `app/src/main/java/com/trustarx/pitgo/features/providerregistration/data/ProviderRegistrationDtos.kt`
- [X] T009 [P] Centralizar `SignUpOrigin`, o pattern opcional do Sign Up e a rota sem argumentos `ProviderRegistration` em `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoRoutes.kt`

**Checkpoint**: schema reproduzível, RPC protegida, tipos imutáveis e rotas estão prontos; todas as
histórias podem ser implementadas sobre o mesmo contrato.

---

## Phase 3: User Story 1 — Cadastrar a empresa prestadora (Priority: P1) 🎯 MVP

**Goal**: Uma conta autenticada sem empresa informa dados empresariais, endereço brasileiro e uma
oferta válida; o sistema salva o agregado inteiro para `auth.uid()` ou nada, e uma conta já
cadastrada consulta seu resumo sem criar outra empresa.

**Independent Test**: Entrar com conta sem provider, preencher empresa/endereço/uma oferta, confirmar
e verificar provider com ID da conta, um endereço e uma oferta; repetir a entrada e obter o resumo.
Campos inválidos não chamam a RPC e falha definitiva não deixa registros parciais.

### Implementation for User Story 1

- [X] T010 [P] [US1] Implementar normalização e validação de empresa, endereço BR, coordenadas pareadas e uma oferta mínima, retornando comando sem owner IDs e primeiro campo inválido, em `app/src/main/java/com/trustarx/pitgo/features/providerregistration/validation/ProviderRegistrationValidation.kt`
- [X] T011 [P] [US1] Implementar bootstrap do agregado próprio, leitura inicial de categorias/serviços, confirmação via RPC, recarga obrigatória do resumo e resultado genérico sanitizado em `app/src/main/java/com/trustarx/pitgo/features/providerregistration/repository/ProviderRegistrationRepository.kt`
- [X] T012 [P] [US1] Criar `ProviderRegistrationScreen` stateless com Scaffold/TopAppBar/LazyColumn, seções de empresa/endereço, uma oferta, loading/falha inicial e resumo sem CNPJ completo ou IDs em `app/src/main/java/com/trustarx/pitgo/features/providerregistration/ui/ProviderRegistrationScreen.kt`
- [X] T013 [US1] Implementar `ProviderRegistrationViewModel` com e-mail inicial de `AccountSnapshot.authEmail`, bootstrap, edição, validação, foco monotônico, envio único, estados AlreadyRegistered/Success e limpeza explícita do draft em `app/src/main/java/com/trustarx/pitgo/features/providerregistration/viewmodel/ProviderRegistrationViewModel.kt`
- [X] T014 [US1] Integrar Route/ViewModel, diálogo de descarte, bloqueio de back durante envio, progresso compreensível, confirmação e resumo no destino em `app/src/main/java/com/trustarx/pitgo/features/providerregistration/ui/ProviderRegistrationScreen.kt`
- [X] T015 [US1] Registrar `ProviderRegistrationRepository` no container de DI manual em `app/src/main/java/com/trustarx/pitgo/core/app/PitGoAppContainer.kt`
- [X] T016 [US1] Conectar CTA da Welcome ao Sign Up com origem PROVIDER, adicionar destino autenticado/guard/back stack no NavHost e expor “Área da empresa” no Perfil em `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt` e `app/src/main/java/com/trustarx/pitgo/features/profile/ui/ProfileScreen.kt`

**Checkpoint**: US1 é um MVP completo e validável sozinho: criação atômica, uma empresa por conta,
resumo existente e navegação autenticada funcionam sem depender das melhorias de múltiplas ofertas.

---

## Phase 4: User Story 2 — Configurar serviços e condições comerciais (Priority: P2)

**Goal**: Selecionar categorias e serviços existentes, adicionar/remover qualquer quantidade de
ofertas, configurar preço/gratuidade/duração/descrição e impedir serviço duplicado sem alterar o
catálogo global.

**Independent Test**: Com duas categorias e três serviços existentes, criar duas ofertas de
categorias distintas — uma paga e uma gratuita —, remover/adicionar cards e confirmar que nomes,
categoria derivada, centavos, duração e descrições do resumo correspondem ao formulário. Catálogo
vazio ou falho deve bloquear somente a seleção e oferecer retry.

### Implementation for User Story 2

- [X] T017 [P] [US2] Implementar paginação completa de categorias, paginação de serviços filtrados por categoria, ordenação estável e carregamento paginado das ofertas do resumo em `app/src/main/java/com/trustarx/pitgo/features/providerregistration/repository/ProviderRegistrationRepository.kt`
- [X] T018 [P] [US2] Estender validação para lista sem teto artificial, serviço único, preço `BigDecimal` com até duas casas, gratuidade explícita, duração inteira 1..10.080 e descrição opcional em `app/src/main/java/com/trustarx/pitgo/features/providerregistration/validation/ProviderRegistrationValidation.kt`
- [X] T019 [US2] Implementar no ViewModel add/remove por `localId`, cache `LoadState` por categoria, limpeza de serviço ao trocar categoria, retry de catálogo e invalidação de seleção removida em `app/src/main/java/com/trustarx/pitgo/features/providerregistration/viewmodel/ProviderRegistrationViewModel.kt`
- [X] T020 [US2] Renderizar ofertas como itens lazy com chave estável, comboboxes dependentes, estados loading/empty/failure, serviço já usado indisponível, opção gratuito, preço/duração/descrição e ações add/remove em `app/src/main/java/com/trustarx/pitgo/features/providerregistration/ui/ProviderRegistrationScreen.kt`

**Checkpoint**: US1 e US2 funcionam juntas; qualquer conjunto não vazio de serviços distintos do
catálogo pode ser confirmado, sem limite imposto pelo app e sem mutar categoria/serviço global.

---

## Phase 5: User Story 3 — Corrigir falhas sem perder o cadastro (Priority: P3)

**Goal**: Preservar o draft em falhas temporárias, orientar conflitos sem vazamento, impedir requests
duplicados e limpar tudo quando a sessão expira ou troca de usuário.

**Independent Test**: Provocar e-mail/CNPJ duplicado, offline, timeout, resposta perdida, toques
repetidos e expiração/troca de sessão. Confirmar erros neutros no campo correto, retry idempotente,
nenhuma duplicidade, draft preservado apenas quando seguro e estado zerado antes de outra conta.

### Implementation for User Story 3

- [X] T021 [P] [US3] Classificar timeout, offline, 401/403/42501, 5xx, contrato, catálogo alterado e constraints allow-listed de e-mail/CNPJ/serviço sem propagar Throwable, message, details ou payload em `app/src/main/java/com/trustarx/pitgo/features/providerregistration/repository/ProviderRegistrationRepository.kt`
- [X] T022 [P] [US3] Adicionar banners Material, retry, live region, supporting text e infraestrutura de foco/scroll para campos simples e `OfferField(localId, field)` em `app/src/main/java/com/trustarx/pitgo/features/providerregistration/ui/ProviderRegistrationScreen.kt`
- [X] T023 [US3] Observar `SessionRepository`, capturar o usuário esperado, cancelar jobs/resultados tardios, limpar PII/cache na troca/expiração, preservar draft em falha transitória e mapear conflitos para foco específico em `app/src/main/java/com/trustarx/pitgo/features/providerregistration/viewmodel/ProviderRegistrationViewModel.kt`
- [X] T024 [US3] Integrar callback de sessão expirada com `SessionViewModel.refresh`, retorno seguro ao gate e remoção do formulário concluído/expirado do back stack em `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt`

**Checkpoint**: Todas as histórias são funcionais; falhas e concorrência mantêm consistência,
privacidade e recuperação sem exigir reinício do cadastro.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Verificar qualidade, segurança, acessibilidade e integração completas sem ampliar escopo.

- [X] T025 [P] Revisar reflow em compacto/paisagem/fonte 200%, IME, alvos 48dp, CTA 56dp, headings, semântica de erro, TalkBack e ausência de truncamento em `app/src/main/java/com/trustarx/pitgo/features/providerregistration/ui/ProviderRegistrationScreen.kt` e `app/src/main/res/values/strings.xml`
- [ ] T026 [P] Executar `supabase db reset` e `supabase db lint`, validar preflight/schema/RLS/grants/RPC/rollback e corrigir qualquer falha em `supabase/migrations/20260820000000_provider_registration.sql`
- [X] T027 Auditar ausência de CNPJ completo, owner IDs, tokens, headers, payload e exceções brutas em logs/mensagens e corrigir as fronteiras em `app/src/main/java/com/trustarx/pitgo/features/providerregistration/repository/ProviderRegistrationRepository.kt`, `app/src/main/java/com/trustarx/pitgo/features/providerregistration/viewmodel/ProviderRegistrationViewModel.kt` e `app/src/main/java/com/trustarx/pitgo/features/providerregistration/ui/ProviderRegistrationScreen.kt`
- [X] T028 Executar `./gradlew :app:assembleDebug :app:lintDebug` e corrigir integração/compilação nos arquivos alterados sob `app/src/main/java/com/trustarx/pitgo/` e `app/src/main/res/values/strings.xml`
- [ ] T029 Executar todos os cenários manuais de entrada, cadastro, catálogo, atomicidade, concorrência, resposta perdida, RLS, sessão e acessibilidade descritos em `specs/007-provider-registration/quickstart.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 — Setup**: inicia imediatamente; T001, T002 e T003 podem ocorrer em paralelo.
- **Phase 2 — Foundational**: depende de Setup. T004 → T005 → T006 no mesmo arquivo; T007 e T009
  podem avançar em paralelo com a migration; T008 depende de T007.
- **Phase 3 — US1**: depende da Foundation completa. T010, T011 e T012 podem iniciar em paralelo;
  T013 depende de T010/T011; T014 depende de T012/T013; T015 depende de T011; T016 depende de
  T009/T014/T015.
- **Phase 4 — US2**: depende do MVP US1. T017 e T018 podem ocorrer em paralelo; T019 depende de
  ambas; T020 depende de T019.
- **Phase 5 — US3**: depende de US1 e integra sobre o formulário final de US2. T021 e T022 podem
  ocorrer em paralelo; T023 depende de T021; T024 depende de T022/T023.
- **Phase 6 — Polish**: depende das histórias desejadas. T025 e T026 podem ocorrer em paralelo;
  T027 vem depois das correções funcionais; T028 compila/linta o resultado; T029 encerra com E2E.

### User Story Dependencies

```text
Setup → Foundation → US1 (MVP) → US2 → US3 → Polish
```

- **US1 (P1)**: independente após Foundation e entrega valor utilizável sozinho.
- **US2 (P2)**: amplia o editor/oferta mínima de US1 para o conjunto dinâmico completo.
- **US3 (P3)**: endurece recuperação e sessão sobre os fluxos P1/P2; seus cenários podem ser
  validados isoladamente por injeção das falhas descritas.

### Within Each User Story

- modelos/contratos antes de Repository e ViewModel;
- validação/Repository antes das transições do ViewModel;
- estado/eventos antes da integração Route/NavHost;
- backend definitivo continua autoridade mesmo quando a UI já validou;
- nenhum checkpoint exige teste automatizado não solicitado.

### Parallel Opportunities

- Setup: T001, T002 e T003 usam fronteiras distintas.
- Foundation: Kotlin models/routes podem avançar enquanto a migration é escrita.
- US1: validação, Repository e Composable stateless podem avançar juntos após Foundation.
- US2: paginação do Repository e regras de validação de oferta usam arquivos diferentes.
- US3: classificação no Repository e componentes de feedback/foco na UI podem avançar juntos.
- Polish: acessibilidade Android e validação local Supabase usam toolchains/arquivos diferentes.

---

## Parallel Example: User Story 1

```text
Task T010: implementar validação de empresa/endereço/oferta mínima
Task T011: implementar Repository e fronteira RPC
Task T012: implementar Composable stateless a partir dos modelos
```

## Parallel Example: User Story 2

```text
Task T017: implementar paginação e leitura de catálogo/ofertas
Task T018: implementar regras de lista, preço, gratuidade e duração
```

## Parallel Example: User Story 3

```text
Task T021: implementar classificação sanitizada no Repository
Task T022: implementar banners, retry e foco dinâmico na UI
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Completar Setup.
2. Completar Foundation, inclusive RPC atômica/RLS.
3. Completar US1.
4. Parar e executar o teste independente de US1.
5. Demonstrar criação de uma empresa, um endereço e uma oferta, mais resumo de provider existente.

### Incremental Delivery

1. Setup + Foundation estabelecem contrato reproduzível e seguro.
2. US1 entrega o cadastro mínimo completo e consultável.
3. US2 acrescenta múltiplas ofertas e todos os estados de catálogo/condições comerciais.
4. US3 acrescenta recuperação, conflitos e limpeza rigorosa de sessão.
5. Polish valida banco, Android, RLS, acessibilidade e privacidade ponta a ponta.

### Suggested Commit Boundaries

- `refactor: share Brazilian input validation and masks`
- `feat: add provider registration database contract`
- `feat: add provider registration MVP`
- `feat: support multiple provider service offers`
- `fix: harden provider registration recovery and session handling`
- `chore: validate provider registration flow`

## Notes

- Tarefas `[P]` não devem editar o mesmo arquivo no mesmo momento.
- Commitar por tarefa ou grupo lógico pequeno, preservando mudanças existentes no worktree.
- Não executar `supabase db push` remoto durante implementação/validação sem solicitação explícita.
- Não criar seeds de produção, testes automatizados, Edge Function, service role, cache local,
  geocodificação, edição posterior ou outras features fora da spec.
