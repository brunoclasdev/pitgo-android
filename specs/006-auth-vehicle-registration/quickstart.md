# Quickstart: validar sessão autenticada e cadastro de veículos

## 1. Prerequisites

- Android SDK configurado e um aparelho/emulador API 24+;
- Docker para o ambiente Supabase local;
- Supabase CLI via instalação oficial ou `npx supabase` (o binário global não está instalado na
  máquina no momento deste plano);
- `local.properties` não versionado contendo `supabaseUrl` e uma publishable/legacy anon key;
- provedor Email habilitado, signup habilitado, confirmação de e-mail desabilitada e senha mínima
  de oito caracteres no projeto usado para a validação;
- nenhuma secret/service-role key no aplicativo.

Leia antes:

- [modelo de dados](data-model.md);
- [contrato Supabase](contracts/supabase.md);
- [contrato de navegação](contracts/navigation.md).

## 2. Establish the database baseline

Como a tabela remota foi criada manualmente, primeiro compare o schema remoto e o histórico local.
Não execute `db push` em produção como parte automática deste guia.

Para uma base local descartável:

```bash
npx supabase start
npx supabase db reset
npx supabase db lint
```

Expected:

- migrations finish without dropping existing model data by design;
- `profiles` and `vehicles` exist;
- only column `active` is used for profile status;
- one secure Auth trigger exists;
- RLS is enabled;
- `authenticated` can SELECT own rows but cannot directly INSERT/UPDATE/DELETE vehicles;
- authenticated role can execute only the two vehicle RPCs required by the contract.

Before any deliberate remote migration, review the generated diff/history with the project owner,
take an appropriate backup and then apply through the normal Supabase migration workflow.

## 3. Build the Android app

```bash
./gradlew :app:assembleDebug :app:lintDebug
```

Expected: both tasks succeed with no tests generated or executed. Install the debug APK using
Android Studio or:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 4. Validate session startup and login

### No stored session

1. Clear app data and open PitGo.
2. Confirm no Home/profile/vehicle appears before Welcome.
3. Tap `Entrar`.
4. Submit empty/invalid email and password.

Expected: local field errors, no request for invalid form, responsive IME/scroll and no bottom nav
on Login.

### Valid login and restoration

1. Sign in with an existing active profile.
2. Confirm Home shows that account's name and initials/avatar.
3. If the account has a primary vehicle, confirm exact brand/model/year/plate.
4. Force-stop and reopen the app.

Expected: a verification surface appears until the session/profile are authorized, then Home opens
without requesting credentials and without flashing mock data.

### Invalid credentials and repeated taps

1. Enter a valid e-mail with wrong password.
2. Tap submit repeatedly while loading.

Expected: one request in flight, neutral invalid-credentials message, password cleared and no
private route opened.

### Offline stored session

1. Sign in successfully, force-stop, disable network and reopen.
2. Tap `Tentar novamente` while still offline.
3. Restore network and retry.

Expected: session is preserved; Home and old personalized data remain hidden; no automatic logout;
successful retry returns to the same account.

### Missing/inactive/inconsistent profile

Using a disposable account/database, make the associated profile missing, inactive or invalid and
reopen.

Expected: Home is blocked; only retry and logout recovery are offered; no profile is inserted,
updated or reactivated by the Android client.

### Logout

1. Open Profile from the bottom navigation.
2. Confirm `Sair`.
3. Press Back after Welcome.

Expected: account snapshot, Home, Garage and vehicle drafts are gone; Back never reveals private
content; the Supabase local session is removed even if logout is performed offline.

## 5. Validate signup onboarding

### Persistent pending choice

1. Create a new valid account.
2. When the vehicle invitation appears, force-stop without choosing.
3. Reopen with network available.

Expected: after session verification, the invitation appears again before Home.

### `Agora não`

1. Choose `Agora não`.
2. Confirm Home opens in the no-vehicle state.
3. Reopen the app.

Expected: the invitation does not repeat; Home offers access to Garage.

### `Cadastrar veículo`

1. Create another account or mark a disposable signup pending.
2. Choose `Cadastrar veículo`.
3. Back out with an empty form or confirm discard of a dirty form.

Expected: the explicit choice is considered resolved; Home opens without vehicle and the invitation
does not repeat. No vehicle is inserted.

## 6. Validate vehicle form

### Local rules

Check each condition independently:

- brand/model blank -> required errors;
- legacy plate and Mercosul plate -> accepted/canonicalized;
- malformed plate -> rejected;
- Renavam shorter/longer/non-digit -> rejected while leading zeros are preserved;
- chassis not 17 characters or containing I/O/Q -> rejected;
- years before 1900 or after next calendar year -> rejected;
- negative/non-numeric mileage -> rejected;
- every optional field blank -> accepted as null.

Expected: all invalid data is blocked before network, the first error receives focus and masks never
change the canonical persisted value.

### First vehicle

1. With an account containing no vehicle, submit only brand and model.
2. Inspect Home, Garage and the database row.

Expected: exactly one row belongs to `auth.uid()`, `active = true`, `is_primary = true`, and Home
shows it. The owner/status fields were never editable.

### Additional vehicle and primary choice

1. Add a second vehicle without selecting it as primary.
2. Confirm the original stays primary.
3. Add/select a vehicle as primary.

Expected: the RPC finishes with exactly one primary; Garage updates immediately and Home refreshes
to the same vehicle when visible again.

### Duplicate identifiers

Across two disposable accounts, attempt duplicate canonical plate, Renavam and chassis.

Expected: each attempt is rejected with a neutral field-specific availability message; no owner,
document value or backend details are disclosed.

### Concurrency/idempotency

1. Tap create/set-primary repeatedly.
2. If possible, trigger competing primary selections for the same account from two clients.

Expected: one form operation per client, no duplicate vehicle from repeated UI taps and database
ends with exactly one primary because the RPC serializes per user.

## 7. Validate Garage and authorization

1. Open an account with no vehicles: verify empty state and add action.
2. Open an account with several rows including an inactive row: verify only active rows appear.
3. Use two accounts and try to query/change the other's known vehicle ID through a REST client using
   each user's normal JWT.

Expected: cross-account SELECT returns no row and mutation RPC returns neutral denied/not-found;
inactive vehicles never appear or become primary. Never use service-role credentials for this test.

## 8. Validate session expiry during vehicle entry

1. Enter identifiable values in the vehicle form without submitting.
2. Expire/revoke the session or force the SDK into an invalid session state.
3. Authenticate again, including once with a different account.

Expected: the old form disappears, every field is empty, no vehicle was inserted and no value is
restored to either account. If expiration cannot be verified because the device is offline, the
session verification screen appears and the draft is still discarded.

## 9. Visual, accessibility and privacy pass

Validate on a compact phone, landscape, font scale 200%, keyboard open and TalkBack:

- all form fields/CTAs remain reachable;
- touch targets are at least 48dp;
- loading and errors are announced;
- selected primary is communicated by text/semantics, not only purple color;
- avatar loading has bounded size, placeholder and initials fallback;
- bottom navigation remains fixed without covering Garage/Home content.

Inspect Logcat and visible messages after success and every failure. Expected zero full password,
token, CPF/CNPJ, Renavam, chassis, exception `details`, request URL or authorization header.

## 10. Completion evidence

Record the following before implementation handoff:

- successful `assembleDebug` and `lintDebug` output;
- successful local database reset/lint or reviewed SQL Editor application;
- screenshots of session gate, personalized/no-vehicle Home, persistent invitation, Garage and form;
- database evidence that two accounts cannot see/mutate each other and one account has at most one
  primary;
- confirmation that no real credential or sensitive test value entered tracked files/log artifacts.

### Implementation run — 2026-08-19

- `./gradlew :app:compileDebugKotlin`: successful after navigation, Garage and real-session
  vehicle integration.
- Android device check: one physical ASUS device is attached through ADB and available for the
  installation/smoke pass.
- Supabase CLI 2.115.0 is available. `npx supabase db reset` could not inspect a local service and
  `npx supabase db lint --local` could not connect to PostgreSQL at `127.0.0.1:54322` because Docker
  is not installed/running in this environment. No remote migration was applied. T052 remains open.
- Static privacy audit: Auth/account/vehicle data sources map only typed classes, status codes and
  allowlisted constraint names. No raw throwable, request URL, headers, token, password, CPF/CNPJ,
  Renavam or chassis is logged or propagated to UI.
- `./gradlew :app:assembleDebug`: successful; `./gradlew :app:lintDebug`: successful with zero
  errors. The remaining 15 warnings only report intentionally pinned toolchain/dependency versions
  and the pre-existing target SDK level; no code or resource warning remains.
- Physical-device smoke pass: the existing session was restored, Home rendered the authenticated
  profile name and no-vehicle state, Garage rendered its empty state, and the add-vehicle form
  opened and returned to Garage without a crash. No remote row was created or changed.
- Responsive smoke pass on the same device: Garage remained scrollable/reachable at 200% font
  scale and rendered in a 2400×1080 landscape viewport. The original font/rotation settings were
  restored. TalkBack and the mutating end-to-end scenarios remain pending.
- `git diff --check`: clean. `local.properties`, `.gradle/` and `app/build/` are ignored; no
  credential-like file or JWT literal is tracked. The only `sb_*` occurrences are explicit
  redacted examples in feature 004 documentation. A Logcat scan found zero occurrences of the
  configured client key and zero JWT-like values.
