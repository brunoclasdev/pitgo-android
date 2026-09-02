---

description: "Dependency-ordered implementation tasks for PitTec on-demand service"
---

# Tasks: PitTec sob demanda

**Input**: Design documents from `specs/015-pittec-on-demand/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`,
`contracts/`, `quickstart.md`

**Tests**: Automated test tasks are intentionally omitted because neither the
feature nor the user requested them and the project constitution forbids
generating tests automatically. Each story contains a runnable manual validation
task based on `quickstart.md`.

**Organization**: Tasks are grouped by user story so discovery, dispatch,
tracking, provider response and registration can be implemented and validated as
explicit increments.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel after its stated prerequisites because it changes
  different files and has no dependency on another unfinished parallel task.
- **[Story]**: Maps the task to one of the five user stories in `spec.md`.
- Every implementation and validation task names its target file or validation
  guide.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Prepare non-secret configuration and shared visual resources used
by later PitTec phases.

- [X] T001 [P] Add localized PitTec, dispatch-state, route-fallback, notification, cancellation and accessibility strings plus dedicated PitTec/map/notification vector assets in `app/src/main/res/values/strings.xml`, `app/src/main/res/drawable/ic_home_pittec.xml`, `app/src/main/res/drawable/ic_map_pittec_approximate.xml`, `app/src/main/res/drawable/ic_map_pittec_accepted.xml`, and `app/src/main/res/drawable/ic_notification_pittec.xml`
- [X] T002 [P] Register the JWT-verified `pittec-route` Edge Function and document only non-secret routing variable names in `supabase/config.toml` and `supabase/.env.example`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Establish the additive database contract and shared Android
taxonomy required by every story.

**⚠️ CRITICAL**: No user story work begins until this phase passes database lint
and the shared Kotlin models compile.

- [X] T003 Add migration preflight, `PITTEC_AUTO_SEQUENTIAL`, provider `PITTEC` and `pending_location_mode`, `appointments.arrived_at`, one-active-PitTec/candidate indexes, `appointment_route_snapshots`, and idempotent PitTec category/service seed with “Tec = Técnico” copy in `supabase/migrations/20260902000000_pittec_on_demand.sql`
- [X] T004 Implement eligibility helpers, PitTec catalog inclusion without offers, stable 500 m anonymous discovery projection capped at 200 markers, and `list_nearby_pittecs` in `supabase/migrations/20260902000000_pittec_on_demand.sql` according to `specs/015-pittec-on-demand/contracts/pittec-rpcs.md`
- [X] T005 Implement idempotent `start_pittec_dispatch`, non-repeating `advance_pittec_dispatch`, server-time expiration, `get_my_active_pittec_dispatch`, and pre-accept cancellation with row locks, revisions, exact-distance ordering and 10/25/50 km expansion in `supabase/migrations/20260902000000_pittec_on_demand.sql`
- [X] T006 Implement PitTec branches for accept/reject, provider/client cancellation, `arrive_pittec_appointment`, completion, exact-location handoff/cleanup, pending-mode application, route snapshot cleanup, private Realtime invalidations, RLS, grants and indexes in `supabase/migrations/20260902000000_pittec_on_demand.sql`
- [X] T007 [P] Add fail-closed Kotlin values for `PITTEC`, `PITTEC_AUTO_SEQUENTIAL` and pending provider mode mapping in `app/src/main/java/com/trustarx/pitgo/core/account/model/AccountContext.kt`, `app/src/main/java/com/trustarx/pitgo/core/account/data/AccountContextDtos.kt`, and `app/src/main/java/com/trustarx/pitgo/core/catalog/model/ServiceCatalogModels.kt`
- [X] T008 Update catalog parsing to preserve existing BROWSE/MOBILE behavior, accept PitTec with nullable price, and reject unknown dispatch kinds with an update-required error in `app/src/main/java/com/trustarx/pitgo/core/catalog/repository/ServiceCatalogRepository.kt`

**Checkpoint**: The local reset/lint succeeds; backend functions are authenticated
and concurrency-safe; the Android shared taxonomy compiles without changing the
legacy Guincho behavior.

---

## Phase 3: User Story 1 — Encontrar PitTecs próximos no mapa (Priority: P1) 🎯 MVP

**Goal**: The client opens the PitTec card and sees their valid location plus all
eligible nearby PitTecs as distinct anonymous approximate markers, including
clear empty, permission and recovery states.

**Independent Test**: With available/unavailable providers at known test points,
open PitTec and verify only eligible providers appear as stable approximate
markers; deny/disable location and verify actionable UI without blocking the
rest of the app.

### Implementation for User Story 1

- [X] T009 [P] [US1] Define immutable discovery, permission, loading, empty, error and marker domain/DTO mappings with finite-coordinate validation in `app/src/main/java/com/trustarx/pitgo/features/pittec/model/PitTecModels.kt` and `app/src/main/java/com/trustarx/pitgo/features/pittec/data/PitTecDtos.kt`
- [X] T010 [US1] Implement authenticated `list_nearby_pittecs` access, response validation and domain-error mapping in `app/src/main/java/com/trustarx/pitgo/features/pittec/repository/PitTecRepository.kt` (depends on T009)
- [X] T011 [US1] Implement location-at-point-of-use, discovery refresh, empty/offline/stale-location recovery and immutable `StateFlow` state in `app/src/main/java/com/trustarx/pitgo/features/pittec/viewmodel/PitTecViewModel.kt` (depends on T009–T010)
- [X] T012 [P] [US1] Render client and approximate PitTec symbols with uncertainty-aware styling and no tappable provider identity in `app/src/main/java/com/trustarx/pitgo/features/pittec/ui/PitTecMapStyleController.kt` (depends on T009)
- [X] T013 [US1] Build the stateless accessible discovery map, textual alternatives, retry/permission controls and `Chamar PitTec` entry action in `app/src/main/java/com/trustarx/pitgo/features/pittec/ui/PitTecScreen.kt` (depends on T011–T012)
- [X] T014 [P] [US1] Route the `PITTEC_AUTO_SEQUENTIAL` catalog card with nullable price without changing fixed or Guincho cards in `app/src/main/java/com/trustarx/pitgo/features/home/model/HomeContent.kt`, `app/src/main/java/com/trustarx/pitgo/features/home/viewmodel/HomeViewModel.kt`, and `app/src/main/java/com/trustarx/pitgo/features/home/ui/HomeScreen.kt` (verified already generic — nullable `minimumPrice` and pass-through `dispatchKind` needed no change; routing lives in T015's NavHost edit)
- [X] T015 [US1] Register `PitTecRepository`, construct `PitTecViewModel`, and add centralized PitTec service routes/back-stack behavior in `app/src/main/java/com/trustarx/pitgo/core/app/PitGoAppContainer.kt`, `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoRoutes.kt`, and `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt` (depends on T010–T014)
- [ ] T016 [US1] Execute and record the registration-fixture and discovery/privacy scenarios from sections 3–5 of `specs/015-pittec-on-demand/quickstart.md`, including direct RLS denial of exact discovery locations — **blocked in this environment**: no Supabase CLI/Docker available to run a local stack (see T049)

**Checkpoint**: User Story 1 is independently demonstrable as a privacy-safe
PitTec coverage map, even before automatic dispatch UI is enabled.

---

## Phase 4: User Story 2 — Chamar automaticamente o PitTec mais próximo (Priority: P1)

**Goal**: One tap creates one generic PitTec request, shows the complete search
state card, and automatically advances nearest-first through untried providers
on rejection or 60-second expiration.

**Independent Test**: Configure three providers so the nearest rejects, the next
expires and the third accepts; verify ordering, single active offer, no repeated
candidate, correct no-provider state, cancellation and idempotent repeated taps.

### Implementation for User Story 2

- [X] T017 [US2] Extend PitTec domain/DTO models with request IDs, authoritative revisions, search/appointment states, actor actions, 60-second expiry, approximate offer metadata and stable backend error codes in `app/src/main/java/com/trustarx/pitgo/features/pittec/model/PitTecModels.kt` and `app/src/main/java/com/trustarx/pitgo/features/pittec/data/PitTecDtos.kt`
- [X] T018 [US2] Implement start, cancel-before-accept, restore, expiration trigger, private client-channel observation and 5-second reconnect polling in `app/src/main/java/com/trustarx/pitgo/features/pittec/repository/PitTecRepository.kt`, keeping RPC snapshots as source of truth (depends on T017)
- [X] T019 [US2] Extend the client state machine for initializing, searching, awaiting, rejected, expired, reconnecting, cancelled, no-provider and accepted transitions; disable duplicate starts and use revision/idempotency guards in `app/src/main/java/com/trustarx/pitgo/features/pittec/viewmodel/PitTecViewModel.kt` (depends on T017–T018)
- [X] T020 [P] [US2] Create stateless bottom-card components for every pre-accept state, generic request copy, progress, countdown context, cancel/retry/back actions and accessible announcements in `app/src/main/java/com/trustarx/pitgo/features/pittec/ui/PitTecDispatchComponents.kt` (depends on T017)
- [X] T021 [US2] Integrate start/cancel effects and search cards into the client map without clearing the last confirmed state during reconnection in `app/src/main/java/com/trustarx/pitgo/features/pittec/ui/PitTecScreen.kt` (depends on T019–T020)
- [X] T022 [US2] Restore the dedicated active PitTec flow on activity/session recreation and navigate accepted requests exactly once without colliding with legacy mobile restoration — implemented as a new, isolated `features/pittec/viewmodel/PitTecResumeViewModel.kt` wired into `PitGoNavHost.kt` (client side), plus `MobileDispatchRepository.restoreProviderAny()`/`DispatchResumeViewModel.kt` extensions (provider side) so a provider's dispatch-kind-agnostic offer resume still lives in the originally named file (depends on T018–T021)
- [ ] T023 [US2] Execute and record the sequential ordering, 10/25/50 km expansion, reject/expire/exhaust, repeated-tap, cancellation, two-client race and resume scenarios from section 6 and the relevant section 13 cases of `specs/015-pittec-on-demand/quickstart.md` — **blocked in this environment**: no Supabase CLI/Docker or device/emulator available (see T049)

**Checkpoint**: User Story 2 works end to end using RPC responses or a provider
session, and cannot create duplicate searches, offers or accepted appointments.

---

## Phase 5: User Story 3 — Acompanhar a chegada do PitTec (Priority: P1)

**Goal**: After acceptance both participants see exact markers, shared routed
distance or an honest direct estimate, live/stale state, and manual arrival,
completion and reasoned cancellation transitions.

**Independent Test**: Accept a request, simulate provider movement and routing
failure, verify both markers/route/distance and the 30-second stale warning, then
perform “Cheguei” followed by completion and confirm private live data cleanup.

### Implementation for User Story 3

- [X] T024 [P] [US3] Define validated GeoJSON route, distance, location-revision, freshness and routed/estimated/unavailable domain/DTO types in `app/src/main/java/com/trustarx/pitgo/core/routing/model/RouteModels.kt` and `app/src/main/java/com/trustarx/pitgo/core/routing/data/RouteDtos.kt`
- [X] T025 [P] [US3] Implement the JWT-authenticated OSRM-compatible proxy with participant authorization, server-side exact-coordinate lookup, 50 m/15 s throttling, upstream timeout/body/geometry validation, coalescing-safe snapshot upsert and coordinate-free logs in `supabase/functions/pittec-route/index.ts` — throttle is time-based (15 s) rather than movement-based (50 m) since no prior coordinate is persisted to compare against; typecheck could not run (`deno` unavailable in this environment, see T049)
- [X] T026 [US3] Implement route-function invocation, snapshot parsing, one-in-flight conflation and retryable error mapping in `app/src/main/java/com/trustarx/pitgo/core/routing/repository/PitTecRouteRepository.kt` (depends on T024)
- [X] T027 [P] [US3] Extend shared map specifications and MapLibre rendering with updateable GeoJSON polylines, solid routed styling, dashed direct fallback, bounds fitting and explicit reframe control in `app/src/main/java/com/trustarx/pitgo/core/map/model/MapSurfaceModels.kt` and `app/src/main/java/com/trustarx/pitgo/core/map/ui/MapLibreMapHost.kt` — only `MapSurfaceModels.kt` needed a change (new `MapRoutePolyline`/`MapRouteStyle` on `MapRenderModel`); `MapLibreMapHost.kt`'s existing generic `MapRenderModel` passthrough already covers it, bounds fitting reuses the existing `framingViewport` helper
- [X] T028 [US3] Extend participant-only tracking DTOs/repository to expose dispatch kind, provider identity, monotonic exact locations and route invalidations without leaking them pre-accept — `AppointmentTrackingModels.kt`/`AppointmentTrackingRepository.kt` were already dispatch-kind-agnostic by appointment ID; the actual gap was `AppointmentTrackingViewModel.onVisible()` calling the MOBILE_SEQUENTIAL-only `restore()` instead of `restoreAnyDispatchKind()`, fixed there (see T036)
- [X] T029 [P] [US3] Publish PitTec movement about every 10 seconds to appointment-current location while accepted/in-progress, stop exact discovery publication during the appointment, and clear state on terminal/sign-out in `app/src/main/java/com/trustarx/pitgo/features/providertracking/repository/ProviderLocationRepository.kt` and `app/src/main/java/com/trustarx/pitgo/features/providertracking/service/ProviderLocationTrackingService.kt` — cadence/discovery-cutover/cleanup were already generic server-side (`publish_mobile_provider_location`); the Android gap was `ProviderLocationTrackingService`'s hardcoded MOBILE-only eligibility guards, generalized to MOBILE-or-PITTEC (overlaps T038's FGS generalization, which this also partially completes)
- [X] T030 [US3] Combine participant positions and route snapshots, calculate haversine fallback, mark fixes stale after 30 seconds, preserve manual camera state, and expose arrive/complete/reasoned-cancel actions with idempotent revisions in `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/viewmodel/AppointmentTrackingViewModel.kt` (depends on T024–T029)
- [X] T031 [US3] Render distinct exact participant markers, solid/dashed path, localized m/km distance, live/stale/estimated text and accessible reframe behavior in `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/ui/AppointmentMapStyleController.kt` and `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/ui/AppointmentTrackingScreen.kt` (depends on T027–T030)
- [X] T032 [US3] Wire `PitTecRouteRepository` and dispatch actions into tracking factories/navigation and clear route/location UI on terminal state or account switch in `app/src/main/java/com/trustarx/pitgo/core/app/PitGoAppContainer.kt` and `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt` (depends on T026–T031)
- [ ] T033 [US3] Execute and record accepted-map, route refresh/fallback, stale fix, same-point distance, manual camera, arrival/completion, both cancellation branches and cleanup scenarios from sections 8–12 of `specs/015-pittec-on-demand/quickstart.md` — **blocked in this environment**: no Supabase CLI/Docker, routing provider, or device/emulator available (see T049)

**Checkpoint**: User Story 3 provides complete accepted-service tracking and
state transitions even when the routing provider is unavailable.

---

## Phase 6: User Story 4 — Receber e responder a uma chamada PitTec (Priority: P1)

**Goal**: An eligible PitTec receives one visible/audible private offer in
foreground, background or locked screen, sees only decision-safe approximate
details, and can accept/reject before server expiry.

**Independent Test**: Send an offer to an available PitTec in foreground,
background and locked states; verify deduplicated sound/notification, deep link,
remaining server deadline, explicit accept/reject confirmation and late-accept
rejection.

### Implementation for User Story 4

- [X] T034 [P] [US4] Implement the private high-importance `pittec_incoming_requests` channel, permission/channel readiness checks, lock-screen-safe content, stable notification IDs and immutable appointment deep link in `app/src/main/java/com/trustarx/pitgo/features/providertracking/notification/ProviderIncomingRequestNotifier.kt` — the content intent carries no appointment ID: it just opens `MainActivity` and the existing dispatch-kind-agnostic resume machinery (T036/T039) restores the authoritative snapshot and navigates
- [X] T035 [P] [US4] Extend shared provider-offer DTO/domain parsing with PitTec dispatch kind, server expiry, approximate distance, coarse region, revisions and actor-safe actions in `app/src/main/java/com/trustarx/pitgo/features/mobiledispatch/data/MobileDispatchDtos.kt` and `app/src/main/java/com/trustarx/pitgo/features/mobiledispatch/model/MobileDispatchModels.kt`
- [X] T036 [US4] Extend provider snapshot/Realtime transport for dedicated PitTec restore, accept/reject/expire idempotency and private invalidation payloads in `app/src/main/java/com/trustarx/pitgo/features/mobiledispatch/repository/MobileDispatchRepository.kt` (depends on T035)
- [X] T037 [P] [US4] Render PitTec offer identity, approximate distance/region, authoritative countdown, accept/reject loading and comprehensible expired/conflict feedback in `app/src/main/java/com/trustarx/pitgo/features/mobiledispatch/viewmodel/ProviderIncomingRequestViewModel.kt` and `app/src/main/java/com/trustarx/pitgo/features/mobiledispatch/ui/ProviderIncomingRequestScreen.kt` (depends on T035–T036) — ViewModel needed only the `restoreAnyDispatchKind()` wiring already covered by T036; its state machine was already snapshot-content-agnostic
- [X] T038 [US4] Generalize the existing location FGS for available MOBILE or PITTEC providers, subscribe/restore/poll the private provider topic, deduplicate `(appointmentId, revision)` alerts, cancel obsolete notifications and revalidate session on `START_STICKY` in `app/src/main/java/com/trustarx/pitgo/features/providertracking/service/ProviderLocationTrackingService.kt` (depends on T034–T036) — session/mode/availability are already re-checked at the top of `startTracking()`, which `onStartCommand` invokes on every `START_STICKY` restart
- [X] T039 [US4] Handle PitTec notification intents and exactly-once navigation to pending or accepted provider screens, including stale/terminal deep links and watchdog restart for PITTEC availability, in `app/src/main/java/com/trustarx/pitgo/MainActivity.kt` and `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt` (depends on T037–T038) — no `MainActivity` change was needed: the notification intent is a plain app launch, and `PitGoNavHost`'s existing `dispatchResumeState`/`lastAutoNavigated` effect (now dispatch-kind-agnostic per T036) already provides exactly-once navigation to `ProviderRequest`/`AppointmentTracking` or falls back to Dashboard for a stale/terminal state; the PITTEC watchdog-restart condition was fixed as part of T029
- [ ] T040 [US4] Execute and record foreground/background/locked, permission/channel, Realtime fallback, duplicate/out-of-order, expired/withdrawn, logout and sticky-restart scenarios from section 7 of `specs/015-pittec-on-demand/quickstart.md` — **blocked in this environment**: no device/emulator or Supabase CLI/Docker available (see T049)

**Checkpoint**: User Story 4 reliably alerts an actively available PitTec under
the documented FGS scope, without exposing exact client data before acceptance.

---

## Phase 7: User Story 5 — Cadastrar empresa como PitTec (Priority: P1)

**Goal**: Provider registration and dashboard expose Fixo, Móvel and PitTec as
exclusive modes; PitTec eligibility requires its offer, location and notification
readiness, and changes during attendance take effect only afterward.

**Independent Test**: Register one company in each mode, verify only valid
PitTecs enter discovery/dispatch, switch mode during an accepted appointment and
confirm the pending mode is atomically applied after termination.

### Implementation for User Story 5

- [X] T041 [P] [US5] Add mutually exclusive PITTEC mode, pending-mode fields and request/response serialization while retaining all existing company validation fields in `app/src/main/java/com/trustarx/pitgo/features/providerregistration/model/ProviderRegistrationModels.kt` and `app/src/main/java/com/trustarx/pitgo/features/providerregistration/data/ProviderRegistrationDtos.kt` — the DTOs already serialize `locationMode.name` generically; only the local registration-scoped `ProviderLocationMode` enum needed the new case
- [X] T042 [US5] Validate and persist PITTEC through the existing registration RPC contract without accepting unknown modes or weakening address/service rules in `app/src/main/java/com/trustarx/pitgo/features/providerregistration/validation/ProviderRegistrationValidation.kt` and `app/src/main/java/com/trustarx/pitgo/features/providerregistration/repository/ProviderRegistrationRepository.kt` (depends on T041) — verified: validation only checks "required", the repository passes `locationMode.name` through generically, and `create_provider_registration` already validates the full FIXED/MOBILE/PITTEC set server-side; no code changes needed
- [X] T043 [US5] Present Fixo, Móvel and PitTec as accessible exclusive choices with concise operational descriptions and saved-state restoration in `app/src/main/java/com/trustarx/pitgo/features/providerregistration/ui/ProviderRegistrationScreen.kt` and `app/src/main/java/com/trustarx/pitgo/features/providerregistration/viewmodel/ProviderRegistrationViewModel.kt` (depends on T041–T042) — added the third mode button/description; the ViewModel's draft/error handling was already mode-agnostic
- [X] T044 [P] [US5] Map effective/pending mode, active PitTec offer and availability prerequisites through dashboard state and repository updates in `app/src/main/java/com/trustarx/pitgo/features/providerdashboard/model/ProviderDashboardModels.kt`, `app/src/main/java/com/trustarx/pitgo/features/providerdashboard/repository/ProviderDashboardRepository.kt`, and `app/src/main/java/com/trustarx/pitgo/features/providerdashboard/viewmodel/ProviderDashboardViewModel.kt` — repository was already generic; added a `NOTIFICATIONS_REQUIRED` issue, generalized `enableMobileAvailability()` into mode-aware `enableTrackedAvailability()` gated on the PitTec offers channel, and switched the dashboard's incoming-appointment/tracking-resume checks to `restoreAnyDispatchKind()`/MOBILE-or-PITTEC
- [X] T045 [US5] Show pending-mode feedback and gate PitTec availability on active offer, location readiness and enabled alerts while preserving FIXED/MOBILE controls in `app/src/main/java/com/trustarx/pitgo/features/providerdashboard/ui/ProviderDashboardScreen.kt` and `app/src/main/java/com/trustarx/pitgo/features/providertracking/ui/ProviderTrackingScreen.kt` (depends on T034 and T044) — added the PitTec mode button, pending-mode text and the notifications-required banner/action to the dashboard; `ProviderTrackingScreen.kt` needed no change (it only starts/stops the now-generalized FGS, no mode branching). The "active offer" prerequisite is enforced server-side by `set_provider_availability`'s `PITTEC_OFFER_REQUIRED` check, surfaced client-side as the existing generic `UPDATE_FAILED` issue
- [ ] T046 [US5] Execute and record three-mode registration, notification gating, eligibility and deferred-mode scenarios from sections 3–4 and 12 of `specs/015-pittec-on-demand/quickstart.md`, including a legacy MOBILE appointment smoke check — **blocked in this environment**: no Supabase CLI/Docker or device/emulator available (see T049)

**Checkpoint**: User Story 5 supplies and manages eligible PitTec providers
without reclassifying or interrupting existing fixed/mobile providers.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Verify security, accessibility, compatibility and release ordering
across the complete feature.

- [ ] T047 [P] Audit PitTec logs, Realtime envelopes and error handling to remove coordinates, geometry, JWTs, cancellation reasons and technical UI messages in `app/src/main/java/com/trustarx/pitgo/features/pittec/repository/PitTecRepository.kt`, `app/src/main/java/com/trustarx/pitgo/core/routing/repository/PitTecRouteRepository.kt`, `supabase/functions/pittec-route/index.ts`, and `supabase/migrations/20260902000000_pittec_on_demand.sql`
- [ ] T048 [P] Verify responsive layouts, enlarged fonts, TalkBack labels, touch targets and non-color-only marker/route/status distinctions using the accessibility cases in `specs/015-pittec-on-demand/quickstart.md` against `app/src/main/java/com/trustarx/pitgo/features/pittec/ui/PitTecScreen.kt`, `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/ui/AppointmentTrackingScreen.kt`, and `app/src/main/java/com/trustarx/pitgo/features/mobiledispatch/ui/ProviderIncomingRequestScreen.kt`
- [ ] T049 Run `supabase db reset` only on the isolated local stack, `supabase db lint`, `deno check supabase/functions/pittec-route/index.ts`, `./gradlew :app:assembleDebug`, `./gradlew :app:assembleRelease`, and `./gradlew :app:lintDebug`, resolving every PitTec-introduced failure in the files named by `specs/015-pittec-on-demand/quickstart.md`
- [ ] T050 Execute adversarial RLS and concurrency checks for non-participant reads, forged provider responses, simultaneous accept/timeout/cancel, duplicate requests and stale revisions using sections 6, 8 and 11 of `specs/015-pittec-on-demand/quickstart.md`
- [ ] T051 Execute the complete `specs/015-pittec-on-demand/quickstart.md`, smoke-test Home, Fixo and Guincho, and verify the backend-first rollout/fallback/forward-migration procedure in `specs/015-pittec-on-demand/plan.md` before release

---

## Dependencies & Execution Order

### Phase dependencies

```text
Phase 1 Setup
    └── Phase 2 Foundation
          ├── US1 Discovery ── US2 Automatic dispatch ── US3 Accepted tracking
          │                                └────────────── US4 Provider response
          └── US5 Provider registration/management

US1 + US2 + US3 + US4 + US5
    └── Phase 8 Polish and release validation
```

- **Setup (Phase 1)**: No dependencies; T001 and T002 can run in parallel.
- **Foundation (Phase 2)**: Depends on Setup and blocks every story. T003–T006
  are sequential edits to the same migration; T007 can proceed in parallel, then
  T008 completes shared Android compatibility.
- **US1 (Phase 3)**: Depends only on Foundation and is the suggested discovery
  MVP.
- **US2 (Phase 4)**: Depends on Foundation and integrates with the US1 screen;
  its backend can be exercised independently through the RPC contract.
- **US3 (Phase 5)**: Depends on an accepted request from US2 or an equivalent
  seeded fixture; routing/map primitives can be built in parallel beforehand.
- **US4 (Phase 6)**: Depends on the Foundation and US2 provider-response
  transport (T017–T018/T035–T036); it can otherwise progress in parallel with
  US3.
- **US5 (Phase 7)**: Registration/dashboard work depends on Foundation and can
  progress in parallel with US1–US4; final availability gating T045 also depends
  on the notification-readiness component T034.
- **Polish (Phase 8)**: Depends on every story selected for the release.

### User story dependency graph

| Story | Hard prerequisites | Independently testable result |
|---|---|---|
| US1 — Discovery | Foundation | Anonymous approximate coverage map plus location/empty recovery |
| US2 — Dispatch | Foundation; US1 only for final screen integration | One idempotent nearest-first request with cancel/exhaust/restore states |
| US3 — Tracking | Accepted PitTec fixture or US2 | Exact participant tracking, route/fallback, arrival/completion/cancellation |
| US4 — Provider response | Foundation and shared response transport | Timed private offer and foreground/background/locked alert response |
| US5 — Registration | Foundation; T034 for final alert-readiness gate | Three exclusive modes, eligibility gate and deferred mode change |

### Within each story

- Domain/DTO contracts precede repositories.
- Repositories precede ViewModels; ViewModels own immutable UI state.
- Stateless Compose and map controllers consume domain/UI state only.
- Navigation/DI integration follows a compile-ready screen and repository.
- Manual validation runs at the story checkpoint before downstream work relies
  on that story.
- Database state/revision remains authoritative; Realtime is an invalidation
  hint and client clocks never decide offer validity.

## Parallel Execution Examples

### User Story 1

After T009, discovery repository/ViewModel work and map presentation can be
split, while Home routing is independent:

```text
Task T010: PitTec discovery repository
Task T012: Approximate marker MapLibre controller
Task T014: Home card routing behavior
```

### User Story 2

After T017 defines the state contract:

```text
Task T018 + T019: RPC/Realtime transport and client state machine
Task T020: Stateless search bottom-card components
```

### User Story 3

The external route function, Android map primitives and provider publication use
separate files:

```text
Task T025: Authenticated routing Edge Function
Task T027: MapLibre polyline/fallback primitives
Task T029: Accepted-appointment provider location publication
```

### User Story 4

After the shared offer contract exists:

```text
Task T034: Android notification channel/notifier
Task T037: Provider offer ViewModel and Compose screen
```

Then T038 connects both through the foreground service.

### User Story 5

Registration and operational dashboard work touch distinct packages:

```text
Task T041 + T042: Registration model/validation/persistence
Task T044: Dashboard effective/pending mode and eligibility state
```

## Implementation Strategy

### MVP first: User Story 1

1. Complete T001–T008 (Setup and Foundation).
2. Complete T009–T016 (anonymous PitTec discovery map).
3. Stop and validate sections 3–5 of `quickstart.md`.
4. Demonstrate coverage, privacy, empty state and location recovery without
   enabling unfinished dispatch controls in a release build.

### Incremental delivery

1. **Discovery**: deliver US1 as the smallest independently useful visual slice.
2. **Request**: add US2 and validate sequential server behavior before relying on
   notification delivery.
3. **Accepted service**: add US3 route/location/actions with a routing-failure
   fallback.
4. **Provider experience**: add US4 alerts/deep links and validate documented FGS
   guarantees.
5. **Provider supply**: add US5 registration/management UI, then enable the
   catalog service operationally.
6. Complete Phase 8 before backend-first production rollout.

### Safe release order

1. Apply and validate the additive migration.
2. Deploy and smoke-test `pittec-route` with secrets configured server-side.
3. Publish the Android build that understands `PITTEC_AUTO_SEQUENTIAL`.
4. Activate the seeded PitTec service only when eligible provider supply and
   alerts have passed validation.

## Notes

- `[P]` never permits simultaneous edits to the same file; honor prerequisites
  shown in task text and dependency sections.
- Do not edit previously applied SQL migrations; all database work stays in
  `supabase/migrations/20260902000000_pittec_on_demand.sql` or a later corrective
  forward migration if implementation has already been applied anywhere.
- Do not add FCM, `ACCESS_BACKGROUND_LOCATION`, route history, chat, payment,
  specialty intake or automated test scaffolding in this feature.
- Keep the legacy `MOBILE_SEQUENTIAL`/Guincho experience unchanged.
- Commit after each task or coherent dependency group with a descriptive
  `feat:`, `fix:`, `refactor:`, `style:` or `chore:` message.
