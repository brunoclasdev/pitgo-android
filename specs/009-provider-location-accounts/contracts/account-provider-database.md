# Contract: Supabase account, provider and discovery database

This contract defines the database boundary. Exact SQL belongs in migrations; Android must depend on
closed outcomes, never raw PostgreSQL messages.

## Authoritative account context

`get_my_account_context() -> jsonb`, executable by `authenticated`.

Success shape:

```json
{
  "account_type": "CLIENT",
  "client_state": "READY",
  "provider_state": null,
  "provider_id": null,
  "legacy_provider_migration_available": true
}
```

Provider `provider_state` is `ONBOARDING`, `READY` or `INCOMPLETE`. Exactly one row/context is returned.
Missing role, contradictory rows or unauthorized session use stable outcomes/SQLSTATE and never fall
back to another role.

## Signup gate

`private.validate_account_signup(jsonb)` is the Before User Created Auth Hook. It accepts only
`raw_user_meta_data.account_type` equal to `CLIENT` or `PROVIDER`.

`private.handle_new_user_account()` is the `AFTER INSERT auth.users` trigger. In the Auth transaction:

1. revalidate account type;
2. insert immutable `account_roles`;
3. insert profile only for CLIENT using validated client metadata;
4. insert no provider/profile for PROVIDER.

Execution grants are limited to `supabase_auth_admin`/trigger owner as applicable. `anon` and
`authenticated` cannot execute helpers or mutate roles.

## Provider registration

```text
create_provider_registration(
  p_provider jsonb,
  p_offers jsonb,
  p_geocode_verification_id uuid,
  p_divergence_confirmed boolean
) -> jsonb
```

The RPC derives owner from `auth.uid()` and accepts no account/provider/category owner ID. Provider
payload contains normalized company data and `location_mode`. Offers contain service UUID, price,
duration, description and free flag. Address text/point comes from the locked geocode proof.

Closed outcomes:

- `CREATED {provider_id, offer_count}`;
- `ALREADY_REGISTERED {provider_id, offer_count}` for idempotent retry;
- domain error codes `GEOCODE_EXPIRED`, `DIVERGENCE_CONFIRMATION_REQUIRED`, `CATALOG_CHANGED`,
  `EMAIL_UNAVAILABLE`, `CNPJ_UNAVAILABLE`, `INVALID_INPUT`, `SESSION_BLOCKED`.

All input and catalog validation occurs before inserts. One transaction inserts Provider, Address,
distinct ProviderCategories and ProviderServices. An advisory lock on account UUID plus unique
constraints prevent concurrent duplicates. Existing complete registration is not overwritten.

## Provider settings

`set_my_provider_mode(p_mode)` and `set_my_provider_availability(p_available)` derive provider from
the PROVIDER account. Changing to FIXED or unavailable deletes current location in the same
transaction. They return a sanitized Provider snapshot.

## Legacy ownership transfer

```text
begin_provider_ownership_migration(p_token text) ->
  {outcome:PENDING, expires_at, requirements}

claim_provider_ownership_migration(
  p_token text,
  p_location_mode text,
  p_geocode_verification_id uuid?,
  p_divergence_confirmed boolean
) -> {outcome:MIGRATED|ALREADY_MIGRATED, provider_id}
```

Begin requires CLIENT owner of a legacy Provider. Claim requires a different-email PROVIDER account
without another Provider, locks transfer/provider/source/target, validates completeness/location and
updates only `providers.account_id` plus transfer audit. Same target retry is idempotent. Token expiry,
different claimant or changed source returns a neutral failure.

## Catalog

Catalog reads return only active categories and active services. Pagination continues until a short
page; no product maximum is imposed. Provider selections are validated again during submit.

`list_home_services()` returns real service UUID, category UUID/name, service name/description and
optional minimum active offer price. It does not expose Provider data.

## Discovery

```text
discover_service_providers(
  p_service_id uuid,
  p_center_lat double precision,
  p_center_long double precision,
  p_radius_meters integer,
  p_limit integer
) -> setof ServiceMapResult
```

Rules:

- caller must be CLIENT;
- center must be WGS84; radius 1..200000 m; limit 1..200;
- exact active service/category and both ProviderService and ProviderCategory must match;
- Provider owner role PROVIDER, active and available;
- address geocode is verified and unexpired;
- FIXED and MOBILE both return the business address;
- sort by `ST_Distance`, use `ST_DWithin`/GiST;
- return only the projection in `data-model.md`.

## RLS and grants matrix

| Resource | CLIENT | PROVIDER | anon |
|---|---|---|---|
| own profile/vehicles | own SELECT/RPC mutations | none | none |
| own provider/address/categories/services | none, except migration begin | own SELECT/RPC mutations | none |
| active catalog | SELECT | SELECT | none |
| discovery tables | no direct SELECT; RPC projection | no cross-owner SELECT | none |
| account_roles | own context only | own context only | none |
| private proofs/transfers | RPC only | RPC only | none |

All critical functions use `SECURITY DEFINER SET search_path=''`, fully qualified objects, revoked
`public/anon` execution and explicit `authenticated` grant. The APK never contains service-role.

## Migration reconciliation

Migrations must preflight and abort rather than guess when encountering orphan providers, missing
Auth rows, duplicate ownership, multiple addresses or inconsistent category/service links.

For the known legacy schema:

1. backfill all profile identities as CLIENT;
2. add/backfill `providers.account_id=providers.id` while preserving Provider PK and children;
3. remove only the old Provider-ID-to-Auth FK, add unique Auth ownership FK and random Provider ID default;
4. mark those rows as legacy; do not grant their CLIENT owners provider permissions;
5. replace the all-user profile trigger;
6. add ProviderCategory, modality, active/available and geospatial fields;
7. replace policies/RPCs that currently require `profiles.active`.
