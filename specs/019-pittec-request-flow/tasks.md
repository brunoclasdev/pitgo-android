---
description: "Dependency-ordered implementation tasks for the corrected PitTec request flow"
---

# Tasks: Fluxo correto de solicitação do PitTec

**Input**: Design documents from `specs/019-pittec-request-flow/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`

**Tests**: Nenhum teste automatizado solicitado (governança do projeto) — cada story termina com
uma tarefa de validação manual ligada à seção correspondente de `quickstart.md`.

**Organization**: As três user stories do `spec.md` (US1 mapa/solicitar, US2 PitTec encontrado, US3
valores/início) são todas P1 e, na prática, formam um único fluxo contínuo — a ordem abaixo é
também a ordem de dependência real, não só de prioridade.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Pode rodar em paralelo — toca arquivo diferente e não depende de tarefa não concluída.
- **[Story]**: Mapeia a tarefa para US1/US2/US3 de `spec.md`.

> **ACHADOS DESTA SESSÃO DE IMPLEMENTAÇÃO** (leia antes das notas por tarefa):
>
> 1. **`PaymentCheckoutViewModel.kt` precisou de uma mudança real**, fora do que `plan.md` previa
>    ("features/payment/ reaproveitado como está"). Ele sempre gerava seu próprio `requestId`
>    (`UUID.randomUUID()`); para o PitTec cobrar pelo `search_id` já existente (exigido por
>    `arrive_pittec_appointment`), ele passou a ler um `requestId` opcional do próprio
>    `SavedStateHandle` (`PitGoRoutes.PaymentRequestIdArg`, mesma chave já usada por
>    `MobileDispatchNew`), caindo para um novo UUID quando ausente — comportamento do guincho móvel
>    inalterado. `PitGoRoutes.Payment` ganhou esse argumento de rota opcional.
> 2. **A navegação de volta do pagamento para o PitTec não avança para uma tela nova** — ela só
>    fecha a tela de pagamento (`popBackStack()`) de volta para a MESMA instância de
>    `PitTecScreen`/`PitTecViewModel`, que nunca foi destruída. É o próprio `PitTecViewModel` quem
>    detecta a aprovação (observando `payments` via Realtime, mesmo mecanismo de
>    `watchForRefund()`) e só então libera `onAccepted(...)`. Isso evita precisar propagar o
>    `appointmentId` de volta através da navegação.
> 3. Todas as tarefas de código (Setup, Foundational, US1–US3 exceto validação manual, Polish) foram
>    implementadas e **compilam** (`./gradlew :app:compileDebugKotlin` e `:app:lintDebug`, ambos
>    `BUILD SUCCESSFUL`, sem nenhum finding novo atribuível a esta feature). A migration foi
>    **aplicada de verdade** no projeto Supabase compartilhado (`supabase db push`) e as três
>    funções foram reverificadas ao vivo (`pg_get_functiondef`) depois do push.
> 4. As tarefas de validação manual (T010/T015/T019) exigem dois dispositivos/emuladores reais
>    (cliente + prestador PitTec de teste) — **bloqueadas neste ambiente**, sem hardware disponível.

---

## Phase 1: Setup

- [X] T001 Criado `supabase/migrations/20260913000000_pittec_request_flow.sql`

---

## Phase 2: Foundational (Blocking Prerequisites)

**⚠️ CRITICAL**: Nenhuma user story começa antes desta fase estar completa — as três dependem do
campo `candidate` no snapshot e dos novos casos de `PitTecDispatchPhase`.

- [X] T002 Escritas e **aplicadas de verdade** (`supabase db push`) as três alterações SQL, exatamente
  como descrito em `contracts/pittec-request-flow-rpcs.md`. Reverificado ao vivo via
  `pg_get_functiondef`: `start_pittec_dispatch` sem guarda, `arrive_pittec_appointment` com a
  guarda, `pittec_dispatch_snapshot` com `candidate`
- [X] T003 [P] Adicionado `PitTecCandidate`, o campo `candidate` em `PitTecDispatchSnapshot`
  (+ `isValid()` atualizado) e os casos `CandidateFound`/`ConfirmingValues` (este com
  `paymentApproved: Boolean = false`, ver achado #2) em `PitTecModels.kt`
- [X] T004 [P] Mapeado `candidate` (`PitTecCandidateDto`) em `PitTecDtos.kt`, propagado por
  `PitTecDispatchEnvelopeDto`/`PitTecDispatchSnapshotDto.toDomainOrNull()`

**Checkpoint**: modelo de domínio e schema do snapshot prontos; nenhuma user story bloqueada por
dado ausente.

---

## Phase 3: User Story 1 - Ver o mapa e solicitar o serviço (Priority: P1) 🎯 MVP

**Goal**: Abrir a tela do PitTec direto no mapa com um botão real de "Solicitar", sem nenhuma tela
de pagamento nem card de estimativa fictícia no caminho.

**Independent Test**: Tocar em "PitTec" na Home e confirmar que a tela abre no mapa com o botão de
solicitar disponível, sem passar por nenhuma tela de pagamento antes.

### Implementation for User Story 1

- [X] T005 [US1] Home agora navega `PITTEC_AUTO_SEQUENTIAL` direto para `PitGoRoutes.pitTec(service.id)`;
  `MOBILE_SEQUENTIAL` continua indo para `PitGoRoutes.Payment`, inalterado
  (`PitGoNavHost.kt`)
- [X] T006 [US1] Removido `initialRequestId` do construtor/`factory(...)` de `PitTecViewModel.kt`;
  `init` sempre chama `restoreDispatch()`; `requestId` gerado por `UUID.randomUUID()` dentro de
  `requestPitTec()`
- [X] T007 [US1] `PitTecViewModel.factory(...)` (em `PitGoNavHost.kt`) sem o argumento de `requestId`;
  removido o argumento de query de `PitGoRoutes.PitTec` (`PitGoRoutes.kt`) — `PaymentRequestIdArg`
  em si **não foi removido** (continua usado por `MobileDispatchNew` e, desde o achado #1, também
  pelo novo argumento opcional de `PitGoRoutes.Payment`)
- [X] T008 [US1] Removido `EstimateGatedDispatchOverlay` de `PitTecScreen.kt`; `DispatchOverlay`
  (`PitTecDispatchComponents.kt`) já tinha o botão real "Solicitar" (`pittec_call`) para
  `phase == None` — só passou a ser chamado diretamente, sem o wrapper fictício
- [X] T009 [US1] Confirmado (antes de remover) que `ServiceEstimate`/`DistancePerimeter`/`EstimateCard`
  seguem em uso real por `PitTecMockModels.kt`/`PitTecMockComponents.kt`/
  `PitTecClientMockViewModel.kt`/`PitTecProviderDashboardScreen.kt` (protótipo `017`, mantido) — só
  os dois imports em `PitTecScreen.kt` e a string `pittec_mock_search_cta` (sem nenhum outro
  consumidor) foram removidos
- [ ] T010 [US1] **bloqueado neste ambiente** (achado #4 — precisa de dois dispositivos/emuladores
  reais, cliente + prestador PitTec de teste)

**Checkpoint**: cliente abre o PitTec e solicita o serviço sem nenhum pagamento prévio nem card
fictício — a busca real começa (ainda sem os cards das próximas stories, comportamento equivalente
ao automático/cego de hoje, só que gratuito até a US3 reintroduzir o pagamento no lugar certo).

---

## Phase 4: User Story 2 - Ver o PitTec encontrado antes de decidir (Priority: P1)

**Goal**: Mostrar nome e distância do candidato assim que localizado, permitindo confirmar ou
recusar antes de qualquer cobrança.

**Independent Test**: Solicitar com um PitTec de teste disponível e confirmar que aparece um card
com nome e distância antes de qualquer cobrança; recusar e confirmar que a busca continua.

### Implementation for User Story 2

- [X] T011 [US2] Em `applyDispatch`, `CandidateFound(snapshot)` é usado quando
  `appointment?.status == PENDING && candidate != null` — com uma guarda adicional
  (`confirmedCandidateAppointmentId`) para não reabrir o card depois que o cliente já confirmou o
  mesmo candidato (sem isso, o próximo snapshot do polling/Realtime reabriria o card
  imediatamente — achado durante a implementação, não previsto em `data-model.md`)
- [X] T012 [US2] Adicionados `confirmCandidate()` (marca o candidato como confirmado, sem RPC) e
  `declineCandidate()` (reaproveita `repository.cancel`, mesmo padrão de `cancelSearch()`) em
  `PitTecViewModel.kt`
- [X] T013 [P] [US2] Criado `FoundCandidateCard` em `PitTecDispatchComponents.kt`, reaproveitando o
  `formatDistanceMeters` (`internal`) já existente em `PitTecMockComponents.kt` em vez de duplicar a
  formatação
- [X] T014 [US2] `DispatchOverlay` trata `CandidateFound`/`ConfirmingValues` (`when` exaustivo do
  sealed interface obrigou a cobrir os dois assim que foram adicionados) — `PitTecScreen.kt` passa
  `onConfirmCandidate`/`onDeclineCandidate` para `DispatchOverlay`
- [ ] T015 [US2] **bloqueado neste ambiente** (achado #4)

**Checkpoint**: cliente vê nome e distância do PitTec antes de decidir, sem nenhuma cobrança nesse
momento; recusar segue para o próximo candidato normalmente.

---

## Phase 5: User Story 3 - Confirmar valores e iniciar o serviço (Priority: P1)

**Goal**: Ao aceite do prestador, mostrar o card de valores reais (reaproveitando o pagamento real
de `018`) antes de considerar o serviço iniciado.

**Independent Test**: Aceitar a oferta do lado do prestador de teste e confirmar que o cliente vê
os valores reais antes de ser levado ao acompanhamento; confirmar que "Cheguei" (prestador) só
funciona depois do pagamento aprovado.

### Implementation for User Story 3

- [X] T016 [US3] `applyDispatch` usa `ConfirmingValues(snapshot, paymentApproved = ...)` quando
  `status == ACCEPTED`, preservando `paymentApproved` de um snapshot anterior para nunca reabrir um
  pagamento já aprovado; `watchForServicePayment(searchId)` é disparado nesse mesmo momento
- [X] T017 [US3] `PitTecRoute` ganhou `onNeedsPayment(requestId)`; o `LaunchedEffect` agora dispara
  `onNeedsPayment` quando `ConfirmingValues` ainda não tem `paymentApproved`, e `onAccepted` quando
  já tem — cada um guardado por id para disparar só uma vez (mesmo idioma do `navigatedAppointmentId`
  original)
- [X] T018 [US3] Implementado **de forma diferente da descrita** (achado #2): em vez de navegar para
  `PitGoRoutes.pitTec(serviceId, requestId)`, o `onApproved` do pagamento PitTec faz
  `navController.popBackStack()` — volta para a mesma `PitTecScreen`/`PitTecViewModel`, que detecta
  a aprovação sozinha (Realtime) e só então chama `onAccepted(appointmentId)`. Evita ter que propagar
  o `appointmentId` de volta através da navegação. `PaymentCheckoutViewModel`/`PaymentCheckoutRoute`
  não mudaram de UI, só ganharam o `requestId` opcional (achado #1)
- [ ] T019 [US3] **bloqueado neste ambiente** (achado #4) — a guarda em `arrive_pittec_appointment`
  foi confirmada ao vivo por leitura (`pg_get_functiondef`), não por execução ponta a ponta

**Checkpoint**: as três user stories funcionam em sequência — mapa/solicitar sem pagamento prévio,
candidato visível antes da cobrança, valores reais confirmados antes de o serviço começar.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T020 [P] Comentários desatualizados (docstring de `PitTecDispatchPhase`, notas de
  `PitTecScreen.kt`) reescritos para descrever o comportamento novo ao longo das próprias edições
  acima, não como uma passada separada
- [X] T021 `./gradlew :app:compileDebugKotlin` e `:app:lintDebug` — **BUILD SUCCESSFUL** nos dois,
  sem nenhum finding novo nos arquivos desta feature (`app:assembleDebug` completo não foi rodado à
  parte — `lintDebug` já força a compilação completa como dependência)
- [ ] T022 **Parcialmente feito**: `supabase db lint --linked` linta o banco JÁ IMPLANTADO, não a
  migration isoladamente (sem Docker neste ambiente para um shadow DB local) — rodado mesmo assim,
  sem nenhum finding novo nas três funções alteradas (confirmado por busca direta no resultado).
  Revisão manual de log: `grep -rn "Logger\|Log\.\|println" `nos arquivos novos/alterados desta
  feature não retornou nenhuma ocorrência — nada loga nome do prestador fora de
  `candidate`/`provider`

---

## Dependencies & Execution Order

```text
Setup (T001) → Foundational (T002-T004) → US1 (T005-T010) → US2 (T011-T015) → US3 (T016-T019) → Polish
```

- **US1** remove o pagamento prévio e o card fictício — pré-requisito prático para US2/US3 fazerem
  sentido (sem ele, a tela nunca chega a `None`/`SEARCHING` de forma alcançável pelo cliente).
- **US2** depende do campo `candidate` (Foundational) e da busca já partindo de US1.
- **US3** depende de US2 apenas na ordem de entrega (o gate de pagamento em
  `arrive_pittec_appointment` já existe desde a Foundational; US3 é sobre onde a UI aciona o
  pagamento, não sobre o dado que a User Story 2 introduziu).

### Parallel Opportunities

- T003 e T004 podem rodar em paralelo (arquivos diferentes) depois de T002.
- T013 (novo composable) pode ser feito em paralelo com T011/T012 (ViewModel) dentro da US2.
- T020 (revisão de comentários) pode rodar em paralelo com T021/T022.

---

## Implementation Strategy

### MVP First (User Story 1 apenas)

1. Completar Setup + Foundational.
2. Completar US1 — já corrige o problema mais visível (pagamento cedo demais e card fictício),
   mesmo que a busca ainda seja automática/cega como hoje.
3. **PARAR e VALIDAR** com o Cenário 1 antes de seguir.

### Incremental Delivery

1. Setup + Foundational → base pronta.
2. US1 (mapa/solicitar sem pagamento prévio) → validar → build/demo.
3. US2 (PitTec encontrado) → validar → build/demo.
4. US3 (valores + início real) → validar → build/demo — só agora o fluxo completo bate com a spec.
5. Polish.

---

## Notes

- Nenhuma dependência nova é adicionada.
- Migration única, aditiva, compartilhada com o iOS (que não é atualizado nesta spec).
- `PitTecMockModels.kt`/`PitTecClientMockViewModel.kt`/demais artefatos do protótipo `017` mantidos
  intencionalmente sem uso — não fazem parte do escopo desta feature.
- Faça commit por tarefa ou grupo lógico; pare em qualquer checkpoint para validar a story
  isoladamente antes de seguir.
