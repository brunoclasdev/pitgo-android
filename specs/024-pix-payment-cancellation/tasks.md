---

description: "Task list for Pix e cancelamento do pagamento"
---

# Tasks: Pix e cancelamento do pagamento

**Input**: Design documents from `specs/024-pix-payment-cancellation/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/payment-pix-cancellation.md`, `quickstart.md`

**Tests**: No automated test tasks were requested in the specification. Validation is defined in `quickstart.md` and the final polish phase.

## Phase 1: Setup

**Purpose**: Confirm the existing payment and PitTec paths before changing behavior.

- [X] T001 Review `specs/024-pix-payment-cancellation/spec.md`, `plan.md`, `data-model.md`, and `contracts/payment-pix-cancellation.md` and record the affected current states in the implementation notes of `supabase/functions/_shared/payment-cancellation.ts`
- [X] T002 [P] Verify the current production-safe payment strings and add only missing user-facing labels in `app/src/main/res/values/strings.xml`
- [X] T003 [P] Verify the current feature navigation entry points and identify the PitTec home destination in `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt`

---

## Phase 2: Foundational

**Purpose**: Establish the shared payment snapshot, cancellation, and idempotency rules before story work.

**⚠️ CRITICAL**: User story implementation depends on these contracts remaining consistent.

- [X] T004 Update the shared payment snapshot and Pix completeness rules in `supabase/functions/_shared/payment-response.ts` so `pix` is returned only when QR text, QR base64, and expiry are valid
- [X] T005 [P] Align Android payment decoding and domain validation in `app/src/main/java/com/trustarx/pitgo/features/payment/data/PaymentDtos.kt` and `app/src/main/java/com/trustarx/pitgo/features/payment/model/PaymentModels.kt` with the complete snapshot contract
- [X] T006 [P] Review and adjust the cancellation/reconciliation state transitions in `supabase/functions/_shared/payment-cancellation.ts` and the related SQL RPC migration so pre-approval cancellation becomes `CANCELLED` and approved cancellation remains on the refund path
- [X] T007 Confirm that cancellation and refund requests remain idempotent and authenticated in `supabase/functions/mp-cancel-payment/index.ts`, `supabase/functions/mp-refund-payment/index.ts`, and `supabase/migrations/`

**Checkpoint**: Shared payment responses and cancellation states are defined before UI and navigation changes.

---

## Phase 3: User Story 1 - Exibir pagamento Pix completo (Priority: P1) 🎯 MVP

**Goal**: When the client selects Pix, show a valid QR Code and Pix Copia e Cola for the R$ 1,00 charge and leave loading with an actionable result when the provider response is incomplete.

**Independent Test**: From an accepted PitTec proposal, select Pix and verify R$ 1,00, visible QR Code, copy action feedback, recovery after reopening, and an actionable state for missing QR data.

### Implementation

- [X] T008 [P] [US1] Render the QR image, the Copia e Cola text, copy action feedback, and the R$ 1,00 amount in `app/src/main/java/com/trustarx/pitgo/features/payment/ui/PaymentCheckoutScreen.kt`
- [X] T009 [P] [US1] Add or align Pix loading, incomplete-response, retry, expiry, and copy-feedback strings in `app/src/main/res/values/strings.xml`
- [X] T010 [US1] Update `app/src/main/java/com/trustarx/pitgo/features/payment/viewmodel/PaymentCheckoutViewModel.kt` to distinguish a complete Pix snapshot from a pending provider response and prevent an indefinite spinner
- [X] T011 [US1] Update `app/src/main/java/com/trustarx/pitgo/features/payment/model/PaymentCheckoutUiState.kt` if a dedicated incomplete-Pix or retryable state is needed, keeping mutually exclusive states explicit
- [X] T012 [US1] Verify the existing `PaymentRepository` recovery path in `app/src/main/java/com/trustarx/pitgo/features/payment/repository/PaymentRepository.kt` returns the same Pix snapshot after screen recreation without creating a duplicate attempt

**Checkpoint**: US1 is independently demonstrable with QR rendering, Copia e Cola, retry/error feedback, and recovery.

---

## Phase 4: User Story 2 - Cancelar e retornar à solicitação PitTec (Priority: P1)

**Goal**: Persist cancellation correctly, return the client to the initial PitTec request screen, and preserve approved payments while starting the refund workflow.

**Independent Test**: Cancel before approval and after approval, verify persisted states, return navigation, removal of the old active request, and idempotent behavior on repeated actions.

### Implementation

- [X] T013 [P] [US2] Implement or adjust the authoritative cancellation RPC/migration under `supabase/migrations/` to set the PitTec request to `CANCELLED` and the unpaid payment to `CANCELLED` while preserving approved payments
- [X] T014 [P] [US2] Align the refund scheduling and result transitions in `supabase/functions/_shared/payment-cancellation.ts`, `supabase/functions/mp-cancel-payment/index.ts`, and `supabase/functions/mp-refund-payment/index.ts` with `REFUND_PENDING`, `REFUNDED`, and `REFUND_FAILED`
- [X] T015 [US2] Update `app/src/main/java/com/trustarx/pitgo/features/payment/repository/PaymentRepository.kt` to classify cancellation responses and retain the authoritative payment snapshot on timeout or conflict
- [X] T016 [US2] Update `app/src/main/java/com/trustarx/pitgo/features/payment/viewmodel/PaymentCheckoutViewModel.kt` so cancel, back, repeated taps, and concurrent cancellation requests execute at most one operation and wait for the persisted result
- [X] T017 [US2] Update `app/src/main/java/com/trustarx/pitgo/features/payment/ui/PaymentCheckoutScreen.kt` to route every visible cancellation action through the same confirmed cancellation callback and show cancellation progress/errors
- [X] T018 [US2] Update `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt` to return to the initial PitTec request route only after cancellation is registered and refresh the active request state
- [X] T019 [US2] Update `app/src/main/java/com/trustarx/pitgo/features/pittec/viewmodel/PitTecViewModel.kt` and related PitTec UI state handling to remove cancelled requests from active state and prevent the stale unavailable banner
- [X] T020 [US2] Preserve the approved payment history and route approved cancellation through refund status handling in `app/src/main/java/com/trustarx/pitgo/features/payment/model/PaymentModels.kt` and `app/src/main/java/com/trustarx/pitgo/features/payment/ui/PaymentCheckoutScreen.kt`

**Checkpoint**: US2 is independently demonstrable with pre-approval cancellation, post-approval refund initiation, correct database status, and clean return navigation.

---

## Phase 5: User Story 3 - Usar valor temporário de teste (Priority: P1)

**Goal**: Keep every payment amount at exactly R$ 1,00 throughout the client display, persisted payment, and provider request.

**Independent Test**: Create a PitTec Pix or saved-card payment and verify that every displayed, persisted, and submitted amount is `1.00`; reject any other value.

### Implementation

- [X] T021 [US3] Set the temporary client amount to `1.00` at the payment command boundary in `app/src/main/java/com/trustarx/pitgo/features/payment/viewmodel/PaymentCheckoutViewModel.kt` while preserving PitTec appointment and quote context
- [X] T022 [US3] Enforce the temporary `1.00` amount in the authoritative reservation/payment SQL migration under `supabase/migrations/` and reject mismatched client values
- [X] T023 [US3] Ensure `supabase/functions/mp-create-payment/index.ts` sends only the persisted `1.00` amount to Mercado Pago for Pix and saved-card Orders
- [X] T024 [US3] Validate the returned amount and currency before exposing payment success in `app/src/main/java/com/trustarx/pitgo/features/payment/data/PaymentDtos.kt` and `app/src/main/java/com/trustarx/pitgo/features/payment/model/PaymentModels.kt`

**Checkpoint**: US3 is independently demonstrable with R$ 1,00 for both payment methods and no alternate amount accepted.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Validate integration, deployment safety, and the complete quickstart flow.

- [X] T025 [P] Update the feature contract and quickstart references in `specs/024-pix-payment-cancellation/contracts/payment-pix-cancellation.md` and `specs/024-pix-payment-cancellation/quickstart.md` if implementation response codes or state names change
- [X] T026 [P] Review Android logs and user-facing errors in `app/src/main/java/com/trustarx/pitgo/features/payment/` and `app/src/main/java/com/trustarx/pitgo/features/pittec/` to ensure tokens, card data, CPF, and full Pix payloads are never logged
- [X] T027 Build and lint the Android app with `./gradlew :app:assembleDebug` and `./gradlew :app:lintDebug`
- [X] T028 Apply and validate Supabase migrations/functions with `npx supabase db push --project-ref eetbrkznuqbdrnybnyhf`, `npx supabase db lint --project-ref eetbrkznuqbdrnybnyhf`, and deploy only the changed `mp-*` functions
- [ ] T029 Run all scenarios in `specs/024-pix-payment-cancellation/quickstart.md`, record the database states for cancellation and refund, and verify no duplicate payment is created
- [ ] T030 Install the resulting APK on all four connected Android devices and validate the Pix, cancellation, fixed-value, and recovery flows on the client device

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 Setup**: No dependencies; T002 and T003 can run in parallel after T001 context review.
- **Phase 2 Foundational**: Depends on T001; T004–T007 can be split by backend and Android owners, but contract alignment must complete before story validation.
- **Phase 3 US1**: Depends on Phase 2; T008–T012 can be parallelized by UI, strings, ViewModel, and repository owners, with T010/T011 completed before the checkpoint.
- **Phase 4 US2**: Depends on Phase 2 and can proceed in parallel with US1, but T018/T019 integrate with the existing PitTec navigation/state and should be validated after T016.
- **Phase 5 US3**: Depends on the payment contract from Phase 2; T021–T024 should be completed before final end-to-end validation.
- **Phase 6 Polish**: Depends on all selected user stories.

### User Story Dependencies

- **US1 (P1)**: Depends on foundational snapshot validation; no dependency on US2 or US3 beyond the shared payment contract.
- **US2 (P1)**: Depends on foundational cancellation contract; independent of QR rendering but shares the PaymentCheckout ViewModel and navigation.
- **US3 (P1)**: Can start after foundational contract work; its backend amount enforcement must be complete before US1/US2 end-to-end testing is considered valid.

### Parallel Opportunities

- T002 and T003 can run in parallel.
- T004 and T005 can run in parallel; T006 and T007 can run in parallel after state rules are agreed.
- Within US1, T008/T009 and T011 can run in parallel; T010 integrates their states.
- Within US2, T013/T014 and T015 can run in parallel; T016–T020 integrate the backend result into the UI/navigation.
- Within US3, T021 and T022 can run in parallel; T023 depends on the authoritative amount; T024 follows the response contract.
- T025 and T026 can run in parallel with final build preparation.

## Parallel Example: User Story 1

```text
Task T008: Implement QR/Copia e Cola presentation in PaymentCheckoutScreen.kt
Task T009: Align Pix loading/error/copy strings in strings.xml
Task T011: Add an explicit incomplete-Pix UI state in PaymentCheckoutUiState.kt
```

## Parallel Example: User Story 2

```text
Task T013: Adjust cancellation RPC/migration states
Task T014: Align cancellation/refund Edge Functions
Task T015: Classify repository cancellation responses
```

## Parallel Example: User Story 3

```text
Task T021: Enforce R$ 1,00 at Android command boundary
Task T022: Enforce R$ 1,00 in authoritative SQL reservation
```

## Implementation Strategy

### MVP First (User Story 1)

1. Complete Phase 1 and Phase 2.
2. Complete US1 tasks T008–T012.
3. Validate QR, Copia e Cola, recovery, missing-data error, and R$ 1,00.
4. Stop and demonstrate the Pix screen before integrating the full cancellation flow.

### Incremental Delivery

1. Add US2 cancellation and return navigation.
2. Add US3 authoritative fixed-value enforcement.
3. Run the complete quickstart and deployment checks.
4. Build and install on all connected Android devices.

## Notes

- Every task follows the required checklist format and includes the concrete file path it changes or validates.
- Automated test files are intentionally omitted because the specification did not request automated tests.
- The temporary R$ 1,00 rule must be removed or replaced by a future pricing requirement before commercial rollout.
