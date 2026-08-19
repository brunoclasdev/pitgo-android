# Contract: Supabase Auth and `profiles`

## Ownership

- Authentication identity is owned by Supabase Auth in `auth.users`.
- Application profile is owned by `public.profiles`.
- Both rows share the same UUID; the profile cannot exist without its Auth identity.
- `public.profiles` is the authoritative profile store. Auth user metadata is untrusted trigger and
  recovery input, never authorization data.

## Table contract

| Column | Type | Required | Source |
|---|---|---:|---|
| `id` | `uuid` | Yes | `auth.users.id`; PK/FK with delete cascade |
| `name` | `text` | Yes | Normalized `name` metadata |
| `email` | `text` | Yes | Lowercased `auth.users.email` |
| `phone` | `text` | Yes | Normalized `phone` metadata |
| `cpf` | `text` | Conditional | Normalized `cpf` for person |
| `cnpj` | `text` | Conditional | Normalized `cnpj` for company |
| `active` | `boolean` | Yes | Database default `true`; system-owned |

No other columns are added by this feature.

## Named constraints

Stable names are part of the Repository error-classification allowlist:

| Constraint | Rule |
|---|---|
| `profiles_pkey` | Unique ID and 1:1 relation |
| `profiles_name_normalized` | `name = btrim(name)` and nonblank |
| `profiles_email_normalized` | `email = lower(btrim(email))` and nonblank |
| `profiles_phone_normalized` | 10/11 digits and accepted Brazilian DDD |
| `profiles_exactly_one_document` | Exactly one of CPF/CNPJ is non-null |
| `profiles_cpf_normalized` | Null or 11 digits |
| `profiles_cpf_valid` | Null or valid non-repeated CPF checksum |
| `profiles_cnpj_normalized` | Null or `[0-9A-Z]{12}[0-9]{2}` |
| `profiles_cnpj_valid` | Null or valid official CNPJ checksum |
| `profiles_email_unique` | Normalized email unique |
| `profiles_cpf_unique` | Present CPF unique; multiple nulls allowed |
| `profiles_cnpj_unique` | Present CNPJ unique; multiple nulls allowed |

CPF and CNPJ are text, never numeric. CNPJ accepts both legacy numeric and the alphanumeric format
assigned from July 2026.

## Trigger contract

An `AFTER INSERT` trigger on `auth.users` calls a hardened function that:

1. uses `security definer` and an empty `search_path`;
2. fully qualifies every schema/table/function reference;
3. reads `new.id`, `new.email` and namespaced profile metadata;
4. normalizes name, email, phone and selected document;
5. inserts one same-ID profile, omitting `active`;
6. propagates any failure so the Auth signup transaction is blocked/rolled back.

The trigger function is not callable by `anon` or `authenticated`. Helper validators reside in a
non-exposed schema and expose only the minimum execution needed by table checks.

## Grants and RLS

- Enable RLS on `public.profiles`.
- Revoke all inherited table privileges from `anon` and `authenticated` before granting the minimum.
- `anon`: no table privilege and no policy.
- `authenticated`: SELECT and INSERT only.
- No client UPDATE/DELETE policy or grant.

### Own SELECT policy

Applies only to `authenticated`; row is visible when `(select auth.uid()) = id`.

### Own INSERT policy

Applies only to `authenticated`; insert is accepted only when:

- `(select auth.uid()) = id`;
- normalized row email equals the authenticated JWT email;
- `active` resolves to true;
- all table constraints pass.

The INSERT policy exists for recovery. Normal profile creation occurs in the Auth trigger.

## Idempotency and concurrency

- Trigger primary-key conflict fails the signup transaction; it does not overwrite a profile.
- Recovery first selects, then inserts/upserts with conflict target `id` and ignores duplicate ID.
- Recovery always selects again and confirms consistency.
- CPF/CNPJ conflicts against another ID still fail their named unique constraint.
- Parallel/repeated Android taps are blocked in ViewModel; database PK/unique constraints remain the
  final concurrency boundary.

## Migration boundary

The complete implementation belongs in one ordered migration under
`supabase/migrations/<timestamp>_create_profiles.sql`, including schema helpers, table, constraints,
trigger, grants and policies. `supabase/config.toml` establishes local CLI configuration. Remote
application is deliberate and requires a linked development project; no admin credential belongs
in Android or the migration file.

## Required assertions

- Valid individual/company signup creates exactly one same-ID row with `active=true`.
- Trigger validation failure leaves no committed Auth identity/profile pair.
- Exactly one document is present.
- Numeric and alphanumeric valid CNPJs pass; invalid DVs fail.
- Duplicate normalized email/CPF/CNPJ fails without changing existing data.
- Authenticated users see/insert only their own row; cross-user and anonymous access is denied.
- Recovery of an absent own profile converges to one row under repeated calls.
