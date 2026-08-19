# Quickstart: Validar criação de conta

## 1. Prerequisites

- JDK 17 and Android SDK configured for the existing project.
- Supabase CLI installed for migration validation; Docker running for local Supabase.
- A dedicated Supabase development project for Android end-to-end checks.
- Valid client-safe Supabase URL/key supplied outside Git as described by feature 004.
- In Supabase Auth settings:
  - Email provider enabled;
  - new signups enabled;
  - Confirm email disabled;
  - minimum password length set to 8 with no hidden composition rule.

Never use a production database or service-role key in this guide.

## 2. Validate the migration locally

From the repository root:

```bash
supabase start
supabase db reset
supabase db lint
```

Expected:

- migration applies from an empty local database;
- `public.profiles` matches [the schema contract](contracts/profiles-schema.md);
- validator functions, trigger, grants and policies exist;
- no migration requires an Android credential.

If `db lint` is unavailable in the installed CLI version, inspect the reset output and schema diff;
do not skip `db reset`.

## 3. Apply to a development project

Link only the intended development project, review the generated diff, then apply the migration:

```bash
supabase link
supabase db diff
supabase db push
```

Remote mutation is deliberate: confirm the project reference before `db push`. Do not run this
against production as part of implementation validation.

In the Dashboard, re-check the Auth settings from section 1. Confirm-email behavior is not set by
the SQL migration.

## 4. Build the Android app

Provide `supabaseUrl` and `supabaseKey` via ignored `local.properties` or Gradle user-home
properties, then run:

```bash
./gradlew :app:assembleDebug :app:lintDebug
```

Expected:

- Kotlin Serialization, Auth and PostgREST resolve at the pinned compatible versions;
- the client installs only Auth and PostgREST in addition to its core;
- debug APK builds and lint completes without credential output;
- no automated test task or test source is added.

Install the debug APK on a supported phone/emulator connected to the development project.

## 5. Primary flow — individual

1. Launch the app and tap **Criar conta** on Welcome.
2. Select **Pessoa física**.
3. Confirm the name label is **Nome completo** and only CPF is shown.
4. Enter valid non-production fixture data, including a valid CPF and an 8+ character password.
5. Submit once, then tap repeatedly while processing.

Expected:

- only one remote submission is active;
- progress appears within one second;
- success opens Home immediately;
- Back from Home does not return to Signup/Welcome;
- Auth contains one user and `profiles` contains exactly one same-ID row;
- profile values are canonical, CNPJ is null and `active=true`.

## 6. Primary flow — company

Repeat with **Pessoa jurídica** using both kinds of approved CNPJ fixture in separate accounts:

- legacy numeric CNPJ;
- alphanumeric CNPJ under the Receita Federal 2026 format.

Expected:

- label becomes **Razão social / nome da empresa**;
- only CNPJ is shown and accepts uppercase letters/numbers with mask;
- CPF is null in the stored company profile;
- invalid checksum is blocked locally and rejected by the database if sent by a modified client.

## 7. Form and interaction matrix

Verify each case independently:

| Scenario | Expected result |
|---|---|
| No person type | Group error; focus/scroll to type |
| Blank name/email/phone/document/password/confirmation | All applicable errors shown; focus on first |
| Password with 7 characters | Local block with 8-character guidance |
| Confirmation differs | Local block on confirmation |
| Invalid email/DDD/CPF/CNPJ DV | Local block; no Auth/profile row |
| Switch individual → company | Document clears; CNPJ keyboard/mask replaces CPF |
| Paste masked phone/CPF/CNPJ | Canonical value and cursor mapping remain correct |
| Show/hide password | Independent accessible toggles; value unchanged |
| Back with pristine form | Return immediately to Welcome |
| Back with draft | Discard dialog; continue preserves draft; discard returns |
| Back during processing | Operation remains protected; no partial navigation |

Repeat on a compact device, landscape, keyboard open, font scale 200%, dark theme and TalkBack.
All controls/errors/CTA must remain reachable, selectable state must not rely only on color and
TalkBack must announce labels, errors, loading and password visibility actions.

## 8. Conflict and failure behavior

Using only development fixtures:

1. Attempt another registration with an existing email.
2. Attempt new emails with an existing CPF or CNPJ.
3. Disconnect networking before submit and during a request.
4. Restore connectivity and retry.
5. Trigger the development project's rate limit or temporarily unavailable endpoint only in a
   controlled environment.

Expected:

- UI uses neutral email/document availability messages without identifying the owner;
- offline, timeout, rate limit and service failures have distinct actionable presentation where
  the SDK provides a reliable signal;
- non-sensitive fields remain; password and confirmation clear after remote failure;
- no exception message, headers, token, password or full document appears in app logs/crash output;
- retry never produces duplicate Auth/profile rows.

## 9. Atomicity and recovery

### Trigger rollback

In the development environment, call signup with deliberately invalid normalized profile metadata
using an API client that does not contain admin credentials. The Android UI correctly blocks this,
so this is a backend contract check.

Expected: profile CHECK failure blocks signup and no committed Auth user or profile remains.

### Missing-profile recovery

1. Create a valid development account and retain its normal client session.
2. As a database administrator in the development project, remove only its profile to simulate a
   historical partial state.
3. Re-enter the registration/recovery path with the same credentials and normalized draft.
4. Repeat the retry action rapidly.

Expected:

- Home remains blocked until the profile is confirmed;
- recovery inserts exactly one same-ID profile with `active=true`;
- repeated recovery converges to the same row;
- no second Auth identity is created.

## 10. RLS verification

Create two development users and exercise the Data API with each user's own access token:

- own profile SELECT returns exactly one row;
- cross-user SELECT returns no row;
- own missing-profile INSERT may succeed when all constraints pass;
- cross-user INSERT is denied;
- anonymous SELECT/INSERT is denied;
- UPDATE/DELETE is denied for authenticated clients.

Do not print access tokens or returned CPF/CNPJ in terminal output or retain them in shell history.

## 11. Completion evidence

The feature is ready when:

- build/lint pass;
- migration reset and reviewed development push pass;
- individual and company flows open Home with session + same-ID profile;
- validation, accessibility, back stack and failure matrix pass;
- atomic rollback and idempotent recovery are demonstrated;
- RLS/grants deny every cross-user/anonymous case;
- repository/log review finds zero raw remote exception, token, password or complete document.
