# Data Model: Cadastro, valores e layout

## Provider identity

Extend the provider profile with `provider_kind` (`PITTEC`, `GUINCHO`, `STORE`) while retaining existing `location_mode` for operational behavior. New records must have a kind. Existing records remain valid during migration; map `PITTEC` to PITTEC and `MOBILE` to GUINCHO only where product semantics confirm the mapping. Do not silently reinterpret legacy FIXED providers as stores.

The provider registration RPC accepts kind and address text. It stores the exact submitted address and resolves coordinates server-side for discovery. Store registrations may have no service offers; PITTEC and GUINCHO retain their respective current service/discovery requirements. New GUINCHO registrations must include a vehicle.

## Provider vehicle

`public.provider_vehicles`

| Field | Type | Rules |
|---|---|---|
| `id` | uuid | Primary key, generated |
| `provider_id` | uuid | Required FK to provider, cascade on provider removal |
| `plate` | text | Required, normalized uppercase/trimmed, unique among active vehicles |
| `make` | text | Required |
| `model` | text | Required |
| `color` | text | Required |
| `created_at`, `updated_at` | timestamptz | Server-managed |

RLS permits provider owners to manage only vehicles for their own provider record. New guincho onboarding requires at least one complete vehicle. The current feature collects one; the relation allows later edits or fleet expansion without changing the initial flow.

## Store product

`public.store_products`

| Field | Type | Rules |
|---|---|---|
| `id` | uuid | Primary key, generated |
| `store_provider_id` | uuid | Required FK to provider, must reference `provider_kind=STORE` |
| `name` | text | Required, non-blank |
| `description` | text | Required, non-blank |
| `price` | numeric(12,2) | Required, greater than zero; entered by store owner |
| `image_path` | text | Optional path in `store-product-images` bucket |
| `active` | boolean | Defaults true; inactive items hidden from customer reads |
| `created_at`, `updated_at` | timestamptz | Server-managed |

No quantity or stock field is included. RLS permits the owning store to create/read/update/delete its products. Customer reads expose active products only. Object policy binds paths to the authenticated store provider; image is optional. No cart, checkout or order relationship is introduced.

## PitTec pricing

Add a versioned central price configuration keyed by `service_id`, with amount, `duration_minutes`, currency, effective window, active/version metadata and audit timestamps. The active configuration is unique per service and time window. Validate nonnegative/positive amount according to the existing service contract, positive bounded duration, nonoverlapping active windows, and manager authorization before activation.

New quotes resolve price and duration from the central service configuration. Provider offers continue to indicate that a provider supplies a service, but their stored price/duration do not determine the customer amount. Snapshot service ID, pricing configuration ID/version, price, duration, and any quote expiry in the request/appointment record used by the existing flow. Do not rewrite valid or confirmed snapshots when a new version is activated.

## Guincho pricing

Extend the existing versioned `public.guincho_pricing_rules` record rather than introducing another source of truth:

| Data | Use |
|---|---|
| PitGo guincho amount | Fixed base component |
| Distance rule | Existing distance component where applicable |
| PitGo percent | Percentage applied to the same base |
| Other fees percent + fixed | Both calculated from the same base as currently contracted |
| `duration_minutes` | Admin-defined service duration, copied to quote/appointment snapshot |
| Guinchiero price | Stored for future use, excluded from this release's total |
| version/effective metadata | Select current rules and preserve quote provenance |

The total remains base (PitGo amount plus applicable distance) + PitGo percentage + other fee percentage + other fee fixed amount. A quote stores the full breakdown, duration and rule version. Existing valid quote and payment paths use that snapshot and must not reject it solely because the active rule version changed after quote creation.

## Address and migration compatibility

The provider's human-readable address remains the exact typed address. The geocoder result is used only for latitude/longitude and spatial point needed by discovery. A failed geocode returns a retryable validation error and does not create a provider without usable discovery coordinates.

Keep `provider_services.price` and `duration_minutes` nullable/available for legacy rows and unrelated service behavior. New PitTec/guincho flows must ignore those fields for price and duration. Migrations must be additive and preserve existing providers, rules, requests and quote snapshots.
