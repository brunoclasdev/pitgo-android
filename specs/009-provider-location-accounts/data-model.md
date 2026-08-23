# Data Model: Contas independentes e localização de prestadores

## Relationship overview

```text
auth.users 1─1 account_roles
   ├─ CLIENT   1─1 profiles 1─N vehicles
   └─ PROVIDER 1─0..1 providers 1─1 addresses
                            ├─N provider_categories N─1 service_categories
                            └─N provider_services   N─1 services N─1 service_categories

profiles 1─N appointments N─1 providers
appointments 1─0..1 provider_current_locations
```

`providers.id` is the stable company identifier. `providers.account_id` is the replaceable Auth
owner. They must never be conflated again.

## Persistent entities

### AccountRole

Table: `public.account_roles`

| Field | Type | Rules |
|---|---|---|
| `user_id` | uuid | PK, FK `auth.users(id)` cascade |
| `account_type` | enum/text | exactly `CLIENT` or `PROVIDER`; immutable to APK |
| `created_at` | timestamptz | server default |

Invariants:

- one Auth identity has exactly one application role;
- CLIENT and PROVIDER are mutually exclusive;
- authorization never reads `raw_user_meta_data` after account creation;
- missing or contradictory context blocks authenticated navigation.

### Profile

Existing table: `public.profiles`. It remains client-only. Its PK is the CLIENT Auth UUID. Provider
signup must not create a row. Existing validation/active semantics remain; profile and vehicle RLS
also require `account_type=CLIENT`.

### Provider

Table: `public.providers`

| Field | Type | Rules |
|---|---|---|
| `id` | uuid | PK, random stable business ID |
| `account_id` | uuid | unique FK Auth owner, role PROVIDER for new/claimed companies |
| `name` | text | trimmed, required, max 150 |
| `description` | text? | trimmed/null, max 2000 |
| `email` | text | normalized lowercase, required, unique, equals Auth email at creation |
| `phone` | text | 10/11 BR digits, valid |
| `cnpj` | text | canonical upper alphanumeric CNPJ, valid, unique |
| `location_mode` | enum/text | `FIXED` or `MOBILE`, explicit |
| `active` | boolean | system-controlled; default true |
| `available` | boolean | provider-controlled through RPC; default true after complete registration |
| timestamps | timestamptz | server-managed |

State derivation:

- Auth PROVIDER without Provider: `ONBOARDING`;
- Auth PROVIDER with exactly one complete Provider: `READY`;
- incomplete/multiple/inconsistent aggregate: blocked;
- a legacy Provider whose `account_id` belongs to CLIENT remains legacy and grants no provider area.

Changing `location_mode` to FIXED or setting inactive/unavailable removes any current location.

### ServiceCategory and Service

Tables: `public.service_categories`, `public.services`.

- both have `id`, normalized `name`, optional `description`, `active`, timestamps;
- `services.category_id` is required and restricts deletion;
- only active category/service pairs appear in registration, Home and discovery;
- Home uses the real service UUID, never a display name or demo enum as identity.

### ProviderCategory

Table: `public.provider_categories`.

Composite PK `(provider_id, category_id)`. Rows are inserted from the distinct categories of selected
services by the registration RPC. At least one is required for a complete Provider. Direct client
DML is revoked.

### ProviderService

Existing table: `public.provider_services`.

Composite uniqueness `(provider_id, service_id)`. Existing `price`, `duration_minutes`, description
and free-service semantics are retained. The service category must also exist in
`provider_categories`; RPC validation enforces this before insert.

### Address

Table: `public.addresses`, exactly one row per Provider.

| Field | Type | Rules |
|---|---|---|
| textual BR address | text | street, number, neighborhood, CEP, city, UF, country=BR; complement optional |
| `latitude`,`longitude` | numeric | required for new/claimed Provider; valid WGS84 |
| `location` | geography(Point,4326) | synchronized canonical spatial point; GiST index |
| `geocode_place_id` | text | Google place identifier |
| `geocoded_at` | timestamptz | coordinate issue/refresh time |
| `geocode_valid_until` | timestamptz | at most 30 days after geocode |
| `location_verified_at` | timestamptz | provider verification time |
| `divergence_distance_m` | numeric? | audit distance only; no device point |
| `divergence_confirmed_at` | timestamptz? | required when distance >500 m |

Discovery requires nonexpired geocode. A refresh that cannot validate the same address before expiry
removes it from discovery without deleting company data.

### GeocodeVerification

Private table: `private.provider_geocode_verifications`.

Short-lived proof created only by the geocoding Edge Function. Contains opaque ID, provider Auth UUID,
hash of normalized address, geocoded point/place ID, geocode time, measured divergence, whether an
override is required, expiry (15 minutes) and consumption time. Device coordinates are never stored.

The registration RPC locks and consumes one proof for the same Auth account/address. Reuse by the
same completed registration is idempotent; use by another account or modified address fails.

### ProviderOwnershipTransfer

Private table: `private.provider_ownership_transfers`.

| Field | Rules |
|---|---|
| `id`, `provider_id`, `source_account_id` | stable transfer identity and legacy ownership |
| `target_account_id` | set only when claimed |
| `token_hash` | SHA-256 of 32-byte random token; plaintext never stored |
| `status` | `PENDING`, `CLAIMED`, `CANCELLED`, `EXPIRED` |
| `expires_at`, timestamps | 15-minute pending window and audit |

Claim updates `providers.account_id` and transfer status in one transaction. Address/categories/
services keep their provider FK and are not copied. Profile is unchanged.

### Appointment

Table: `public.appointments`; minimal authorization anchor, not a complete dispatch feature.

| Field | Rules |
|---|---|
| `id` | uuid PK |
| `client_id` | FK profiles, CLIENT participant |
| `provider_id` | FK providers, PROVIDER participant |
| `service_id` | FK services; must be offered by provider |
| `status` | `PENDING`, `ACCEPTED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED` |
| `accepted_at`,`ended_at`, timestamps | consistent with state |

Only `ACCEPTED` and `IN_PROGRESS` authorize mobile tracking. This feature does not expose appointment
creation/acceptance mutations. A partial unique index permits at most one active appointment per
Provider until fleet/worker assignment exists.

Valid state progression consumed here:

```text
PENDING -> ACCEPTED -> IN_PROGRESS -> COMPLETED
   └───────────────-> CANCELLED <──────────────┘
```

### ProviderCurrentLocation

Table: `public.provider_current_locations`; no history.

| Field | Rules |
|---|---|
| `appointment_id` | PK/FK appointment cascade; one current row |
| coordinates | valid WGS84 |
| `accuracy_m` | positive and <=100 |
| `captured_at` | not older than 30 s or more than 10 s in future at publish |
| `published_at` | server time |
| `version` | monotonically increasing |
| `tracker_instance_id` | UUID for device lease |
| `tracker_sequence` | increasing within tracker |

Freshness is derived, not persisted: `published_at >= now()-30 seconds`. Terminal appointment,
provider fixed/inactive/unavailable or explicit stop deletes the row. A second tracker is rejected
while the current publisher is fresh and may take over after it becomes stale.

### ServiceMapResult

Nonpersistent RPC projection:

- opaque business `provider_id`, display name, location mode;
- business-address latitude/longitude;
- exact service summary, minimum/selected offer price and optional distance;
- no account UUID, CNPJ, email, phone, textual address, client point or mobile current point.

## Application states

### Session

```text
Initializing | Verifying | Unauthenticated | OfflineVerification |
Blocked(reason) |
Authenticated.Client(AccountSnapshot) |
Authenticated.Provider.Onboarding(AuthIdentity) |
Authenticated.Provider.Ready(ProviderSnapshot)
```

Any user change, missing role, CLIENT without valid profile, PROVIDER with contradictory profile or
inconsistent Provider clears visible feature state and blocks protected routes.

### Provider address verification

```text
Unverified -> Geocoding -> AwaitingPrecisePermission -> Locating
  -> Matched(distance <= 500m)
  -> Diverged(distance > 500m) -> ConfirmedDivergence
  -> Failure(reason) -> retry
```

Editing address returns to `Unverified`. Only Matched/ConfirmedDivergence with unexpired proof enables
registration submit.

### Provider registration

```text
LoadingCatalog | Editing | VerifyingLocation | Submitting |
Failure(recoverable) | Success | AlreadyRegistered | SessionBlocked
```

The draft exists only in ViewModel memory. Double submit is ignored. Auth account can remain
Provider.Onboarding after failure, but no partial provider aggregate is considered success.

### Service map

```text
Loading | Content(results, viewport, locationMode) | Empty | Offline | Failure
```

Each request has a generation. Responses for an old service/viewport are discarded. Client location
is in memory only; denial uses manual region/map pan.

### Provider tracking

```text
Stopped | Starting | Active(lastPublishedAt) | Degraded(reason) | Stopping
```

Stop conditions: terminal appointment, fixed/inactive/unavailable provider, session loss/change,
permission loss, location services disabled, publisher conflict or explicit stop. Process death does
not restart silently; the server row becomes stale.

### Appointment marker

```text
Unavailable | Live(location, version) | Stale(location, version) | Ended
```

Broadcast and snapshot merge by `version`; a local timer transitions Live to Stale at 30 seconds even
without another network event.
