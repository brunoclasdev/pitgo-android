# Contract: Navegação e UI autenticada

## Route catalog

| Route | Access | Arguments | Purpose |
|---|---|---|---|
| `session-gate` | root | none | initializing, verifying, offline retry, blocked profile |
| `welcome` | unauthenticated | none | public entry |
| `login` | unauthenticated | none | email/password login |
| `sign-up` | unauthenticated or signup-in-progress | none | existing account creation flow |
| `vehicle-onboarding` | authenticated + pending | none | explicit post-signup decision |
| `home` | authenticated | none | personalized account snapshot |
| `garage` | authenticated | none | active vehicles and primary selection |
| `vehicle/new?origin={origin}` | authenticated | `ONBOARDING` or `GARAGE` | vehicle form; owner never passed |
| `profile` | authenticated | none | current identity and logout |
| existing service request route | authenticated | existing service ID only | preserved feature behavior |

All route strings/builders remain centralized in `PitGoRoutes`. UUID, email, token, CPF/CNPJ,
Renavam and chassis never appear in navigation arguments.

## Root session gate

The NavHost starts at `session-gate`; this destination does not render Home data.

| Session state | Render/navigation |
|---|---|
| `Initializing`, `Verifying` | full-screen progress |
| `OfflineVerification` | offline verification message + retry |
| `ProfileBlocked` | blocked profile message + retry + Sair |
| `Unauthenticated` | navigate to Welcome while clearing private destinations |
| `Authenticated` + pending onboarding | navigate to vehicle onboarding |
| `Authenticated` + no pending onboarding | navigate to Home |

Any transition from `Authenticated` to a non-authenticated/verification state immediately hides
private content. Invalid/closed sessions clear the authenticated back stack. Offline verification
preserves the SDK session but does not preserve a visible Home/form snapshot.

## Login flow

```text
Welcome --Entrar--> Login
Login --valid credentials + verified profile--> Home
Login --Back--> Welcome
```

- duplicate submit ignored while processing;
- local field errors appear before network access;
- remote failure remains on Login with actionable message;
- successful navigation removes Login/Welcome from the authenticated back stack;
- pressing Back on Home must not reveal the password form.

## Signup and onboarding flow

```text
Welcome --Criar conta--> SignUp
SignUp --profile confirmed + pending persisted--> VehicleOnboarding
VehicleOnboarding --Agora não--> Home (pending resolved)
VehicleOnboarding --Cadastrar veículo--> NewVehicle(ONBOARDING) (pending resolved)
NewVehicle(ONBOARDING) --saved--> Home
NewVehicle(ONBOARDING) --Back/confirmed discard--> Home without vehicle
```

The onboarding destination cannot be dismissed without one explicit action. Process recreation
uses pending state to reopen it before Home. If persisting pending fails, signup does not emit UI
success; it remains recoverable without repeating identity creation.

## Garage flow

```text
Home/Profile --bottom navigation Garagem--> Garage
Garage --Adicionar veículo--> NewVehicle(GARAGE)
NewVehicle(GARAGE) --saved--> Garage refreshed
NewVehicle(GARAGE) --Back/confirmed discard--> Garage
Garage --select primary--> Garage updated
Garage --bottom navigation Início--> Home refreshed
```

Back from a dirty vehicle form requires a discard confirmation. Session expiry bypasses the dialog,
clears the draft and routes through session handling. A different authenticated identity never
receives that draft.

## Profile/logout flow

```text
Home/Garage --bottom navigation Perfil--> Profile
Profile --Sair + confirm--> Welcome
```

Logout clears the session snapshot, screen ViewModels/back stack and local Supabase session even if
remote revocation is unavailable. Pending onboarding for that `userId` is not treated as private
screen content and remains so the same account can answer later.

## Bottom navigation

- Home, Garage and Profile are active destinations in this feature;
- selected state follows the current route and includes icon/text semantics, not color alone;
- Services and Appointments preserve current non-navigation behavior until their own feature;
- service request retains Home selected as in the existing design;
- one shared `PitGoBottomNavigation` implementation is reused.

## Screen contracts

### SessionGateScreen

- no bottom navigation;
- progress or one error card;
- offline: `Tentar novamente`;
- blocked profile: `Tentar novamente` and `Sair`;
- messages never include IDs or backend details.

### LoginScreen

- top back action, title and scroll/IME-safe content;
- email and password required; password obscured with visibility toggle;
- CTA disabled with progress during submit;
- errors announced and first invalid field focused;
- password is not saveable/persistent and is cleared after remote failure.

### VehicleOnboardingScreen

- authenticated surface without bottom navigation;
- concise reason to register a vehicle;
- primary action `Cadastrar veículo` and secondary `Agora não`;
- Back does not silently resolve/dismiss the pending choice.

### HomeScreen

- loading, personalized content, no-vehicle and failure are exclusive;
- profile name/avatar URL or initials from authorized snapshot;
- vehicle card is absent/replaced by actionable empty state when no principal;
- address text is neutral, never demo data;
- existing services remain usable.

### GarageScreen

- fixed shared bottom navigation, scrollable content above it;
- empty state with add action;
- active vehicle cards only;
- primary item has label/check/radio semantics independent of color;
- per-item selection progress prevents repeated taps;
- floating or full-width add action has at least 48dp touch target.

### VehicleFormScreen

- top app bar and one scrollable IME-safe column;
- brand/model required; all schema optionals available;
- formatted display for plate/Renavam/chassis/numeric fields while state stays normalizable;
- optional `Tornar principal` only when account already has a vehicle;
- submit CTA at least 56dp, progress text and duplicate-tap guard;
- first invalid field receives focus and supporting error;
- no fixed heights that truncate with enlarged fonts.

### ProfileScreen

- shared bottom navigation with Profile selected;
- current name/e-mail from snapshot;
- explicit `Sair` action with confirmation;
- no CPF/CNPJ display in this delivery.

## Navigation consistency rules

- all navigation side effects are initiated by Route composables observing durable ViewModel state;
- ViewModels/repositories never receive `NavController`;
- callbacks use `dropUnlessResumed` or equivalent guards to prevent duplicate destinations;
- private routes verify central state before rendering any personalized component;
- returning Home after vehicle mutation requests a fresh account snapshot;
- system back, top app bar back and discard dialogs have one shared behavior per destination.
