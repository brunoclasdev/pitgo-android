# Data Model: Criar conta de usuário

## Overview

O fluxo mantém um rascunho somente em memória, produz uma requisição normalizada, cria uma
identidade Supabase Auth e garante um único perfil PostgreSQL com o mesmo UUID. Senha nunca pertence
ao perfil nem a armazenamento de estado persistente.

```text
RegistrationDraft
        │ validate + normalize
        ▼
AccountRegistration ── signUp ──► auth.users (id)
                                      │ 1:1, FK + trigger/recovery
                                      ▼
                                public.profiles (id)
```

## PersonType

| Value | UI meaning | Required document | `profiles` representation |
|---|---|---|---|
| `INDIVIDUAL` | Pessoa física | CPF | `cpf` present; `cnpj` null |
| `COMPANY` | Pessoa jurídica | CNPJ | `cnpj` present; `cpf` null |

`PersonType` belongs to the draft/domain model only. It is inferred from the non-null document and
does not create another database column.

## RegistrationDraft

Mutable-by-copy form values owned by `SignUpViewModel` through immutable `SignUpUiState`.

| Field | Type | Initial value | Rules |
|---|---|---|---|
| `personType` | `PersonType?` | null | Required before submit |
| `name` | `String` | empty | Label is full name for individual and company/legal name for company |
| `email` | `String` | empty | Required; normalized only for validation/submission |
| `phone` | `String` | empty | Digits-only in state; max 11 |
| `document` | `String` | empty | One field; CPF digits or CNPJ uppercase alphanumeric according to type |
| `password` | `String` | empty | At least 8 characters; memory only |
| `passwordConfirmation` | `String` | empty | Must equal password; memory only |

Changing `personType` clears `document` and its error, so the draft cannot contain both CPF and
CNPJ. Name, email and phone are preserved. Password fields are cleared after any completed remote
failure and on success; they are never placed in `SavedStateHandle` or saved UI state.

## AccountRegistration

Normalized, validated input passed from ViewModel to Repository.

| Field | Type | Invariant |
|---|---|---|
| `personType` | `PersonType` | Non-null |
| `name` | `String` | `trim()` result is nonblank; internal characters preserved |
| `email` | `String` | `trim().lowercase(Locale.ROOT)` and locally valid |
| `phone` | `String` | 10 or 11 digits with valid Brazilian DDD |
| `cpf` | `String?` | Exactly 11 valid digits for `INDIVIDUAL`; otherwise null |
| `cnpj` | `String?` | Exactly 14 canonical characters with valid DVs for `COMPANY`; otherwise null |
| `password` | `String` | At least 8 characters; consumed only by Auth/recovery |

Invariant: exactly one of `cpf` and `cnpj` is non-null.

## Auth signup metadata

The signup request carries only values needed by the database trigger/recovery:

| Key | Source | Rule |
|---|---|---|
| `name` | normalized name | Required |
| `phone` | normalized phone | Required |
| `cpf` | CPF | Present only for individual |
| `cnpj` | CNPJ | Present only for company |

Email comes from `auth.users.email`; password and `active` are never metadata. User metadata is
untrusted input and must not be used in RLS. Database checks remain authoritative.

## Profile (`public.profiles`)

| Column | PostgreSQL type | Null | Default | Constraints |
|---|---|---:|---|---|
| `id` | `uuid` | No | — | PK; FK `auth.users(id) on delete cascade` |
| `name` | `text` | No | — | Trimmed, nonblank |
| `email` | `text` | No | — | Lowercase/trimmed, nonblank, unique |
| `phone` | `text` | No | — | Canonical Brazilian 10/11 digits |
| `cpf` | `text` | Yes | null | Canonical valid CPF, unique when present |
| `cnpj` | `text` | Yes | null | Canonical valid numeric/alphanumeric CNPJ, unique when present |
| `active` | `boolean` | No | `true` | System-owned; never supplied by the form |

Table invariants:

- `num_nonnulls(cpf, cnpj) = 1`;
- CPF is 11 digits, not a repeated sequence, and both DVs are valid;
- CNPJ matches `[0-9A-Z]{12}[0-9]{2}` and both official DVs are valid;
- nullable unique constraints allow many nulls while rejecting repeated documents;
- profile ID is always the authenticated user ID;
- profile email must match the authenticated identity email during client recovery.

No person-type, timestamps, address, company entity or editable activity field is added.

## DTOs

### ProfileInsertDto

Internal serialized payload with `id`, `name`, `email`, `phone`, nullable `cpf` and `cnpj`. It omits
`active`, allowing the database default to remain authoritative.

### ProfileDto

Internal response DTO with all profile columns. Kotlin property `isActive` maps from the exact
database name `active` via serialization name metadata. It is used only to confirm a consistent
profile; no UI profile model is introduced.

## Validation results

`SignUpFieldErrors` has at most one typed error for each visual input: person type, name, email,
phone, document, password and password confirmation. It contains no rendered strings and no raw
backend exception.

Validation order for focus is the same as visual order:

1. person type;
2. name;
3. email;
4. phone;
5. document;
6. password;
7. confirmation.

All applicable errors are calculated in one pass; `FocusRequest(field, requestId)` points to the
first and increments `requestId` on every invalid submit.

## RegistrationResult

Repository output is a sealed domain result:

| Result | Meaning | UI behavior |
|---|---|---|
| `Success(userId)` | Session and same-ID profile confirmed | Set Success; navigate Home |
| `EmailUnavailable` | Stable Auth/DB conflict for email | Field error with neutral wording |
| `DocumentUnavailable` | Allowlisted CPF/CNPJ unique conflict | Document error with neutral wording |
| `InvalidServerInput` | Backend rejected contract/weak password | Field/banner; no technical detail |
| `RateLimited` | Auth/API throttled | Ask user to wait before retry |
| `Offline` | Network unavailable | Preserve non-sensitive fields; retry |
| `ServiceUnavailable` | Timeout or 5xx/configuration service error | Preserve non-sensitive fields; retry |
| `ProfileRecoveryRequired` | Session exists but profile not confirmed | Keep Home blocked; retry recovery |
| `EnvironmentMisconfigured` | Signup produced no immediate session | Block Home; generic configuration message |
| `Unknown` | Sanitized unmapped failure | Generic retry message |

Raw SDK exceptions and their messages/details never cross the Repository boundary.

## SignUpUiState

`SignUpUiState` combines the draft, field errors, focus request and exactly one submission state:

- `Editing`;
- `Submitting(stage)` where stage is `CREATING_ACCOUNT`, `ENSURING_PROFILE` or
  `RECOVERING_PROFILE`;
- `Failure(reason, profilePending)`;
- `Success`.

Derived properties include `hasDraft`, `isInputEnabled`, dynamic name/document labels and current
document maximum length/keyboard type.

## State transitions

```text
Editing ── invalid submit ─────────────► Failure(INVALID_FORM) ── edit ─► Editing
   │
   └── valid submit ─► Submitting(CREATING_ACCOUNT)
                           │
                           ├── Auth failure ─► Failure(recoverable) ─► Editing/retry
                           │
                           └── session ─► Submitting(ENSURING_PROFILE)
                                              │
                                              ├── profile confirmed ─► Success
                                              └── partial/ambiguous ─► Failure(profilePending)
                                                                            │
                                                                            └── retry
                                                                                ▼
                                                               Submitting(RECOVERING_PROFILE)
```

Submits during any `Submitting` state are no-ops. Home navigation occurs only from `Success`.
Back is confirmed when `hasDraft` and blocked while processing a result that may have reached the
server.
