---

description: "Dependency-ordered implementation tasks for conditional provider service selection"
---

# Tasks: Seleção de serviços condicionada à modalidade do prestador

**Input**: Design documents from `specs/016-conditional-provider-services/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`,
`quickstart.md`

**Tests**: Automated test tasks are intentionally omitted — neither the feature nor the user
requested them and the project constitution forbids generating tests automatically. Each story
includes a runnable manual validation task based on `quickstart.md`.

**Organization**: Tasks are grouped by user story (Fixo, Móvel, PitTec) so each modality's
registration behavior can be implemented and validated as an independent increment.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel because it touches a different file and has no dependency on an
  unfinished task. Two tasks that edit the same file are never marked `[P]` relative to each other.
- **[Story]**: Maps the task to one of the three user stories in `spec.md`.
- Every task names its target file(s).

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Add the copy needed by the mode-aware offer UI before any behavior depends on it.

- [X] T001 [P] Add strings for the Móvel/PitTec implicit-service explanation and the catalog
      inconsistency failure message in `app/src/main/res/values/strings.xml`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Establish the shared data/model/ViewModel plumbing every modality depends on.

**⚠️ CRITICAL**: No user story work begins until this phase compiles and the mode-dispatch logic
below exists.

- [X] T002 Add `dispatchKind` to the `CatalogService` domain model and to `CatalogServiceDto` (with
      `@SerialName("dispatch_kind")` and mapping) in
      `app/src/main/java/com/trustarx/pitgo/features/providerregistration/model/ProviderRegistrationModels.kt`
      and
      `app/src/main/java/com/trustarx/pitgo/features/providerregistration/data/ProviderRegistrationDtos.kt`
- [X] T003 Add a repository method that resolves the implicit service for a given `dispatch_kind`
      (`MOBILE_SEQUENTIAL` or `PITTEC_AUTO_SEQUENTIAL`), returning found/ausente/duplicado per
      `contracts/provider-registration-service-rule.md`, in
      `app/src/main/java/com/trustarx/pitgo/features/providerregistration/repository/ProviderRegistrationRepository.kt`
      (depends on T002)
- [X] T004 Extend `ProviderRegistrationViewModel` so `onLocationModeSelected` rebuilds
      `draft.offers` per modality — Fixo: um rascunho vazio e editável (categoria, serviço, preço,
      duração); Móvel: uma oferta travada no serviço Guincho resolvido, com preço/duração ainda
      editáveis pelo prestador; PitTec: uma oferta travada no serviço PitTec resolvido, gratuita
      (`isFree = true`, preço zero) e com uma duração padrão interna, sem nenhum campo editável —
      e exponha essa falha através de um novo campo dedicado em `ProviderEditingContent` (ex.:
      `implicitServiceIssue: ProviderFailure?`), independente do estado de `submission` —
      verificado ao trocar de modalidade e novamente antes do envio, não apenas como resultado de
      uma tentativa de envio, em
      `app/src/main/java/com/trustarx/pitgo/features/providerregistration/viewmodel/ProviderRegistrationViewModel.kt`
      (depends on T003)
- [X] T005 Re-checar a resolução do serviço implícito imediatamente antes de montar o payload em
      `submit()`, bloqueando o envio com a falha do T004 se o serviço estiver ausente ou duplicado
      naquele momento, em
      `app/src/main/java/com/trustarx/pitgo/features/providerregistration/viewmodel/ProviderRegistrationViewModel.kt`
      (depends on T004)
- [X] T006 Renderizar um banner de inconsistência de catálogo compartilhado — acionado pelo campo
      `implicitServiceIssue` do T004, reaproveitando apenas o texto já usado para
      `ProviderFailure.CatalogChanged` (via a string do T001), nunca o estado de `submission` — em
      `app/src/main/java/com/trustarx/pitgo/features/providerregistration/ui/ProviderRegistrationScreen.kt`
      (depends on T004)

**Checkpoint**: O app compila; selecionar qualquer modalidade já resolve o serviço implícito
correto (ou sinaliza a falha) sem que nenhuma tela tenha sido ajustada ainda.

---

## Phase 3: User Story 1 — Prestador Fixo escolhe seus serviços (Priority: P1) 🎯 MVP

**Goal**: Continuar exigindo, sem regressão, que um prestador Fixo escolha ao menos um serviço do
catálogo com preço e duração antes de enviar o cadastro.

**Independent Test**: Cadastrar um prestador com modalidade Fixo, tentar enviar sem escolher
serviço (deve bloquear), depois escolher um serviço válido e confirmar que o cadastro é aceito.

### Implementation for User Story 1

- [X] T007 [US1] Restringir a seção completa de oferta (título, cartões de categoria/serviço/
      preço/duração/descrição e o botão "Adicionar oferta") para só ser exibida quando
      `content.draft.locationMode == ProviderLocationMode.FIXED`, preservando integralmente o
      comportamento e os textos atuais para essa modalidade, em
      `app/src/main/java/com/trustarx/pitgo/features/providerregistration/ui/ProviderRegistrationScreen.kt`
      (depends on T004)
- [X] T008 [US1] Executar e registrar o Cenário 1 e o Cenário 4 de
      `specs/016-conditional-provider-services/quickstart.md` — **validado em dispositivo físico**
      (R1AIB60009675B2): modalidade Fixo continua exibindo a seção completa (Categoria, Serviço,
      Preço, Duração, Descrição, "Remover oferta", "Adicionar outro serviço"); Cenário 4 confirmado
      digitando `R$ 123` no Preço em Fixo e trocando para Móvel — o campo veio vazio, comprovando
      que `onLocationModeSelected` reconstrói `draft.offers` do zero a cada troca de modalidade

**Checkpoint**: User Story 1 é demonstrável de forma independente — Fixo continua exigindo
serviço, exatamente como hoje.

---

## Phase 4: User Story 2 — Prestador Guincho não escolhe serviço (Priority: P1)

**Goal**: Eliminar a escolha de categoria/serviço para a modalidade Móvel, mantendo apenas
preço/duração como campos que o prestador preenche para o serviço "Guincho" atribuído
automaticamente.

**Independent Test**: Cadastrar um prestador com modalidade Móvel e verificar que nenhuma escolha
de categoria/serviço aparece, que preço e duração continuam sendo pedidos, e que o cadastro é
aceito normalmente.

### Implementation for User Story 2

- [X] T009 [US2] Criar a seção simplificada de oferta para Móvel — somente os campos de preço,
      duração e "gratuito" (ligados aos mesmos `onOfferFieldChanged`/`onFreeChanged` já existentes),
      sem categoria, serviço, descrição, botão de remover ou "Adicionar oferta" — exibida apenas
      quando `content.draft.locationMode == ProviderLocationMode.MOBILE`, em
      `app/src/main/java/com/trustarx/pitgo/features/providerregistration/ui/ProviderRegistrationScreen.kt`
      (depends on T004, T006, T007)
- [X] T010 [US2] Executar e registrar o Cenário 2 e a variante Móvel do Cenário 5 de
      `specs/016-conditional-provider-services/quickstart.md` — **validado em dispositivo físico**
      (R1AIB60009675B2): modalidade Móvel exibe somente o cartão "Móvel" com o hint do Guincho,
      checkbox "Serviço gratuito", Preço e Duração — sem categoria/serviço/descrição; o serviço
      implícito (`MOBILE_SEQUENTIAL`) resolveu com sucesso neste ambiente (botão "CADASTRAR EMPRESA"
      habilitado), confirmando o caminho feliz da variante Móvel do Cenário 5 (a variante de falha
      não pôde ser forçada sem acesso direto ao banco para desativar o serviço)

**Checkpoint**: User Story 2 é demonstrável de forma independente — Móvel não pede categoria/
serviço, apenas preço/duração, sem afetar o comportamento de Fixo.

---

## Phase 5: User Story 3 — Prestador PitTec não escolhe serviço nem preço (Priority: P1)

**Goal**: Eliminar completamente a etapa de oferta (categoria, serviço, preço e duração) para a
modalidade PitTec, cujo serviço é atribuído automaticamente e sem preço definido pelo prestador.

**Independent Test**: Cadastrar um prestador com modalidade PitTec e verificar que nenhuma tela
de categoria, serviço, preço ou duração é exibida, e que o cadastro é aceito normalmente.

### Implementation for User Story 3

- [X] T011 [US3] Ocultar inteiramente a seção de oferta quando
      `content.draft.locationMode == ProviderLocationMode.PITTEC` (nenhum campo, nenhum título de
      seção relacionado a serviço), em
      `app/src/main/java/com/trustarx/pitgo/features/providerregistration/ui/ProviderRegistrationScreen.kt`
      (depends on T004, T006, T007)
- [X] T012 [US3] Executar e registrar o Cenário 3 e a variante PitTec do Cenário 5 de
      `specs/016-conditional-provider-services/quickstart.md` — **validado em dispositivo físico**
      (R1AIB60009675B2): modalidade PitTec exibe apenas o texto `provider_offer_pittec_hint`, sem
      nenhum campo de categoria/serviço/preço/duração; a variante PitTec do Cenário 5 foi validada
      organicamente — o serviço implícito (`PITTEC_AUTO_SEQUENTIAL`) está ausente/duplicado neste
      catálogo, e o app exibiu corretamente o banner "O catálogo mudou. Revise os serviços
      selecionados." com "Tentar novamente", mantendo "CADASTRAR EMPRESA" desabilitado

**Checkpoint**: User Story 3 é demonstrável de forma independente — PitTec não pede nenhuma
informação de serviço, categoria, preço ou duração.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Verificar compilação e ausência de regressão em todo o fluxo de cadastro.

- [X] T013 [P] Rodar `./gradlew :app:compileDebugKotlin`, `./gradlew :app:assembleDebug`,
      `./gradlew :app:assembleRelease` e `./gradlew :app:lintDebug`, resolvendo qualquer falha
      introduzida por esta funcionalidade — clean build completo (debug + release + lint) passou
      sem nenhuma falha nova
- [ ] T014 Executar o `specs/016-conditional-provider-services/quickstart.md` por completo (todos
      os 5 cenários) e confirmar que os fluxos já existentes de Guincho e PitTec (despacho,
      rastreamento, disponibilidade) permanecem funcionando sem regressão após um cadastro criado
      pelo novo fluxo — **parcialmente coberto**: T008/T010/T012 validaram ao vivo, em dispositivo
      físico, a UI dos 5 cenários (incluindo as duas variantes do Cenário 5); não foi concluído um
      envio real de cadastro Fixo/Móvel de ponta a ponta seguido de teste de despacho/rastreamento
      em outro dispositivo (papel de cliente), nem a variante PitTec pôde ser exercida com sucesso
      (o serviço implícito `PITTEC_AUTO_SEQUENTIAL` está ausente/duplicado no catálogo deste
      ambiente — dado do banco, não falha de código). Recomenda-se completar esse regression test
      de ponta a ponta antes do merge, corrigindo antes o catálogo (garantir exatamente um serviço
      ativo com `dispatch_kind = PITTEC_AUTO_SEQUENTIAL`)

---

## Dependencies & Execution Order

### Phase dependencies

```text
Phase 1 Setup
    └── Phase 2 Foundational
          ├── US1 Fixo continua exigindo serviço
          ├── US2 Móvel sem escolha de serviço
          └── US3 PitTec sem serviço nem preço

US1 + US2 + US3
    └── Phase 6 Polish
```

- **Setup (Phase 1)**: sem dependências.
- **Foundational (Phase 2)**: depende do Setup e bloqueia todas as histórias; T002 → T003 → T004 →
  T005 são sequenciais (mesmo fluxo de dados); T006 depende só de T004.
- **US1 (Phase 3)**: depende apenas do Foundational; T007 é o primeiro ajuste de tela e serve de
  base para as próximas duas histórias (que adicionam suas próprias seções alternativas no mesmo
  arquivo).
- **US2 (Phase 4)**: depende do Foundational e de T007 (a seção de Fixo precisa já estar
  condicionada antes de adicionar a seção alternativa de Móvel no mesmo arquivo).
- **US3 (Phase 5)**: mesma dependência de US2; pode ser feita em paralelo por outra pessoa desde
  que não edite o arquivo ao mesmo tempo que T009.
- **Polish (Phase 6)**: depende das três histórias.

### Within each story

- Modelo/DTO/repositório/ViewModel (Foundational) precedem qualquer ajuste de tela.
- Cada história só edita a tela para adicionar ou restringir a sua própria seção; nenhuma história
  reintroduz lógica de validação já coberta pelo Foundational.
- Validação manual roda ao final de cada história, antes de depender dela na Polish.

## Parallel Execution Examples

### Foundational

T002 e T006 não podem rodar em paralelo com T003/T004/T005 (dependência direta), mas T001 (Setup)
pode ser feito em paralelo com todo o Foundational, já que só mexe em `strings.xml`:

```text
Task T001: Novas strings de explicação/erro
Task T002: dispatchKind no modelo/DTO de catálogo
```

### User Story 2 e User Story 3

T009 e T011 editam o mesmo arquivo (`ProviderRegistrationScreen.kt`) em blocos `if` distintos —
podem ser feitas por pessoas diferentes desde que uma espere a outra commitar para evitar conflito
de merge, mas não são tecnicamente paralelas no mesmo arquivo.

## Implementation Strategy

### MVP first: User Story 1

1. Completar T001–T006 (Setup e Foundational).
2. Completar T007–T008 (Fixo continua exigindo serviço).
3. Parar e validar os Cenários 1 e 4 do `quickstart.md`.
4. Demonstrar que o comportamento de Fixo está preservado antes de simplificar Móvel/PitTec.

### Incremental delivery

1. **Fixo**: entrega a US1 como o menor incremento que não pode regredir.
2. **Móvel**: remove a escolha de serviço, mantendo preço/duração — valida com o Cenário 2.
3. **PitTec**: remove serviço, preço e duração por completo — valida com o Cenário 3.
4. Completar a Fase 6 antes de considerar a funcionalidade pronta para revisão.

## Notes

- `[P]` nunca permite edição simultânea do mesmo arquivo; respeite as dependências indicadas no
  texto de cada tarefa.
- Nenhuma migração de banco ou mudança de RPC é necessária nesta funcionalidade — toda a mudança é
  em `app/src/main/java/com/trustarx/pitgo/features/providerregistration`.
- Não alterar o comportamento já entregue de despacho/rastreamento de Guincho ou PitTec
  (`features/mobiledispatch`, `features/pittec`, `features/appointmenttracking`); esta
  funcionalidade só muda o que acontece durante o cadastro.
- Commit após cada tarefa ou grupo de dependência coerente, com mensagem `feat:`, `fix:`,
  `refactor:`, `style:` ou `chore:`.
