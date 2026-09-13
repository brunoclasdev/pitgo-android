---

description: "Dependency-ordered implementation tasks for Mercado Pago payments (Pix and saved card)"
---

# Tasks: Pagamento de serviços (Pix e cartão)

**Input**: Design documents from `specs/018-mercadopago-payments/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`

**Tests**: Automated test tasks are intentionally omitted — neither the feature nor the user
requested them, and the project constitution forbids generating tests automatically. Each story
ends with a manual validation task tied to the matching section of `quickstart.md`.

**Organization**: Tasks are grouped by user story (US1 Pix, US2 cadastro de cartão, US4 reembolso
automático, US3 pagar com cartão salvo — in that priority/dependency order) so each can be
implemented and validated as an independent increment.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel — touches different files and has no dependency on an unfinished
  task in this list.
- **[Story]**: Maps the task to US1/US2/US3/US4 from `spec.md`.
- Every task names its exact target file(s).

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Non-secret wiring shared by every later phase.

- [X] T001 [P] Add the Mercado Pago Android SDK to the version catalog and wire a
  `MERCADO_PAGO_PUBLIC_KEY` `buildConfigField` (public key only, never the Access Token) sourced
  from a Gradle/`local.properties` property in `gradle/libs.versions.toml` and
  `app/build.gradle.kts`
- [X] T002 [P] Register the five new Edge Functions (`mp-create-payment`, `mp-save-card`,
  `mp-remove-card`, `mp-webhook`, `mp-refund-payment`) — `mp-webhook` with `verify_jwt = false`
  (called by Mercado Pago, validated by signature instead) and the rest with JWT verification on —
  and document only non-secret environment variable **names** (`MP_ACCESS_TOKEN`,
  `MP_WEBHOOK_SECRET`) in `supabase/config.toml` and `supabase/.env.example`
- [X] T003 [P] Add localized strings and vector icons for Pix, cartão salvo and "Formas de
  pagamento" in `app/src/main/res/values/strings.xml`, `app/src/main/res/drawable/ic_payment_pix.xml`,
  and `app/src/main/res/drawable/ic_payment_card.xml`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Database contract and shared Kotlin model that every payment story depends on.

**⚠️ CRITICAL**: No user story work begins until this phase passes `supabase db lint` and the new
Kotlin model compiles.

- [X] T004 Create the `payments` table, its status/method enums, indexes and RLS policy
  (`client_id = auth.uid()` for `SELECT`, no direct client writes) in
  `supabase/migrations/20260912000000_mercadopago_payments.sql` per `data-model.md`
- [X] T005 In the same migration file, implement the `get_payment_status`, `list_saved_cards` and
  `remove_saved_card` `SECURITY DEFINER` RPCs per `contracts/payment-rpcs.md` (depends on T004)
- [X] T006 In the same migration file, add the `PAYMENT_REQUIRED` guard clause to
  `start_mobile_dispatch` and `start_pittec_dispatch` per `contracts/payment-rpcs.md`, without
  changing any other existing behavior of those RPCs (depends on T004)
- [X] T007 [P] Create the shared `Payment`/`PaymentStatus`/`PaymentMethod` (`PIX`, `SAVED_CARD`)
  domain model and its Edge-Function/RPC DTOs in `features/payment/model/PaymentModels.kt` and
  `features/payment/data/PaymentDtos.kt`, reusing `MobileDispatchKind` from
  `features/mobiledispatch/model/MobileDispatchModels.kt` for the dispatch-kind field, per
  `data-model.md`

**Checkpoint**: Migration lints; both dispatch RPCs now reject starts without an approved payment;
the shared payment model compiles standalone.

---

## Phase 3: User Story 1 - Pagar a solicitação de serviço via Pix (Priority: P1) 🎯 MVP

**Goal**: Cliente paga via Pix ao finalizar uma solicitação (padrão ou PitTec) e o despacho do
prestador só ocorre depois da confirmação.

**Independent Test**: Gerar um Pix de teste, aprová-lo no sandbox do Mercado Pago e confirmar que o
despacho é liberado; gerar outro e deixá-lo expirar, confirmando que nenhum prestador é acionado.

### Implementation for User Story 1

- [X] T008 [P] [US1] Implement the Pix branch of `mp-create-payment` (idempotency key = `request_id`,
  reuse-if-pending check via RPC, `payer.identification` from the client's CPF, QR/`qr_code_base64`
  passthrough) in `supabase/functions/mp-create-payment/index.ts` per
  `contracts/mercadopago-edge-functions.md`
- [X] T009 [P] [US1] Implement `mp-webhook` (HMAC signature validation, refetch by `id` from the
  Mercado Pago API, `payments` update, Realtime publish on `payment:<request_id>`) in
  `supabase/functions/mp-webhook/index.ts` per `contracts/mercadopago-edge-functions.md` and
  `contracts/payment-realtime-ui.md`
- [X] T010 [P] [US1] Implement `PaymentRepository` (`createPayment`, `observe(requestId)` via the
  private Realtime channel, `getStatus` RPC fallback) in
  `features/payment/repository/PaymentRepository.kt`, following the same channel/error-mapping
  pattern as `features/mobiledispatch/repository/MobileDispatchRepository.kt` (depends on T007)
- [X] T011 [US1] Implement `PaymentCheckoutViewModel` and its UI state
  (`SelectingMethod`, `CreatingPayment`, `AwaitingPix`, `Approved`, `Declined`, `Expired`,
  `Failure`) in `features/payment/viewmodel/PaymentCheckoutViewModel.kt` and
  `features/payment/model/PaymentCheckoutUiState.kt` per `contracts/payment-realtime-ui.md`
  (depends on T010)
- [X] T012 [US1] Implement `PaymentCheckoutScreen` with the Pix branch (QR image, copia-e-cola,
  expiration countdown, "gerar novo código"/"trocar forma de pagamento" actions) in
  `features/payment/ui/PaymentCheckoutScreen.kt` (depends on T011)
- [X] T013 [US1] Connect the MOBILE_SEQUENTIAL (padrão) auto-dispatch flow to payment — the real
  "fluxo padrão", entered from Home, is `ClientMobileDispatchViewModel`/`mobile-dispatch/new/{serviceId}`,
  not the unrelated (and separately incomplete) `RequestService`/BROWSE screen: add a
  `payment/{serviceId}/{dispatchKind}` route that Home navigates to instead of
  `mobileDispatchNew(...)` directly for `MOBILE_SEQUENTIAL` services, generating the `request_id`
  and reading the amount from `HomeService.minimumPrice`; on `Approved`, navigate to
  `mobile-dispatch/new/{serviceId}?requestId=...`, and make
  `ClientMobileDispatchViewModel.startWithPreciseLocation()` accept and auto-start with that id in
  `core/navigation/PitGoNavHost.kt` and `features/mobiledispatch/viewmodel/ClientMobileDispatchViewModel.kt`
  (depends on T012)
- [X] T014 [US1] Connect the PitTec on-demand call the same way: Home navigates to the
  `payment/{serviceId}/{dispatchKind}` route for `PITTEC_AUTO_SEQUENTIAL` services instead of
  `pitTec(...)` directly; on `Approved`, navigate to `pittec/{serviceId}?requestId=...`, and make
  `PitTecViewModel`'s `requestPitTec()`/init accept and auto-start with that id in
  `core/navigation/PitGoNavHost.kt` and `features/pittec/viewmodel/PitTecViewModel.kt`
  (depends on T012)
- [ ] T015 [US1] Execute and record Cenário 1 (Pix, including expiration) from
  `specs/018-mercadopago-payments/quickstart.md` — **blocked in this environment**: no device/emulator, deployed Supabase project or Mercado Pago sandbox credentials available

**Checkpoint**: A client can pay via Pix and reach dispatch in both flows; no dispatch happens
without an approved payment.

---

## Phase 4: User Story 2 - Cadastrar um cartão como forma de pagamento (Priority: P2)

**Goal**: Cliente cadastra, lista e remove cartões de forma totalmente opcional numa área dedicada,
sem afetar nenhuma outra funcionalidade do app.

**Independent Test**: Cadastrar um cartão de teste válido e um de teste de recusa na área de formas
de pagamento; confirmar que só bandeira/últimos 4 dígitos ficam visíveis e que nada no app exige
esse cadastro.

### Implementation for User Story 2

- [X] T016 [P] [US2] Implement `mp-save-card` (create-or-reuse Mercado Pago Customer for the
  authenticated `client_id`, save the card token as a Customer Card, return brand/last-4) in
  `supabase/functions/mp-save-card/index.ts` per `contracts/mercadopago-edge-functions.md`
- [X] T017 [P] [US2] Implement `mp-remove-card` (ownership check, remove the Customer Card upstream,
  call `remove_saved_card`) in `supabase/functions/mp-remove-card/index.ts` per
  `contracts/mercadopago-edge-functions.md`
- [X] T018 [P] [US2] Create the `SavedCard` domain model and `SavedCardRepository`
  (`list`, `save(cardToken)`, `remove(id)`) in `features/paymentmethods/model/SavedCardModels.kt`
  and `features/paymentmethods/repository/SavedCardRepository.kt` per `data-model.md`
- [X] T019 [US2] Implement `PaymentMethodsViewModel` and its UI state (`Loading`, `Empty`,
  `Listing`, `Registering`, `RegisterFailed`, `Removing`) in
  `features/paymentmethods/viewmodel/PaymentMethodsViewModel.kt` and
  `features/paymentmethods/model/PaymentMethodsUiState.kt` per `contracts/payment-realtime-ui.md`
  (depends on T018)
- [X] T020 [US2] Implement `PaymentMethodsScreen`, embedding the Mercado Pago SDK card form for
  tokenization and listing/removing saved cards, in
  `features/paymentmethods/ui/PaymentMethodsScreen.kt` (depends on T019)
- [X] T021 [US2] Add a "Formas de pagamento" card to Profile that opens the new screen, in
  `features/profile/ui/ProfileScreen.kt`, `core/navigation/PitGoRoutes.kt` and
  `core/navigation/PitGoNavHost.kt` (depends on T020)
- [ ] T022 [US2] Execute and record Cenário 2 from `specs/018-mercadopago-payments/quickstart.md` — **blocked in this environment**: no device/emulator, deployed Supabase project or Mercado Pago sandbox credentials available

**Checkpoint**: Cartões podem ser cadastrados, listados e removidos independentemente do checkout;
nenhum fluxo existente exige esse cadastro.

---

## Phase 5: User Story 4 - Reembolso automático quando o atendimento pago não se concretiza (Priority: P2)

**Goal**: Estorno automático total quando o cliente cancela antes da conclusão, ou quando a busca
PitTec termina sem prestador — com retry e fallback para suporte se o próprio estorno falhar.

**Independent Test**: Pagar uma solicitação, cancelar antes da conclusão (e, separadamente, esgotar
a busca PitTec) e confirmar o estorno automático; forçar falha do estorno e confirmar o aviso de
"reembolso em andamento" ao cliente.

### Implementation for User Story 4

- [X] T023 [P] [US4] Implement `mp-refund-payment` (refund call, `refund_attempts` bookkeeping,
  retry with the backoff from `research.md` §7, mark `REFUNDED` or `REFUND_FAILED` after 3
  failures) in `supabase/functions/mp-refund-payment/index.ts` per
  `contracts/mercadopago-edge-functions.md`
- [X] T024 [US4] In `supabase/migrations/20260912000000_mercadopago_payments.sql`, add the
  refund-trigger guard to `cancel_mobile_appointment`, the PitTec cancellation RPC and the PitTec
  exhausted-search RPC per `contracts/payment-rpcs.md` (depends on T006)
- [X] T025 [P] [US4] Surface `REFUNDED`/`REFUND_FAILED` feedback ("valor estornado" /
  "reembolso em andamento, nossa equipe já foi avisada") on the cancellation and tracking UI in
  `features/appointmenttracking/ui/AppointmentTrackingScreen.kt` and
  `features/mobiledispatch/ui/ClientMobileDispatchScreen.kt`, reading `PaymentStatus` from
  `features/payment/model/PaymentModels.kt`
- [ ] T026 [US4] Execute and record Cenário 4 from `specs/018-mercadopago-payments/quickstart.md` — **blocked in this environment**: no device/emulator, deployed Supabase project or Mercado Pago sandbox credentials available

**Checkpoint**: Cancelamentos pré-conclusão e buscas PitTec exauridas sempre resultam em estorno ou
em aviso claro de reembolso em andamento — nunca em silêncio.

---

## Phase 6: User Story 3 - Pagar a solicitação de serviço com cartão salvo (Priority: P3)

**Goal**: Cliente com cartão já cadastrado escolhe esse cartão na etapa de pagamento em vez de Pix.

**Independent Test**: Com um cartão de teste já salvo, pagar uma solicitação escolhendo esse cartão
e confirmar aprovação e despacho; repetir com um cartão de teste de recusa.

### Implementation for User Story 3

- [X] T027 [US3] Implement the `SAVED_CARD` branch of `mp-create-payment` (charge using the
  `mp_card_id` as `token`, same idempotency key strategy as the Pix branch) in
  `supabase/functions/mp-create-payment/index.ts` (depends on T008)
- [X] T028 [US3] Extend `PaymentCheckoutViewModel`/`PaymentCheckoutScreen` to list saved cards
  (via `SavedCardRepository`) alongside Pix, and to submit `method = SAVED_CARD` — or fall back to
  Pix-only when there are no saved cards — in
  `features/payment/viewmodel/PaymentCheckoutViewModel.kt` and
  `features/payment/ui/PaymentCheckoutScreen.kt` (depends on T018, T027)
- [ ] T029 [US3] Execute and record Cenário 3 from `specs/018-mercadopago-payments/quickstart.md` — **blocked in this environment**: no device/emulator, deployed Supabase project or Mercado Pago sandbox credentials available

**Checkpoint**: Todas as quatro user stories funcionam de forma independente e integrada.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Verificação final cruzando todas as stories.

- [ ] T030 Run `supabase db lint`, `deno check` on all five functions under `supabase/functions/`,
  `./gradlew :app:assembleDebug` and `./gradlew :app:lintDebug`, resolving every failure introduced
  by this feature — **partially done**: `./gradlew :app:compileDebugKotlin` and `:app:lintDebug`
  both pass clean (verified against the real Mercado Pago SDK 1.0.0 artifacts, including its
  Secure Fields tokenization API, resolved from `https://artifacts.mercadolibre.com/repository/android-releases`)
  — **blocked in this environment**: no Supabase CLI or Deno installed, so `supabase db lint` and
  `deno check` could not run
- [ ] T031 Execute and record Cenário 5 (paridade PitTec/fluxo padrão) and the "Verificações
  técnicas complementares" section (nenhum log expõe PAN/CVV/Access Token; cobrança duplicada não
  ocorre) from `specs/018-mercadopago-payments/quickstart.md` — **partially done**: a code review
  confirmed no `console.log`/`Log`/`println` in any new file touches PAN, CVV, tokens or the
  Access Token — **blocked in this environment**: the live sandbox scenario (device/emulator +
  deployed Supabase project + Mercado Pago test credentials) could not run
- [ ] T032 Confirm the Rollout and rollback checklist in `specs/018-mercadopago-payments/plan.md`
  (secrets set, migration and all five functions deployed) before releasing the APK that requires
  approved payment to dispatch — **blocked in this environment**: requires a deployed Supabase
  project and real Mercado Pago credentials; must be completed before production rollout

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS every user story.
- **User Story 1 (Phase 3)**: Depends on Foundational only. This is the MVP.
- **User Story 2 (Phase 4)**: Depends on Foundational only; independent of US1's code, but ordered
  after it here because P1 outranks P2.
- **User Story 4 (Phase 5)**: Depends on Foundational (T006) and needs at least one approved
  payment to exist to be meaningfully tested — implemented after US1.
- **User Story 3 (Phase 6)**: Depends on US2 (needs a saved card, T018) and reuses US1's checkout
  UI/Edge Function (T012, T008) — the only story that isn't independent of another story's code.
- **Polish (Phase 7)**: Depends on all four stories being complete.

### Within Each User Story

- Edge Functions before repositories before ViewModels before UI before wiring/integration before
  the manual validation task.

### Parallel Opportunities

- All of Phase 1 (T001-T003) can run in parallel.
- T004→T005→T006 are sequential (same migration file); T007 can run in parallel with all three.
- Within US1: T008 and T009 (different Edge Function files) can run in parallel; T010 can run in
  parallel with both once T007 is done.
- Within US2: T016, T017 and T018 can run in parallel.
- Within US4: T023 can run in parallel with T024/T025.

---

## Parallel Execution Examples

### Foundational

```bash
Task: "Create the shared Payment/PaymentStatus/PaymentMethod domain model in features/payment/model/PaymentModels.kt"
# ...while, separately:
Task: "Create payments table, enums, indexes and RLS in supabase/migrations/20260912000000_mercadopago_payments.sql"
```

### User Story 1

```bash
Task: "Implement the Pix branch of mp-create-payment in supabase/functions/mp-create-payment/index.ts"
Task: "Implement mp-webhook in supabase/functions/mp-webhook/index.ts"
```

### User Story 2

```bash
Task: "Implement mp-save-card in supabase/functions/mp-save-card/index.ts"
Task: "Implement mp-remove-card in supabase/functions/mp-remove-card/index.ts"
Task: "Create SavedCard model and SavedCardRepository in features/paymentmethods/"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 (Setup) and Phase 2 (Foundational).
2. Complete Phase 3 (US1 — Pix). This alone lets clients pay and get dispatched in both flows.
3. **STOP and VALIDATE** with Cenário 1 of `quickstart.md` before continuing.

### Incremental Delivery

1. Setup + Foundational → base pronta.
2. US1 (Pix) → valida → deploy/demo (MVP).
3. US2 (cadastro de cartão) → valida → deploy/demo.
4. US4 (reembolso automático) → valida → deploy/demo.
5. US3 (pagar com cartão salvo, depende de US2) → valida → deploy/demo.
6. Polish.

---

## Notes

- [P] tasks touch different files and have no unmet dependency in this list.
- Nenhuma tarefa grava Access Token, PAN ou CVV em código, log ou migração.
- Migrações são forward-only: T004/T005/T006/T024 editam o mesmo arquivo novo, nunca uma migração
  já aplicada.
- Faça commit após cada tarefa ou grupo lógico; pare em qualquer checkpoint para validar a story
  isoladamente antes de seguir para a próxima.
