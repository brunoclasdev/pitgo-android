---

description: "Dependency-ordered implementation tasks for the mocked PitTec UI experience"
---

# Tasks: Experiência mockada de UI do PitTec

**Input**: Design documents from `specs/017-pittec-mocked-ui/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`,
`quickstart.md`

**Tests**: Automated test tasks are intentionally omitted because neither the feature nor the user
requested them and the project constitution forbids generating tests automatically. Each user
story ends with manual validation against `quickstart.md`; build and lint run in Setup/Foundation
and again in the final phase.

**Organization**: Tasks are grouped by the five user stories in `spec.md`. Shared mock models,
participant-map UI and real-flow isolation are foundational because they are required by both
profiles and multiple stories.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: May run in parallel after its stated prerequisites because it changes different files
  and does not depend on another unfinished parallel task.
- **[Story]**: Maps the task to US1–US5 from `spec.md`; Setup, Foundational and Polish tasks do not
  carry a story label.
- Every task names the exact implementation file or validation guide it affects.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Establish a verified baseline and add the shared localized resources required by the
mocked experience without changing dependencies or backend artifacts.

- [X] T001 Run the pre-change `./gradlew :app:assembleDebug :app:lintDebug` baseline using `app/build.gradle.kts` and resolve any existing failure before feature edits
- [X] T002 Add all localized provider/client mock states, price breakdowns, location recovery, search feedback, confirmation, stale-position, tab and accessibility strings in `app/src/main/res/values/strings.xml`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Define the local state contract, reusable participant map and isolation from the real
PitTec infrastructure before implementing either profile.

**⚠️ CRITICAL**: No user story work begins until the foundation compiles and no mock action can
reach the real dispatch/tracking stack.

- [X] T003 Define `MockParticipant`, `DistancePerimeter`, cent-based `ServiceEstimate`, `MockLocationSnapshot`, candidate, search, request, confirmation and appointment types with validation/derived fields in `app/src/main/java/com/trustarx/pitgo/features/pittec/model/PitTecMockModels.kt`
- [X] T004 Add deterministic `progressive-success`, `no-provider` and `provider-incoming` fixtures plus mutually exclusive client/provider UI stages and idempotency revisions in `app/src/main/java/com/trustarx/pitgo/features/pittec/model/PitTecMockModels.kt` (depends on T003)
- [X] T005 [P] Move the reusable exact-participant MapLibre styling from `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/ui/AppointmentMapStyleController.kt` to `app/src/main/java/com/trustarx/pitgo/core/map/ui/ParticipantMapStyleController.kt` and update its use in `app/src/main/java/com/trustarx/pitgo/features/appointmenttracking/ui/AppointmentTrackingScreen.kt` without visual behavior changes
- [X] T006 Create the stateless two-participant map/summary surface with initial `framingViewport`, revisioned recenter callback, line-distance estimate, map failure fallback and textual equivalents in `app/src/main/java/com/trustarx/pitgo/features/pittec/ui/PitTecMockTrackingContent.kt` (depends on T003–T005)
- [X] T007 [P] Add an `ACTION_STOP_LOCAL` branch that cancels an existing PitTec tracking job and local alert without restoring dispatch or updating remote availability in `app/src/main/java/com/trustarx/pitgo/features/providertracking/service/ProviderLocationTrackingService.kt`
- [X] T008 Expose an idempotent `stopLocally()` command for the local-only service action in `app/src/main/java/com/trustarx/pitgo/features/providertracking/repository/ProviderTrackingStateRepository.kt` (depends on T007)
- [X] T009 [P] Remove construction/collection of `PitTecResumeViewModel`, remove its automatic redirect effect and restrict the global tracking watchdog to MOBILE while retaining all non-PitTec navigation behavior in `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt`
- [X] T010 Run `./gradlew :app:assembleDebug` after T003–T009 and fix foundation compilation issues only in the files named by those tasks, using `app/build.gradle.kts` as the module entry point

**Checkpoint**: Shared models/map compile, the existing appointment map looks unchanged, and the
mock flow has no active wiring to PitTec restore, Realtime, tracking publication or notifications.

---

## Phase 3: User Story 1 — Receber e responder a uma chamada na Home PitTec (Priority: P1) 🎯 MVP

**Goal**: A PitTec provider opens a location-gated map, sees a simulated incoming request with
three purple pulses or a static reduced-motion presentation, and can accept or decline exactly
once.

**Independent Test**: Open the PITTEC provider Home with location enabled, trigger the fixture and
verify the real device marker, textual available state, persistent request card, Accept/Decline
idempotency and accepted map; repeat with permission/GPS unavailable and animations disabled.

### Implementation for User Story 1

- [X] T011 [US1] Implement `PitTecProviderMockViewModel` with immutable `StateFlow`, existing `DeviceLocationAvailability` mapping, precise-fix acquisition, foreground-only device updates, available/incoming states, one-shot fixture delivery and revision-guarded accept/decline in `app/src/main/java/com/trustarx/pitgo/features/pittec/viewmodel/PitTecProviderMockViewModel.kt`
- [X] T012 [P] [US1] Create the persistent incoming-request card and exactly-three-cycle purple pulse overlay, consume its request revision once and branch to static rendering when `MotionDurationScale.scaleFactor == 0f` in `app/src/main/java/com/trustarx/pitgo/features/pittec/ui/PitTecMockComponents.kt` (depends on T004)
- [X] T013 [US1] Build `PitTecProviderDashboardRoute` permission launchers, app/location settings actions, `ON_START` revalidation and blocked-map/locating/unavailable/ready states in `app/src/main/java/com/trustarx/pitgo/features/pittec/ui/PitTecProviderDashboardScreen.kt` (depends on T011)
- [X] T014 [US1] Integrate available map, simulated-request trigger, pulse/card actions and initial accepted two-participant map into `app/src/main/java/com/trustarx/pitgo/features/pittec/ui/PitTecProviderDashboardScreen.kt` using `PitTecMockTrackingContent` (depends on T006, T011–T013)
- [X] T015 [US1] Branch `ProviderDashboard` by effective `ProviderLocationMode.PITTEC`, create a session-keyed root-owner `PitTecProviderMockViewModel`, invoke `stopLocally()` once and retain the existing dashboard factory for FIXED/MOBILE in `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt` (depends on T008, T011–T014)
- [ ] T016 [US1] Execute provider location-gate and alert/response scenarios from sections 7–9 of `specs/017-pittec-mocked-ui/quickstart.md`, including repeated taps, navigation replay and reduced-motion behavior

**Checkpoint**: US1 is independently demonstrable without a client session and constitutes the
suggested MVP.

---

## Phase 4: User Story 2 — Conferir e confirmar a estimativa antes da busca (Priority: P1)

**Goal**: The client keeps the current initial PitTec presentation, sees a 5 km price estimate
before confirmation, then follows a single deterministic search through 5/10/15 km, rejection and
empty-result states without calling the backend.

**Independent Test**: Open the existing client PitTec screen, verify it is unchanged before
“Buscar PitTec”, validate R$ 60/70/80 calculations, cancel before confirmation, repeatedly confirm,
and observe non-repeating candidates plus automatic 5 km expansion and no-provider recovery.

### Implementation for User Story 2

- [X] T017 [US2] Implement `PitTecClientMockViewModel` with local discovery markers, estimate/cancel/confirm stages, cent-based 5/10/15 km pricing, deterministic sequential candidate responses, same-search expansion, rejected-ID exclusion, no-provider retry and operation/revision guards in `app/src/main/java/com/trustarx/pitgo/features/pittec/viewmodel/PitTecClientMockViewModel.kt`
- [X] T018 [US2] Add stateless estimating, estimate breakdown, searching, awaiting-response, rejected-continuing and no-provider cards with pt-BR currency output and live-region feedback in `app/src/main/java/com/trustarx/pitgo/features/pittec/ui/PitTecMockComponents.kt` (depends on T012, T017)
- [X] T019 [US2] Preserve the current top bar/map/approximate-marker layout until “Buscar PitTec” and replace the real dispatch overlay with mock estimate/search states and callbacks in `app/src/main/java/com/trustarx/pitgo/features/pittec/ui/PitTecScreen.kt` (depends on T017–T018)
- [X] T020 [US2] Replace the client PitTec route factory with a session-keyed root-owner `PitTecClientMockViewModel`, remove `PitTecRepository`/connectivity dependencies and keep mock acceptance inside the PitTec route in `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt` (depends on T009, T017–T019)
- [ ] T021 [US2] Execute client initial-screen, estimate, confirmation, progressive-search and empty-result scenarios from sections 3–4 of `specs/017-pittec-mocked-ui/quickstart.md`, verifying only one local search is created per confirmation

**Checkpoint**: US2 works independently from the provider profile and never invokes the real
discovery/dispatch/resume code.

---

## Phase 5: User Story 3 — Acompanhar o encontro no mapa (Priority: P1)

**Goal**: Either profile can open an accepted fixture with both participants framed, distance,
total and state, then observe lifecycle-aware 30-second PitTec movement, a 35-second stale warning
and explicit recentering without camera theft.

**Independent Test**: Start directly from an accepted fixture in each profile, pan the map, observe
two position updates, force a gap beyond 35 seconds and verify markers/distance update while the
manual viewport remains until “Centralizar”.

### Implementation for User Story 3

- [X] T022 [P] [US3] Add initial accepted appointment creation, one lifecycle-aware movement job, 30-second fixture steps, monotonic 35-second freshness ticker, distance recomputation, pause/resume and recenter commands in `app/src/main/java/com/trustarx/pitgo/features/pittec/viewmodel/PitTecClientMockViewModel.kt` (depends on T017)
- [X] T023 [P] [US3] Add the equivalent accepted appointment, 30/35-second job ownership, device-origin movement path, pause/resume, stale state and recenter commands in `app/src/main/java/com/trustarx/pitgo/features/pittec/viewmodel/PitTecProviderMockViewModel.kt` (depends on T011)
- [X] T024 [US3] Complete participant markers, dashed direct line, localized remaining distance including “menos de 100 m”, total, status, timestamp/stale warning and explicit “Centralizar” rendering in `app/src/main/java/com/trustarx/pitgo/features/pittec/ui/PitTecMockTrackingContent.kt` (depends on T006, T022–T023)
- [X] T025 [P] [US3] Connect `LifecycleStartEffect` visibility, viewport-idle storage, surface retry and recenter events for accepted client tracking in `app/src/main/java/com/trustarx/pitgo/features/pittec/ui/PitTecScreen.kt` (depends on T022, T024)
- [X] T026 [P] [US3] Connect the same lifecycle, viewport, surface and recenter events for accepted provider tracking in `app/src/main/java/com/trustarx/pitgo/features/pittec/ui/PitTecProviderDashboardScreen.kt` (depends on T023–T024)
- [ ] T027 [US3] Execute accepted-map, two-tick, manual-camera, recenter, same-point and stale-position scenarios from section 5 of `specs/017-pittec-mocked-ui/quickstart.md` for both independent profiles

**Checkpoint**: US3 is independently testable from a direct accepted fixture and integrates with
either US1 acceptance or US2 search acceptance when those stories are present.

---

## Phase 6: User Story 4 — Confirmar encontro e conclusão pelos dois participantes (Priority: P1)

**Goal**: Each independent profile records its local meeting/completion action once, visibly waits
for the other party, applies one simulated response after 3 seconds and stops movement on
completion.

**Independent Test**: Start an accepted fixture in either profile, repeat both actions, navigate
away during each delay and verify no immediate unilateral transition, no duplicate jobs, one
3-second counterpart response and one terminal completion.

### Implementation for User Story 4

- [X] T028 [P] [US4] Implement revision-guarded local meeting/completion confirmations, one 3-second counterpart job per stage, timestamps, valid transitions and movement cancellation on completion in `app/src/main/java/com/trustarx/pitgo/features/pittec/viewmodel/PitTecClientMockViewModel.kt` (depends on T022)
- [X] T029 [P] [US4] Implement the same independent confirmation state machine and idempotent counterpart jobs for the provider flow in `app/src/main/java/com/trustarx/pitgo/features/pittec/viewmodel/PitTecProviderMockViewModel.kt` (depends on T023)
- [X] T030 [US4] Render “Já nos encontramos”, “Finalizar serviço”, disabled/pending states, the missing-party explanation, transition announcements and completed summary/actions in `app/src/main/java/com/trustarx/pitgo/features/pittec/ui/PitTecMockTrackingContent.kt` (depends on T024, T028–T029)
- [X] T031 [P] [US4] Wire client confirmation events and “Voltar para a Home” completion navigation without resetting the host-scoped state prematurely in `app/src/main/java/com/trustarx/pitgo/features/pittec/ui/PitTecScreen.kt` and `app/src/main/java/com/trustarx/pitgo/core/navigation/PitGoNavHost.kt` (depends on T028, T030)
- [X] T032 [P] [US4] Wire provider confirmation events and “Voltar ao mapa” so the last appointment remains available in memory while the stage returns to available in `app/src/main/java/com/trustarx/pitgo/features/pittec/ui/PitTecProviderDashboardScreen.kt` (depends on T029–T030)
- [ ] T033 [US4] Execute both profiles' bilateral meeting/completion, repeated-action, navigate-away and terminal-stop scenarios from section 6 of `specs/017-pittec-mocked-ui/quickstart.md`

**Checkpoint**: US4 proves every meeting/completion transition requires a local action plus the
single delayed simulated counterpart and cannot duplicate on recomposition.

---

## Phase 7: User Story 5 — Navegar pelas áreas do PitTec (Priority: P2)

**Goal**: The provider shell uses the standard bottom navigation with Mapa, Dados da corrida,
Valores and Perfil, and the two informational tabs show the same current/last local appointment or
an explanatory empty state.

**Independent Test**: Navigate all four tabs before and after an accepted/completed fixture,
verify text plus indicator selection, matching appointment data/value breakdown and absence of
history, balance or earnings.

### Implementation for User Story 5

- [X] T034 [P] [US5] Add purpose-specific vector icons for Mapa, Dados da corrida and Valores in `app/src/main/res/drawable/ic_nav_map.xml`, `app/src/main/res/drawable/ic_nav_ride_data.xml`, and `app/src/main/res/drawable/ic_nav_values.xml`
- [X] T035 [US5] Extend `BottomItemId`, define ordered `PitTecBottomNavigationItems` and retain the existing client item list/visual behavior in `app/src/main/java/com/trustarx/pitgo/core/ui/PitGoBottomNavigation.kt` (depends on T002, T034)
- [X] T036 [P] [US5] Add idempotent selected-tab events and a single `currentOrLastAppointment` projection shared by Mapa, Dados da corrida and Valores in `app/src/main/java/com/trustarx/pitgo/features/pittec/viewmodel/PitTecProviderMockViewModel.kt` (depends on T011)
- [X] T037 [US5] Wrap the provider experience in one Material `Scaffold` with `PitTecBottomNavigationItems` and implement scrollable Mapa, Dados da corrida, Valores and basic provider Perfil tabs with empty/current-last states and logout in `app/src/main/java/com/trustarx/pitgo/features/pittec/ui/PitTecProviderDashboardScreen.kt` (depends on T014, T035–T036)
- [ ] T038 [US5] Execute bottom-navigation, empty-state, current/last appointment, matching value and provider-profile scenarios from section 10 of `specs/017-pittec-mocked-ui/quickstart.md`

**Checkpoint**: All provider main areas use the app's standard navigation and expose only the
session-scoped information requested by US5.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Verify state retention, accessibility, isolation and non-regression across the full
prototype.

- [X] T039 [P] Audit and correct heading/live-region semantics, content descriptions, minimum touch targets, scroll behavior, 200% font scale and light/dark contrast in `app/src/main/java/com/trustarx/pitgo/features/pittec/ui/PitTecScreen.kt`, `app/src/main/java/com/trustarx/pitgo/features/pittec/ui/PitTecMockComponents.kt`, `app/src/main/java/com/trustarx/pitgo/features/pittec/ui/PitTecMockTrackingContent.kt`, and `app/src/main/java/com/trustarx/pitgo/features/pittec/ui/PitTecProviderDashboardScreen.kt`
- [ ] T040 Verify session-key isolation, navigation/configuration retention, process-death reset and absence of `SavedStateHandle`/DataStore/Room in the mock implementation using section 11 of `specs/017-pittec-mocked-ui/quickstart.md`
- [ ] T041 Verify with Network Inspector and service state that every mock event avoids PitTec RPC, Realtime, provider publication, route, system notification and payment effects while existing auth/catalog/map traffic remains allowed, following section 12 of `specs/017-pittec-mocked-ui/quickstart.md`
- [X] T042 Run `./gradlew :app:assembleDebug :app:lintDebug`, resolve all introduced failures in files listed by `specs/017-pittec-mocked-ui/plan.md`, then complete FIXED/MOBILE, client Home/Profile, non-PitTec and Android-back smoke checks from section 13 of `specs/017-pittec-mocked-ui/quickstart.md`

---

## Dependencies & Execution Order

### Phase dependencies

```text
Phase 1 Setup
    └── Phase 2 Foundation
          ├── US1 Provider Home/Alert ───────────────┐
          └── US2 Client Estimate/Search ────────────┤
                                                     └── US3 Tracking
                                                           └── US4 Confirmations
          US1 Provider Shell ──────────────────────────────└── US5 Bottom Navigation

US1 + US2 + US3 + US4 + US5
    └── Phase 8 Polish and full validation
```

- **Setup (Phase 1)**: T001 establishes the baseline; T002 supplies all shared copy.
- **Foundation (Phase 2)**: Depends on Setup and blocks every story. T005, T007 and T009 can begin
  in parallel with T003; T004 follows T003, T006 follows T003–T005, T008 follows T007, and T010
  closes the phase.
- **US1 (Phase 3)**: Depends on Foundation and is the suggested MVP.
- **US2 (Phase 4)**: Depends on Foundation and is independent from the provider profile.
- **US3 (Phase 5)**: Depends on the shared foundation; T022 integrates US2 and T023 integrates US1,
  while a direct accepted fixture keeps the story independently testable.
- **US4 (Phase 6)**: Depends on US3's accepted tracking state and timer ownership.
- **US5 (Phase 7)**: Depends on the provider shell from US1; it can otherwise proceed in parallel
  with US2–US4 once that shell is ready.
- **Polish (Phase 8)**: Depends on every story selected for the delivered prototype.

### User story dependency graph

| Story | Hard prerequisites | Independently testable result |
|---|---|---|
| US1 — Provider Home/alert | Foundation | Real-location map gate, simulated alert and one accept/decline response |
| US2 — Client estimate/search | Foundation | Preserved initial UI, explicit estimate/confirmation and deterministic local search |
| US3 — Accepted tracking | Foundation plus accepted fixture; integrates US1/US2 | Dual-marker tracking with distance, 30 s movement, 35 s stale and recenter |
| US4 — Bilateral confirmation | US3 | Meeting and completion each require local plus delayed simulated confirmation |
| US5 — Provider navigation | US1 provider shell | Four standard tabs with empty/current-last data and matching values |

### Within each story

- Immutable models/state precede ViewModel transitions.
- ViewModel transitions and lifecycle-safe jobs precede Compose integration.
- Stateless shared components receive state and callbacks only.
- NavHost wiring follows compile-ready ViewModels and screens.
- Manual validation runs at the story checkpoint before downstream work relies on it.
- `[P]` never authorizes simultaneous edits to the same file.

## Parallel Execution Examples

### User Story 1

After Foundation, provider state and visual alert work use different files:

```text
Task T011: Provider mock ViewModel and location/request states
Task T012: Incoming request card and reduced-motion pulse
```

T013–T015 then integrate them in order.

### User Story 2

After T017 defines the client state machine, UI cards and route wiring remain ordered, but manual
review of the preserved initial screen can proceed alongside ViewModel work:

```text
Task T017: Client estimate/search state machine
Review target: app/src/main/java/com/trustarx/pitgo/features/pittec/ui/PitTecScreen.kt
```

### User Story 3

Client and provider movement/freshness logic are independent:

```text
Task T022: Client 30/35-second tracking logic
Task T023: Provider 30/35-second tracking logic
```

After T024, T025 and T026 can also run in parallel because they touch separate screens.

### User Story 4

The independent profile confirmation machines can be implemented together:

```text
Task T028: Client confirmation jobs
Task T029: Provider confirmation jobs
```

After shared UI T030, client navigation T031 and provider return behavior T032 can run in parallel.

### User Story 5

Icons and provider tab-state logic use independent files:

```text
Task T034: Bottom-navigation vector assets
Task T036: Provider selected-tab/current-last state
```

T035 may follow T034 while T036 proceeds independently; T037 integrates both.

## Implementation Strategy

### MVP first: User Story 1

1. Complete T001–T010 (Setup and Foundation).
2. Complete T011–T016 (provider Home, real-location gate and mock alert response).
3. Stop and validate US1 independently with location allowed/blocked and animation enabled/disabled.
4. Demonstrate that Accept/Decline are local and idempotent before implementing the client flow.

### Incremental delivery

1. **Foundation**: isolate real PitTec effects and establish shared local state/map.
2. **Provider MVP**: deliver US1 and validate the core alert decision loop.
3. **Client consent/search**: add US2 without coupling the two profiles.
4. **Tracking**: add US3 once each profile can create an accepted fixture.
5. **Lifecycle**: add US4 bilateral meeting/completion on the accepted state.
6. **Provider information architecture**: add US5 tabs and current/last projections.
7. Complete Phase 8 before sharing the prototype build.

## Notes

- Do not add or edit Supabase migrations, functions, RPCs, policies or secrets for this feature.
- Do not instantiate the real PitTec repository/resume/tracking/notification stack from mock routes.
- Existing authentication, catalog and managed map traffic remain outside the mock boundary.
- Do not add Repository, Use Case, DataStore, Room, WorkManager, FCM, background-location
  permission or automated-test scaffolding.
- Use cent-based money and `SystemClock.elapsedRealtime()` for freshness; do not use floating-point
  currency or wall-clock age.
- Keep FIXED/MOBILE provider behavior and the current client Home/card appearance unchanged.
- Commit after each task or coherent dependency group with a descriptive `feat:`, `fix:`,
  `refactor:`, `style:` or `chore:` message.
