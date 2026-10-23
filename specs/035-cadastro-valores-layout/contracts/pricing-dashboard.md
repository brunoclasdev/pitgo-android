# Pricing Dashboard Contract

These contracts extend the existing Supabase dashboard staff API. Dashboard staff are not mobile client/provider accounts. Every write is authenticated through the existing staff session and is audited; the app never receives a service-role credential.

## PitTec configuration

Manager write RPC accepts `service_id`, `price`, `duration_minutes`, optional currency/effective timestamp, and an idempotency/request key. It validates that the catalog service exists, numeric values and duration are within configured bounds, and the caller satisfies `private.dashboard_access_at_least('MANAGER')`. It creates a new immutable version, retires/replaces the currently active version atomically, and records `record_dashboard_action` with actor, target service and version. It never updates provider-entered offer prices.

Read RPC returns one current configuration per requested service, including configuration ID/version, amount, duration, currency and effective time. Mobile users can read applicable active values but cannot access inactive administrative data or write any pricing table directly.

## Guincho configuration

Manager write RPC accepts PitGo guincho amount, applicable distance parameters, PitGo percentage, other-fee percentage and fixed amount, guinchiero price (stored only), duration, and effective time. It validates percentages and monetary values, enforces one active version, uses the staff authorization helper, and records an audit entry. Direct table writes remain revoked.

Quote RPC returns a snapshot with rule ID/version, base amount, distance component, PitGo percentage amount, other fee percentage and fixed components, customer total, duration and currency. The guinchiero price may be included in the administrative rule response but is excluded from the customer quote calculation.

## Error behavior

Use stable database error codes for `STAFF_REQUIRED`, `INVALID_PRICING`, `SERVICE_NOT_FOUND`, `ACTIVE_RULE_CONFLICT`, and `IDEMPOTENCY_CONFLICT`. Validation failures leave the active version unchanged. Reads with no active configuration return an explicit unavailable result; clients must not synthesize a default amount.

## Consistency requirements

- Quote creation and rule selection happen in one transaction against a single active version.
- Any still-valid or confirmed quote is charged/fulfilled from its stored snapshot, even if a newer rule is active.
- PitTec candidate selection and offer amount use the same central price and duration version.
- Preserve existing RPC callers or update all callers in the same migration sequence; do not expose direct writes as a compatibility workaround.

## Implemented RPC signatures

- `set_dashboard_pittec_pricing(p_service_id uuid, p_request_id uuid, p_price numeric, p_duration_minutes integer)` creates/activates an immediate version and returns it. The request UUID makes retries idempotent.
- `set_dashboard_guincho_pricing(p_request_id uuid, p_config jsonb)` creates/activates a version. Required config keys: `base_amount`, `duration_minutes`, `distance_rate_per_km`, `markup_percent`, `fee_percent`, `fee_fixed_amount`, `guincheiro_price`.
- `get_dashboard_provider_pricing(p_service_id uuid default null)` returns the latest configuration history to MANAGER or higher, including the reserved driver price.
- `get_current_service_pricing(p_service_id uuid default null)` exposes active values to authenticated mobile accounts without the reserved driver-price field.

The new configuration tables start without invented central amounts. A manager must configure tariffs before new quotes can be created. Applying migrations does not replace legacy provider price values or rewrite existing quotes.
