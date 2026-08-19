# Contract: Account Registration

## Purpose

Define the boundary between the signup ViewModel, Supabase Repository, Auth session and navigation.
The UI never receives `SupabaseClient`, SDK DTOs or raw exceptions.

## Environment prerequisites

- Supabase Email provider is enabled.
- New email/password signups are enabled.
- Confirm email is disabled, so signup returns/imports a session immediately.
- Minimum password length is 8 and no additional rule exists unless also added to the UI/spec.
- The `profiles` migration in [profiles-schema.md](profiles-schema.md) is applied.
- The Android app uses only a publishable/legacy anon key; no admin/service-role key is present.

If signup completes without an active session, registration returns `EnvironmentMisconfigured` and
must not navigate to Home.

## Repository input

```text
register(AccountRegistration) -> RegistrationResult
retryProfile(AccountRegistration without requiring password when session exists) -> RegistrationResult
```

`AccountRegistration` is already locally valid and normalized. The Repository still treats it as
untrusted at the remote boundary. Its invariants are defined in [data-model.md](../data-model.md).

The Repository receives `SupabaseClient` by constructor. It owns Auth/PostgREST calls and exception
classification. A Use Case is not part of this contract.

## Normal registration sequence

1. Await Auth initialization so a persisted session is not mistaken for absence.
2. If the current session belongs to the same normalized email, skip duplicate signup and ensure
   its profile.
3. Otherwise call email/password signup with normalized profile metadata.
4. Do not use nullable signup return value as the session signal; read current session after the
   call.
5. Require a user ID and email in the current session.
6. Select `profiles` by the session user ID.
7. If present and consistent, return `Success`.
8. If absent, perform the idempotent recovery sequence.

### Metadata payload

| Key | Value |
|---|---|
| `name` | normalized name |
| `phone` | normalized phone |
| `cpf` | CPF only for individual |
| `cnpj` | CNPJ only for company |

Do not send password, duplicate email, `active`, tokens or formatted documents as metadata.

## Profile recovery sequence

1. Require an active session and take ID/email exclusively from it.
2. Select the same-ID profile first.
3. If absent, insert/upsert a `ProfileInsertDto` with conflict target `id` and ignore duplicates.
4. Never send `active`; database default owns it.
5. Select the same-ID profile again.
6. Return `Success` only if exactly one row with matching ID/email and valid document shape exists.
7. Otherwise return `ProfileRecoveryRequired`; Home remains blocked and retry invokes recovery, not
   another signup.

For an ambiguous previous response without a local session, a later signup conflict may trigger one
sign-in attempt with the submitted email/password. If it produces a session, continue recovery. It
must not loop or retry automatically without bound.

## Result contract

| Result | Trigger | Required presentation |
|---|---|---|
| `Success` | Session + profile confirmed | Navigate Home and clear unauthenticated stack |
| `EmailUnavailable` | Stable allowlisted email conflict | Neutral email field error |
| `DocumentUnavailable` | Allowlisted profile CPF/CNPJ unique constraint | Neutral document field error |
| `InvalidServerInput` | Backend validation/weak password | Relevant field or generic input message |
| `RateLimited` | Stable rate-limit code/status | Ask user to wait; no immediate auto-retry |
| `Offline` | Sanitized network failure | Preserve non-sensitive values; retry action |
| `ServiceUnavailable` | Timeout/5xx/disabled provider | Generic actionable banner |
| `ProfileRecoveryRequired` | Authenticated identity but unconfirmed profile | Read-only pending state + retry profile |
| `EnvironmentMisconfigured` | No immediate session after accepted signup | Block Home; generic setup message |
| `Unknown` | Everything else | Generic retry message |

## Error sanitization

- Rethrow `CancellationException`.
- Map only stable Auth error codes, HTTP status and allowlisted database constraint names.
- Treat generic conflict as generic unless the stable code names the resource.
- Never log or propagate SDK Throwable, exception message, request/response headers, PostgREST
  `details`, metadata, password, token, CPF or CNPJ.
- UI text must not confirm which other account owns an email or document.

## ViewModel/UI contract

- ViewModel accepts explicit field events and owns validation/submission.
- Inputs and CTA are disabled during Submitting; repeated submit is a no-op.
- Local invalid submit sets all field errors and focuses the first in visual order.
- Remote completion clears password and confirmation.
- Success is durable UI state; Route observes it and invokes `onAccountCreated`.
- Back uses the same path for app bar and system gesture; dirty draft requires confirmation.
- Back is blocked while a potentially committed remote operation is being finalized.

## Navigation contract

```text
Welcome --Criar conta--> SignUp
SignUp --Back pristine/confirmed discard--> Welcome
SignUp --Success--> Home
```

Success navigation uses `popUpTo(Welcome) { inclusive = true }` plus `launchSingleTop`, so Back from
Home cannot reveal the signup form or its sensitive in-memory state.
