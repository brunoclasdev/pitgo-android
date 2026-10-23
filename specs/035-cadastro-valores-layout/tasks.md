---
description: "Implementation tasks for provider signup, pricing, and layout"
---

# Tasks: Cadastro, valores e layout

**Input**: Design documents from `/specs/035-cadastro-valores-layout/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, and `contracts/`

**Tests**: No automated test tasks are included; the project constitution requires explicit request before generating tests. Each story has an independent manual acceptance criterion and final validation follows `quickstart.md`.

**Organization**: Tasks are grouped by the five user stories in `spec.md`. Database constraints and RPCs remain authoritative; Android repositories only call the approved contracts.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Add shared provider-kind representation used across signup and provider registration.

- [X] T001 [P] Add `ProviderKind` values and operational-mode mapping in `app/src/main/java/com/trustarx/pitgo/core/account/ProviderKind.kt`
- [X] T002 [P] Add nullable `providers.provider_kind`, safe backfill for unambiguous legacy rows, and constraint/index groundwork in `supabase/migrations/20261023100000_provider_kind_foundation.sql`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Persist selected provider kind through the existing provider-registration contract while preserving legacy registrations.

- [X] T003 Extend `create_provider_registration` to accept and persist provider kind, map new kinds to PITTEC/MOBILE/FIXED operational modes, and keep legacy calls compatible in `supabase/migrations/20261023101000_provider_kind_registration_rpc.sql`
- [X] T004 Add provider-kind serialization to the registration request and result DTOs in `app/src/main/java/com/trustarx/pitgo/features/providerregistration/data/ProviderRegistrationDtos.kt`
- [X] T005 Pass provider kind through registration repository calls and legacy-provider loading in `app/src/main/java/com/trustarx/pitgo/features/providerregistration/repository/ProviderRegistrationRepository.kt`

**Checkpoint**: A selected commercial kind can be persisted without changing dispatch mode semantics or reclassifying existing FIXED providers.

---

## Phase 3: User Story 1 - Escolher o tipo de cadastro (Priority: P1) 🎯 MVP

**Goal**: Let a new provider choose PitTec, guincho, or loja before entering modality-specific data and route to the matching form.

**Independent Test**: Start signup, choose each kind, verify the matching registration route opens, go back and change kind, and verify fields from the prior choice are not submitted.

### Implementation for User Story 1

- [X] T006 [P] [US1] Add a provider-kind choice screen with PitTec, guincho, and loja options in `app/src/main/java/com/trustarx/pitgo/features/providersignup/ui/ProviderKindSelectionScreen.kt`
- [X] T007 [US1] Add the provider-kind selection route and carry the selected kind into provider signup and registration in `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoRoutes.kt` and `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt`
- [X] T008 [US1] Update the company-registration entry action to open provider-kind selection instead of directly opening provider signup in `app/src/main/java/com/trustarx/pitgo/features/welcome/ui/WelcomeScreen.kt`
- [X] T009 [US1] Store the chosen kind in provider signup state and include it in the registration handoff in `app/src/main/java/com/trustarx/pitgo/features/providersignup/model/ProviderSignUpModels.kt`, `app/src/main/java/com/trustarx/pitgo/features/providersignup/viewmodel/ProviderSignUpViewModel.kt`, and `app/src/main/java/com/trustarx/pitgo/features/providersignup/repository/ProviderSignUpRepository.kt`
- [X] T010 [US1] Render the selected kind as the registration identity and clear kind-specific draft fields when the user changes it in `app/src/main/java/com/trustarx/pitgo/features/providerregistration/model/ProviderRegistrationModels.kt` and `app/src/main/java/com/trustarx/pitgo/features/providerregistration/viewmodel/ProviderRegistrationViewModel.kt`

**Checkpoint**: Signup offers all three kinds and can be changed before submission without leaking mode-specific form values.

---

## Phase 4: User Story 2 - Informar endereço e veículo do guincho (Priority: P1)

**Goal**: Save the typed provider address without a map-selection step, geocode it server-side for discovery, and require a complete guincho vehicle.

**Independent Test**: Submit a typed address and complete vehicle; verify exact address text and derived coordinates are saved, missing/duplicate vehicle fields block submission, and geocode errors preserve form values for retry.

### Implementation for User Story 2

- [X] T011 [P] [US2] Add provider-owned vehicle schema, plate normalization/uniqueness, owner RLS, and atomic vehicle input to provider registration in `supabase/migrations/20261023102000_provider_vehicles_registration.sql`
- [X] T012 [P] [US2] Change address resolution to geocode the submitted text without map/device proof and preserve that text as canonical address in `supabase/functions/geocode-provider-address/index.ts`
- [X] T013 [US2] Remove map verification state and adapt typed-address validation and retryable geocode errors in `app/src/main/java/com/trustarx/pitgo/features/providerregistration/repository/ProviderAddressVerificationRepository.kt`, `app/src/main/java/com/trustarx/pitgo/features/providerregistration/validation/ProviderRegistrationValidation.kt`, and `app/src/main/java/com/trustarx/pitgo/features/providerregistration/viewmodel/ProviderRegistrationViewModel.kt`
- [X] T014 [US2] Add plate, make, model, and color to the guincho registration draft and require all four fields before submission in `app/src/main/java/com/trustarx/pitgo/features/providerregistration/model/ProviderRegistrationModels.kt` and `app/src/main/java/com/trustarx/pitgo/features/providerregistration/validation/ProviderRegistrationValidation.kt`
- [X] T015 [US2] Add the guincho vehicle form and typed-address-only fields, retaining draft values after field and geocode errors, in `app/src/main/java/com/trustarx/pitgo/features/providerregistration/ui/ProviderRegistrationScreen.kt`
- [X] T016 [US2] Serialize vehicle data and typed address through the registration RPC and display backend validation errors by field in `app/src/main/java/com/trustarx/pitgo/features/providerregistration/data/ProviderRegistrationDtos.kt` and `app/src/main/java/com/trustarx/pitgo/features/providerregistration/repository/ProviderRegistrationRepository.kt`

**Checkpoint**: Guincho signup cannot complete without the four vehicle fields; provider address is typed, preserved, and geocoded without a map-selection screen.

---

## Phase 5: User Story 3 - Usar valores definidos pela PitGo (Priority: P1)

**Goal**: Use PitGo-managed, validated, versioned PitTec and guincho pricing and duration for new quotes while preserving valid quote snapshots.

**Independent Test**: As manager staff, configure one PitTec service and guincho, verify matching providers share the PitTec amount, verify guincho fee math and duration, reject a non-manager update, and complete an older valid quote after activating a new version.

### Implementation for User Story 3

- [X] T017 [P] [US3] Add per-service PitTec pricing versions, extend guincho pricing fields, validate values/durations, and expose manager-only audited write and restricted read RPCs in `supabase/migrations/20261023110000_central_provider_pricing_schema.sql`
- [X] T018 [US3] Route PitTec candidate selection and offer calculation through the same central price/duration version in `supabase/migrations/20261023111000_central_pittec_quote_pricing.sql`
- [X] T019 [US3] Update guincho quote creation, simulation revalidation, payment reservation, and snapshot handling to use central config while leaving still-valid snapshots payable in `supabase/migrations/20261023112000_central_guincho_quote_pricing.sql`
- [X] T020 [US3] Keep provider price columns for compatibility but remove provider-entered price and duration from PitTec/guincho registration state and local validation in `app/src/main/java/com/trustarx/pitgo/features/providerregistration/model/ProviderRegistrationModels.kt` and `app/src/main/java/com/trustarx/pitgo/features/providerregistration/validation/ProviderRegistrationValidation.kt`
- [X] T021 [US3] Remove PitTec/guincho price and duration inputs from the provider registration form and show that PitGo defines current service terms in `app/src/main/java/com/trustarx/pitgo/features/providerregistration/ui/ProviderRegistrationScreen.kt`
- [X] T022 [US3] Remove provider price/duration values from registration RPC payload construction without dropping legacy database columns in `app/src/main/java/com/trustarx/pitgo/features/providerregistration/data/ProviderRegistrationDtos.kt` and `app/src/main/java/com/trustarx/pitgo/features/providerregistration/repository/ProviderRegistrationRepository.kt`
- [X] T023 [US3] Decode central pricing version and duration from PitTec and guincho quote responses in `app/src/main/java/com/trustarx/pitgo/features/pittec/data/PitTecDtos.kt`, `app/src/main/java/com/trustarx/pitgo/features/pittec/model/PitTecModels.kt`, `app/src/main/java/com/trustarx/pitgo/features/guincho/data/GuinchoDtos.kt`, and `app/src/main/java/com/trustarx/pitgo/features/guincho/model/GuinchoModels.kt`

**Checkpoint**: New quotes use an active central version, unauthorized updates fail, and quote/payment flows honor the stored snapshot after configuration changes.

---

## Phase 6: User Story 4 - Simplificar as áreas e os mapas (Priority: P2)

**Goal**: Remove provider mode selector buttons from company screens and keep address inputs in a purple-bordered floating card on address-entry maps.

**Independent Test**: Open provider registration/dashboard and both address-entry maps; verify no Fixo/Móvel/Pittec selectors remain, essential company actions remain available, and the map card stays visible and usable with panning, enlarged text, compact layout, and keyboard open.

### Implementation for User Story 4

- [X] T024 [US4] Replace Fixo/Móvel/Pittec registration buttons with a read-only selected provider kind while preserving company fields and actions in `app/src/main/java/com/trustarx/pitgo/features/providerregistration/ui/ProviderRegistrationScreen.kt`
- [X] T025 [P] [US4] Remove mode selector buttons from the provider dashboard and retain availability/status and business actions in `app/src/main/java/com/trustarx/pitgo/features/providerdashboard/ui/ProviderDashboardScreen.kt`
- [X] T026 [P] [US4] Add the reusable purple-bordered floating address card using the existing design system in `app/src/main/java/com/trustarx/pitgo/core/ui/AddressEntryMapCard.kt`
- [X] T027 [US4] Anchor guincho address fields in the floating card over the map and support compact/keyboard-visible layouts in `app/src/main/java/com/trustarx/pitgo/features/guincho/ui/GuinchoMapScreen.kt`
- [X] T028 [US4] Apply the shared floating card to the mobile-dispatch address-entry map without obscuring map controls in `app/src/main/java/com/trustarx/pitgo/features/mobiledispatch/ui/ClientMobileDispatchScreen.kt`

**Checkpoint**: Company areas retain essential actions without mode buttons, and address-entry maps use the responsive floating card.

---

## Phase 7: User Story 5 - Cadastrar produtos da loja (Priority: P2)

**Goal**: Let a store owner manage products with required name, description and price, optional photo, and a customer-visible active catalog.

**Independent Test**: Register a store, add products with and without a photo, verify active products appear in its customer catalog, and verify another account cannot edit them or read inactive items.

### Implementation for User Story 5

- [X] T029 [P] [US5] Create the STORE registration RPC (business details and typed address, no service offers), owner-scoped product schema/constraints, CRUD RPCs, and active-only customer reads in `supabase/migrations/20261023120000_store_product_catalog.sql`
- [X] T030 [US5] Add `store-product-images` storage bucket policies that bind uploads and mutations to the owning store provider in `supabase/migrations/20261023121000_store_product_images.sql`
- [X] T031 [P] [US5] Add store and product models with required fields and optional image state in `app/src/main/java/com/trustarx/pitgo/features/store/model/StoreModels.kt`
- [X] T032 [US5] Implement store registration and product catalog RPC/storage calls with owner-scoped errors and upload retry in `app/src/main/java/com/trustarx/pitgo/features/store/repository/StoreRepository.kt`
- [X] T033 [US5] Add product list/create/edit state and validation for nonblank name/description and positive price in `app/src/main/java/com/trustarx/pitgo/features/store/viewmodel/StoreViewModel.kt`
- [X] T034 [US5] Build the store registration and owner product-management screens, including optional photo selection, in `app/src/main/java/com/trustarx/pitgo/features/store/ui/StoreRegistrationScreen.kt` and `app/src/main/java/com/trustarx/pitgo/features/store/ui/StoreProductManagementScreen.kt`
- [X] T035 [US5] Add active product catalog presentation for customers and connect store registration/catalog routes in `app/src/main/java/com/trustarx/pitgo/features/store/ui/StoreCatalogScreen.kt` and `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt`

**Checkpoint**: Store registration creates a STORE provider without service offers, and its owner-managed catalog is visible to customers with active-only reads and no stock fields.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Validate integration, compatibility, accessibility and security across the completed stories.

- [ ] T036 Verify address card and company screens with compact width, enlarged font, supported orientation, and keyboard-visible states in `specs/035-cadastro-valores-layout/quickstart.md`
- [X] T037 Verify staff pricing authorization, audit writes, owner RLS, storage ownership, quote snapshots, and legacy provider compatibility against the contracts in `specs/035-cadastro-valores-layout/contracts/`
- [ ] T038 Run `git diff --check` and `./gradlew :app:assembleHomolDebug` and complete the acceptance walkthrough in `specs/035-cadastro-valores-layout/quickstart.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies; establishes the shared provider-kind representation in Kotlin and Supabase.
- **Foundational (Phase 2)**: Depends on Setup; blocks story work because signup and registration must persist provider kind safely.
- **User Stories (Phase 3+)**: Begin after Foundation. US1, US2, US3, and US4 can proceed in parallel after Phase 2 except where tasks touch the same provider registration files; US3's registration-form tasks should follow US2's form/DTO changes. US5 depends on US1's provider-kind registration path.
- **Polish (Phase 8)**: Depends on all selected stories.

### User Story Dependencies

- **US1 (P1)**: After Phase 2; MVP foundation for the registration choice. Store product creation is completed in US5.
- **US2 (P1)**: After Phase 2; independent of signup choice UI once provider kind is persisted.
- **US3 (P1)**: Pricing migrations are independent of US1/US2. Registration screen/DTO tasks T020–T022 follow T013–T016 because those stories edit shared registration files.
- **US4 (P2)**: After Phase 2; map-card work is independent. Its provider-registration selector removal T024 follows the US2/US3 edits to that shared screen.
- **US5 (P2)**: Depends on US1 provider kind and US2 typed-address/geocoding contract; store product/catalog tasks can then proceed independently of pricing and map-card UI.

### Within Each User Story

- Database schema/RPC contracts precede repositories that call them.
- Models and request/response DTOs precede ViewModel and UI integration.
- Keep provider registration RPC and Android DTO changes in sync within each story checkpoint.
- Automated test tasks are omitted under the project constitution; verify each independent test criterion manually.

### Parallel Opportunities

- T001 and T002 can run in parallel; T004 and T005 follow T003.
- After Phase 2, US2 vehicle schema/geocoding (T011–T012), US3 pricing schema (T017), and US4 dashboard/card work (T025–T026) can start in parallel.
- In US5, product schema (T029), image policies (T030), and product models (T031) can be developed in parallel; repository/UI wait for their contracts/models.
- Avoid parallel edits to `ProviderRegistrationScreen.kt`, `ProviderRegistrationModels.kt`, `ProviderRegistrationDtos.kt`, and `ProviderRegistrationRepository.kt`; finish US2 changes before US3 registration changes, then US4 selector cleanup.

## Parallel Example: User Story 3

```text
After Phase 2:
Task T017: Add pricing configuration schema and manager RPCs in supabase/migrations/20261023110000_central_provider_pricing_schema.sql
Task T026: Add shared address card in app/src/main/java/com/trustarx/pitgo/core/ui/AddressEntryMapCard.kt

After T017:
Task T018: Update PitTec candidate/offer pricing in supabase/migrations/20261023111000_central_pittec_quote_pricing.sql
Task T019: Update guincho quote and payment snapshots in supabase/migrations/20261023112000_central_guincho_quote_pricing.sql

After US1 and US2:
Task T029: Add store registration and product schema in supabase/migrations/20261023120000_store_product_catalog.sql
```

## Implementation Strategy

### MVP First (User Story 1)

1. Complete Setup and Foundational phases.
2. Implement provider-kind choice, routing, and kind propagation (US1).
3. Validate all three routes and changing the choice independently.
4. Continue with US2 and US3 before treating registration and commercial flows as production-ready; US1 is the smallest demonstrable increment, while US5 completes the store catalog path.

### Incremental Delivery

1. Complete Setup + Foundational → shared provider-kind persistence is ready.
2. Add US1 → validate the three registration routes.
3. Add US2 → validate typed address, geocoding, and guincho vehicle ownership.
4. Add US3 → validate central prices, authorization, calculation, and snapshots.
5. Add US4 → validate company hierarchy and map-card behavior.
6. Add US5 → validate store onboarding and customer-visible catalog.
7. Run cross-cutting checks in Phase 8 and confirm legacy flows remain functional.

## Notes

- Every implementation task has an ID and an exact target file path; `[P]` is used only when files are independent at that point in the dependency graph.
- There are no automated test tasks because the spec did not request tests and the project constitution prohibits automatic test generation.
- The admin dashboard frontend is outside this repository; pricing management is implemented through the manager RPC contract for the existing operational dashboard.

## Implementation status — 2026-10-22

- T001–T035 implemented. The existing provider registration form is reused for STORE business/address data; `StoreRegistrationScreen.kt` supplies the store-specific introduction and the owner catalog opens from the dashboard after registration.
- T008 changes the Welcome entry callback in `core/navigation/PitGoNavHost.kt`; `WelcomeScreen.kt` already delegates navigation and does not need a code change.
- T012 retains the existing automatic typed-address geocoding and adds coordinate range validation. The proof token stays internal to this automatic flow; the manual verification UI was removed.
- T037 checked with the actual new migrations in an isolated PostgreSQL/PGlite sandbox using a minimal existing schema and deterministic spatial helpers. Verified staff authorization/audit, tariff versions, preserved quote/payment snapshots, store/product ownership and inactive-product RLS, photo policies, and provider/store registration. This does not validate live PostGIS or external geocoding.
- T038 build and whitespace checks passed. T036 and the device acceptance portion of T038 remain pending after migrations are applied to homol. No remote migration, account change, or device installation was performed in this implementation step.

### Atualização de homologação

APK instalado e aberto nos quatro devices conectados. As oito migrations foram publicadas em homol a pedido do usuário via `npx supabase`; o dry-run posterior confirmou banco atualizado. T036 e o roteiro funcional de T038 continuam pendentes.
