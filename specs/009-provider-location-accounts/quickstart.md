# Quickstart: validar contas, cadastro, mapa e localização

This is a manual validation guide. It intentionally contains no automated test suite or production
migration bodies.

## Prerequisites

- Android Studio/toolchain already used by the repository; JDK/Gradle wrapper available.
- Supabase CLI and Docker for local database/auth/functions validation.
- A Google Cloud project with billing, Maps SDK for Android and Geocoding API enabled.
- Separate restricted keys:
  - Android Maps key restricted to package `com.trustarx.pitgo`, debug/release signing SHA-1 and
    Maps SDK for Android;
  - server Geocoding key restricted to Geocoding API and stored only in Supabase secrets.
- Supabase Email provider enabled. Keep the current project policy for immediate session (email
  confirmation disabled) or validate the explicit confirmation state before expecting navigation.
- At least one active service category and service in the catalog.
- Two devices/emulators for publisher-conflict/Realtime scenarios; include API 31 and API 34+ when
  possible.

Local, ignored configuration:

```properties
supabaseUrl=https://<project-ref>.supabase.co
supabaseKey=sb_publishable_<redacted>
MAPS_API_KEY=<android-restricted-key>
```

Set the server key through Supabase secrets, never in the Android file:

```bash
supabase secrets set MAPTILER_API_KEY
```

## Build and backend setup

```bash
supabase start
supabase db reset
supabase functions serve geocode-provider-address
./gradlew :app:assembleDebug :app:lintDebug
```

Expected:

- all migrations and the signup hook load without silently rewriting ambiguous legacy data;
- APK/lint compile with Maps, Functions, Realtime and foreground-service declarations;
- no real key or token appears in tracked files or build/error output.

### Resultado desta implementação (2026-08-21)

- `ORG_GRADLE_PROJECT_MAPS_API_KEY=validation-only ./gradlew :app:assembleDebug` — aprovado;
- `ORG_GRADLE_PROJECT_MAPS_API_KEY=validation-only ./gradlew :app:lintDebug` — aprovado, sem erros;
- Maps Compose foi fixado em `6.10.0`, a versão oficial construída com Kotlin `2.2.10`; a linha
  8.x exige metadata Kotlin mais nova que o compilador atual do app;
- auditoria estática confirmou que chaves server-side aparecem somente como nomes de variáveis de
  ambiente das Edge Functions e que o APK contém apenas o placeholder da chave Android;
- `supabase db reset`/`db lint` e lint Deno não puderam ser executados neste ambiente porque
  `supabase`, Docker e Deno não estão instalados. As migrations não foram aplicadas remotamente.

For a remote environment, deploy migrations/functions and configure the Before User Created Hook,
private Realtime authorization, Broadcast database trigger and scheduled geocode refresh deliberately.
Do not run `db push` against production as an implicit validation step.

## 1. Client versus provider account

1. From Welcome, create a normal client.
2. Confirm `account_roles=CLIENT`, one Profile exists and no Provider is created.
3. Logout. Tap “Sou uma empresa · Cadastrar”.
4. Confirm the first screen is Provider account creation, not CPF/profile signup.
5. Complete Auth signup and interrupt before company onboarding finishes.
6. Login again with the provider credentials. Confirm it resumes Provider onboarding and never opens
   client Home/vehicle onboarding.
7. Complete provider registration and confirm it opens Provider dashboard.
8. Try the client e-mail for provider signup and vice versa. Confirm a generic unavailable-account
   message and no role mutation.
9. Remove/corrupt the role in a disposable local fixture. Confirm the app blocks rather than guessing.

## 2. Provider registration and location verification

Exercise both FIXED and MOBILE:

- category/service catalog loading, empty, offline and changed-during-submit states;
- multiple categories and services, remove/add, duplicate category/service prevention;
- provider/company validation and CNPJ/phone normalization;
- permission first rationale, denial, approximate-only, permanent denial/settings and location
  services disabled;
- stale/imprecise fix and 15-second acquisition timeout;
- address no-match/partial/wrong region/upstream unavailable;
- measured distances of 499 m and 501 m: first proceeds, second requires explicit confirmation;
- edit one address field after verification: proof/confirmation must be invalidated;
- double tap, concurrent submit and response loss: exactly one Provider, Address and set of
  ProviderCategory/ProviderService rows;
- inspect Address: persisted coordinate is geocoded address; no device coordinate/history exists.

Verify a FIXED provider cannot start mobile tracking. Change MOBILE to FIXED and availability off in
Provider dashboard; any current row must be removed.

## 3. Legacy assisted migration

Create a disposable legacy fixture matching the pre-009 model (CLIENT role/profile owning a Provider
with address/services).

1. Login as the client and confirm the app offers migration but does not open Provider private area.
2. Start migration, logout intentionally and create/login a different-email PROVIDER account.
3. Complete any requested modality/address verification and claim the transfer.
4. Confirm only `providers.account_id`/transfer audit changed; Provider ID, Address,
   ProviderCategories and ProviderServices remain; Profile remains CLIENT.
5. Repeat claim after simulated response loss: receive `ALREADY_MIGRATED`, no copies.
6. Validate expired token, wrong target, same email, target with existing Provider and concurrent
   claims. Each must leave ownership unchanged and expose no account details.

## 4. Service map discovery

1. Seed active/inactive, available/unavailable, FIXED/MOBILE Providers across services/categories and
   address distances. Keep all mobile current-location rows absent or deliberately different.
2. Login CLIENT and tap a Home service card.
3. Confirm route carries its real service UUID and returns only exact service+category matches.
4. Confirm both provider modes plot the business address, never mobile current point.
5. Pan/zoom and use “Buscar nesta área”; quickly change service/viewport and confirm stale responses
   do not replace current results.
6. Deny client location: map still opens at a selected/manual region and the rest of the app works.
7. Validate loading, empty, offline, retry, clustering and overlapping markers.
8. Switch Map/List, use TalkBack and font 200%; every result/action must remain available without
   relying on marker color or info windows.
9. Inspect response/UI: no CNPJ, contact, owner Auth UUID, client coordinate or mobile coordinate.

Target: first results or understandable empty state within 3 seconds under normal connectivity.

## 5. Mobile tracking and private Realtime

Because appointment dispatch is out of scope, insert a disposable authoritative Appointment fixture
through admin/local SQL, never through an Android payload. Associate one CLIENT, one MOBILE Provider
and an exact offered service; set `ACCEPTED`.

1. Start tracking from the visible Provider appointment screen with precise permission.
2. Confirm foreground notification/in-app indicator and approximately 10-second publishes.
3. Minimize the app: with notification permission, updates continue; without it, the product explains
   and limits tracking to visible mode.
4. The participant CLIENT subscribes and sees marker movement without reopening. A third CLIENT and
   public discovery must fail to join/read it.
5. Stop events: COMPLETED, CANCELLED, logout, session switch/expiry, permission revoke, settings off,
   provider FIXED/inactive/unavailable. Each must stop publication and clear current row.
6. Kill the process. It must not restart silently; participant UI changes to stale after 30 seconds.
7. Disconnect network: only newest in-memory fix remains; reconnect publishes current fix, not a path.
8. Start a second provider device: it receives publisher conflict while first publish is fresh and
   can take over only after >30 seconds. Old sequence/timestamp must never regress marker/version.
9. Disconnect/reconnect participant Realtime. It must re-fetch snapshot and discard duplicate/older
   Broadcast versions.

Target: new valid position reflected in <=10 seconds for normal connectivity; no marker labeled live
after 30 seconds without a valid server publish.

## 6. Geocode retention and privacy audit

- Advance a disposable `geocoded_at` to day 25 and run refresh; confirm validity is renewed.
- Simulate refresh failure past 30 days; Provider is excluded from discovery without deleting data.
- Simulate a materially changed upstream point; mark for reverification rather than silently moving.
- Search logs/crash output and tracked files for Auth tokens, API keys, CNPJ, transfer tokens, device
  points and raw Supabase/Google exception payloads; none should be present.
- Confirm tables contain one current location at most and no historical route/fix collection.

## 7. Android version/device matrix

- API 31/32: combined COARSE+FINE and approximate downgrade behavior.
- API 33: notification denied/granted behavior.
- API 34–36: foreground service type/permission and visible-start restriction.
- portrait, landscape, compact/expanded widths and font 200%.
- physical device location services off/on and OEM process kill when available.
