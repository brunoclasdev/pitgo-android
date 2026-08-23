# Contract: Navigation and role-aware session

## Central routes

| Route | Access | Entry/exit |
|---|---|---|
| `provider-sign-up` | public, no session | Welcome company CTA; success refreshes session |
| `provider-registration` | PROVIDER Onboarding | resumed after signup/login; success replaces with dashboard |
| `provider-dashboard` | PROVIDER Ready | role home; owns mode/availability/logout |
| `provider-migration-start` | CLIENT with legacy flag | starts proof, then intentional account switch |
| `provider-migration-claim` | PROVIDER Onboarding + in-memory challenge | claims/repairs legacy company |
| `service-map/{serviceId}` | CLIENT | Home real service card; back returns Home |
| `appointment/{appointmentId}/tracking` | CLIENT participant | private marker, no bottom nav |
| `provider-appointment/{appointmentId}/tracking` | PROVIDER participant | starts/stops eligible FGS |

Routes contain only allow-listed destination names and opaque service/appointment UUIDs. They never
contain role, user/provider owner ID, coordinates, address, token, e-mail or document. Central route
builders validate/URI-encode UUID strings; repositories remain authoritative.

## Session destination matrix

```text
Unauthenticated                         -> Welcome/public routes
Authenticated.Client.Ready             -> Client Home
Authenticated.Provider.Onboarding      -> Provider Registration
Authenticated.Provider.Ready           -> Provider Dashboard
OfflineVerification/Blocked/Inconsistent -> Session Gate
```

The current route is re-evaluated whenever session state changes. A CLIENT attempting provider route
or PROVIDER attempting client route is redirected to its own root; it never sees a partial screen.
Provider legacy presence does not change a CLIENT destination.

Login is common: credentials are submitted once, then `get_my_account_context()` determines the
destination. UI never asks the user to declare a role during login and never falls back when the
context is absent/ambiguous.

## Signup stacks

Client:

```text
Welcome -> Client SignUp -> Client vehicle onboarding/Home
```

Provider:

```text
Welcome -> Provider SignUp -> Provider Registration -> Provider Dashboard
```

Provider Auth creation may be complete while registration is pending. Back/restart/login resumes
Provider Registration. After registration, the public/signup/onboarding stack is removed so Android
Back cannot reveal credentials/draft.

The existing `SignUpOrigin.PROVIDER` path is removed; company CTA must never call Client SignUp or
lock its person type.

## Assisted migration stack

```text
Client Profile -> Migration Start -> intentional logout
 -> Provider SignUp/Login -> Migration Claim -> Provider Dashboard
```

The plaintext challenge exists only in a process-scoped coordinator for at most 15 minutes. The
intentional logout used between proofs must stop all location work but may retain this one migration
challenge. Process death clears it and requires restarting from the original CLIENT session. It is
never placed in SavedStateHandle, DataStore, route, analytics or logs.

## Tracking cleanup

Before clearing an authenticated session, the session coordinator signals the tracking controller to
stop foreground service/channel/callback and performs best-effort stop RPC. Navigation then clears
role-owned ViewModels and replaces the back stack with Welcome. Late results carry expected user ID
and are discarded after account switch.

## UI shells

- Service discovery map retains the client bottom navigation with Home selected; Scaffold insets
  protect map controls and list.
- Provider Registration/Dashboard use the provider experience and never render client bottom nav.
- Appointment tracking is a focused child screen with TopAppBar/back and no bottom nav.
- Map always has an equivalent `Mapa | Lista` representation; expanded landscape may render both,
  compact/font-scaled layouts render one mode at a time.
