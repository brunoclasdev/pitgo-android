# Implementation Plan: Sessão autenticada e cadastro de veículos

**Branch**: `006-auth-vehicle-registration` | **Date**: 2026-08-19 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/006-auth-vehicle-registration/spec.md`

## Summary

Implementar autenticação real por e-mail/senha, restauração e encerramento de sessão com uma única
máquina de estados que bloqueia qualquer conteúdo privado até validar identidade e perfil. Substituir
os mocks da Home por um snapshot autorizado de `profiles` e do veículo ativo principal. Persistir a
decisão pós-cadastro por usuário com Preferences DataStore e oferecer convite, Garagem, formulário
completo e Perfil/logout em Jetpack Compose.

O acesso externo continuará no Supabase Kotlin já instalado. Leituras usam RLS e filtros explícitos;
criação de veículo e troca de principal usam RPCs PostgreSQL transacionais, serializadas por usuário,
para preservar exatamente um principal. Uma migração incremental reconcilia o schema manual remoto
com o histórico local, fecha writes diretos e mantém exclusivamente a coluna `active`.

## Technical Context

**Language/Version**: Kotlin 2.2.10, Java 11 bytecode, PostgreSQL 15/PLpgSQL

**Primary Dependencies**: Android SDK/Jetpack Compose BOM 2026.06.01, Material 3, Lifecycle 2.10.0,
Navigation Compose 2.9.8, Coroutines/StateFlow, Supabase Kotlin 3.2.6 (Auth + PostgREST), Ktor Android
3.3.1, Kotlin Serialization 1.9.0; add Preferences DataStore 1.2.1 and Coil 3.3.0
(`coil-compose` + `coil-network-ktor3`, latest stable compatible with Kotlin 2.2)

**Storage**: Supabase Auth session storage managed by the SDK; Supabase PostgreSQL tables
`public.profiles` and `public.vehicles`; one Preferences DataStore containing pending onboarding
user IDs; no Room, vehicle cache or persisted form draft

**Testing**: no automated tests per project constitution; `assembleDebug`, `lintDebug`, Supabase
local reset/lint and documented manual end-to-end/security/accessibility validation

**Target Platform**: native Android app, minSdk 24, targetSdk 36, compileSdk 37, phones in portrait
and landscape with edge-to-edge/IME support

**Project Type**: single-module Android mobile application with Supabase backend schema migrations

**Performance Goals**: 95% of normal-network authenticated opens show correct Home data within two
seconds; smooth 60fps Compose scrolling; one bootstrap sequence per session transition; no repeated
network request from recomposition

**Constraints**: no private content before validation; offline stored session is preserved but Home
is hidden; no automatic profile repair; no token/sensitive-value logs; session data excluded from
backup/transfer; no owner ID from UI/navigation; vehicle primary mutation atomic; draft discarded
on session expiry; remote schema already exists manually and must not be dropped

**Scale/Scope**: one Android module, one Supabase project, six new authenticated/public surfaces
(session gate, login, onboarding decision, Garage, vehicle form, Profile), one personalized Home
refactor, two RPCs, one incremental migration; personal vehicle lists capped at a practical 100 rows
without pagination in this delivery

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

### Pre-research gate

| Principle/gate | Status | Evidence |
|---|---|---|
| I. Simplicity/KISS/YAGNI | PASS | Reuses current Supabase client and design system; adds only DataStore for required persistence and Coil for required avatar loading; no speculative modules. |
| II. Android native MVVM | PASS | Compose screens emit events to ViewModels; ViewModels use repositories/data source; Kotlin, StateFlow, Coroutines and Navigation Compose remain standard. |
| III. Predictable state/UX | PASS | Root session and each screen use explicit exclusive states; fixed bottom nav plus lazy/IME-safe content; Material components and existing tokens reused. |
| IV. Data/security/failures | PASS | Central session, RLS, least-privilege grants, transactional RPCs, local+server validation, sanitized errors, no owner/token/document in navigation or logs. |
| V. Proportional quality | PASS | No tests because not requested; compile/lint/manual verification proportional to risk; no Hilt, Room, Realtime, broad Clean Architecture or unrelated refactor. |
| Navigation constraint | PASS | Routes/builders centralized; session transition clears private/public stacks correctly; Android Back behavior specified. |
| Persistence constraint | PASS | DataStore is used only for the simple pending preference; no cache or sensitive draft persistence. |

No constitutional violation requires an exception.

### Post-design re-evaluation

| Design concern | Status | Resolution |
|---|---|---|
| Repository/data-source boundaries | PASS | Auth SDK access is centralized in one data source; session/account/vehicle responsibilities have demonstrated consumers and no pass-through Use Case. |
| Additional dependencies | PASS | DataStore 1.2.1 is current stable; Coil 3.3.0 is selected over 3.5.0 solely for Kotlin 2.2 binary compatibility and has immediate avatar use. |
| `SECURITY DEFINER` RPCs | PASS | Required because direct DML is revoked and multi-row primary changes must be atomic; empty search path, explicit `auth.uid()` checks and restricted EXECUTE mitigate elevation. |
| Session storage | PASS | Uses SDK lifecycle/rotation, removes locally on logout and excludes SharedPreferences from backup/device transfer; app never handles token values. |
| Sensitive UI state | PASS | Password and vehicle draft stay in memory and are cleared on remote failure/session loss; private snapshots exist only in `Authenticated`. |
| Remote schema drift | PASS | Incremental reconciliation is non-destructive and remote application remains an explicit reviewed operation. |

Phase 1 design introduces no new gate failure.

## Project Structure

### Documentation (this feature)

```text
specs/006-auth-vehicle-registration/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── navigation.md
│   └── supabase.md
└── tasks.md                         # created later by $speckit-tasks
```

### Source Code (repository root)

```text
app/
├── build.gradle.kts                 # DataStore/Coil dependencies
└── src/main/
    ├── AndroidManifest.xml          # register PitGoApplication
    ├── java/com/trustarx/pitgo/
    │   ├── MainActivity.kt          # obtain/pass application container
    │   ├── PitGoApplication.kt      # process-scoped composition root
    │   ├── core/
    │   │   ├── app/
    │   │   │   └── PitGoAppContainer.kt
    │   │   ├── account/
    │   │   │   ├── data/AccountDtos.kt
    │   │   │   ├── model/AccountModels.kt
    │   │   │   └── repository/AccountRepository.kt
    │   │   ├── auth/
    │   │   │   ├── data/SupabaseAuthDataSource.kt
    │   │   │   ├── model/AppSessionState.kt
    │   │   │   └── repository/SessionRepository.kt
    │   │   ├── navigation/
    │   │   │   ├── PitGoNavHost.kt
    │   │   │   └── PitGoRoutes.kt
    │   │   ├── network/SupabaseClientProvider.kt
    │   │   ├── preferences/VehicleOnboardingRepository.kt
    │   │   ├── ui/PitGoBottomNavigation.kt
    │   │   └── vehicle/
    │   │       ├── data/VehicleDtos.kt
    │   │       ├── model/VehicleModels.kt
    │   │       ├── repository/VehicleRepository.kt
    │   │       └── validation/VehicleValidation.kt
    │   └── features/
    │       ├── session/
    │       │   ├── ui/SessionGateScreen.kt
    │       │   └── viewmodel/SessionViewModel.kt
    │       ├── login/
    │       │   ├── model/LoginModels.kt
    │       │   ├── ui/LoginScreen.kt
    │       │   └── viewmodel/LoginViewModel.kt
    │       ├── onboarding/
    │       │   ├── ui/VehicleOnboardingScreen.kt
    │       │   └── viewmodel/VehicleOnboardingViewModel.kt
    │       ├── garage/
    │       │   ├── model/GarageUiModels.kt
    │       │   ├── ui/GarageScreen.kt
    │       │   ├── ui/VehicleFormScreen.kt
    │       │   ├── viewmodel/GarageViewModel.kt
    │       │   └── viewmodel/VehicleFormViewModel.kt
    │       ├── profile/ui/ProfileScreen.kt
    │       ├── home/
    │       │   ├── model/HomeContent.kt
    │       │   ├── ui/HomeScreen.kt
    │       │   └── viewmodel/HomeViewModel.kt
    │       └── signup/
    │           ├── data/ProfileDtos.kt
    │           ├── repository/SignUpRepository.kt
    │           ├── ui/SignUpScreen.kt
    │           └── viewmodel/SignUpViewModel.kt
    ├── res/
    │   ├── drawable/                # only required vehicle/session/profile vectors
    │   ├── values/strings.xml
    │   └── xml/
    │       ├── backup_rules.xml
    │       └── data_extraction_rules.xml

gradle/libs.versions.toml             # DataStore and compatible Coil aliases

supabase/
├── config.toml
└── migrations/
    ├── 20260819000000_create_profiles.sql
    └── 20260819010000_auth_session_vehicles.sql
```

**Structure Decision**: manter o único módulo Android e a organização por feature existente.
Auth/account/vehicle vão para `core` porque são consumidos por múltiplas features; estados e
componentes exclusivamente visuais permanecem nas respectivas features. `PitGoApplication` e um
container manual fornecem uma instância de cada repositório sem framework DI. DTOs de perfil hoje
em signup serão movidos para `core/account` quando compartilhados, removendo o arquivo antigo após
atualizar seus consumidores.

## Implementation Design

### Session composition and lifecycle

1. `PitGoApplication` constructs `PitGoAppContainer` once per process from the existing singleton
   Supabase client and application context.
2. `SessionViewModel` is owned by the NavHost and exposes only `AppSessionState` plus retry/logout.
3. `SessionRepository.initialize()` waits for SDK storage, verifies the current Auth user remotely,
   loads profile and primary vehicle, and emits one exclusive state.
4. Login delegates to the same repository; signup uses the shared Auth data source but activates the
   session state only after profile confirmation and successful pending-onboarding persistence.
5. SDK session loss clears the snapshot before root navigation removes private destinations.

### Account and Home

1. `AccountRepository` decodes a narrow profile projection and active principal projection.
2. Inconsistent profile data maps to `ProfileBlocked`, not a DTO exception shown to UI.
3. `HomeViewModel` combines authenticated snapshot with the unchanged service catalog and refreshes
   account data when Home resumes after Garage mutation.
4. `HomeScreen` replaces demo header/vehicle/location with profile, avatar/initials, real principal
   or an actionable no-vehicle state and neutral address.

### Login/signup/onboarding

1. Login state owns trimmed/lowercased e-mail and in-memory password, validates locally, serializes
   submit and maps sanitized failures.
2. Existing signup keeps its form/validation, but no longer upserts missing profiles; recovery only
   rechecks the trigger-created profile.
3. Before signup success, `VehicleOnboardingRepository.markPending(userId)` must complete.
4. Root session navigation consults pending state after every authenticated bootstrap.
5. Either onboarding choice resolves the preference first; registration opens the shared vehicle
   form with only a non-sensitive origin argument.

### Garage and vehicle form

1. `VehicleRepository` lists explicit own active rows and invokes only the two RPCs for mutations.
2. Garage list/empty/error states are exclusive and update the returned primary immediately.
3. Vehicle form keeps raw fields in memory, generates a normalized domain draft on submit, focuses
   first local error and disables all duplicate events while submitting.
4. First-primary and later-primary decisions are server-owned; Android never sends `user_id`,
   `active` or final `is_primary`.
5. Session loss clears the form state and private back stack without a save/restore path.

### Database migration

1. Reconcile the repository profile migration and the user's manual remote schema without dropping
   rows/tables.
2. Ensure `avatar_url`, exact `active` naming and one hardened profile trigger.
3. Ensure canonical vehicle constraints/indexes and clean legacy invalid-primary flags only when
   safe/reviewed.
4. Revoke direct table writes from app roles, establish own-row SELECT policies and install the
   restricted transactional RPCs.
5. Validate on local reset/lint before any separately approved remote application.

## Complexity Tracking

No constitution violation or exceptional complexity is present. The manual container, Auth data
source and two database RPCs each have multiple current consumers or enforce a stated security/
atomicity requirement; no simpler alternative satisfies the same contract.
