# Contract: PitTec discovery and dispatch RPCs

## General rules

- Functions are exposed to `authenticated` only and derive identity from
  `auth.uid()`.
- Mutations run in one database transaction, lock the current search/appointment
  and accept `expected_revision` where an existing entity is changed.
- Retrying the same `request_id` is idempotent and returns the authoritative
  result instead of duplicating rows.
- Domain errors use stable codes; localized text belongs to Android.
- Coordinates are finite WGS84 values. `captured_at`, accuracy and freshness are
  validated server-side.

## `list_nearby_pittecs`

Lists approximate, anonymous positions for the pre-request map.

### Input

```json
{
  "p_service_id": "uuid",
  "p_latitude": -23.55052,
  "p_longitude": -46.63331,
  "p_accuracy_meters": 18.0,
  "p_captured_at": "2026-09-02T15:00:00Z",
  "p_limit": 200
}
```

### Output

```json
{
  "markers": [
    {
      "marker_id": "opaque-string",
      "latitude": -23.551,
      "longitude": -46.635,
      "accuracy_radius_meters": 500
    }
  ],
  "generated_at": "2026-09-02T15:00:01Z",
  "search_radius_meters": 50000
}
```

### Rules

- Caller must be an active client; service must use
  `PITTEC_AUTO_SEQUENTIAL`.
- Consider only currently eligible PitTecs with fresh discovery location.
- Apply stable server-side approximation before returning any point.
- Return at most `min(max(p_limit, 1), 200)` markers within 50 km.
- Never return provider ID/name, exact distance, price, exact location or direct
  contact.
- Empty `markers` is a successful result, not an error.

## `start_pittec_dispatch`

Starts a request and immediately offers it to the nearest eligible untried
provider.

### Input

```json
{
  "p_request_id": "uuid",
  "p_service_id": "uuid",
  "p_latitude": -23.55052,
  "p_longitude": -46.63331,
  "p_accuracy_meters": 18.0,
  "p_captured_at": "2026-09-02T15:00:00Z"
}
```

### Output: offered

```json
{
  "search_id": "uuid",
  "appointment_id": "uuid",
  "dispatch_kind": "PITTEC_AUTO_SEQUENTIAL",
  "status": "AWAITING_PROVIDER",
  "revision": 1,
  "offer_expires_at": "2026-09-02T15:01:01Z",
  "available_actions": ["CANCEL"]
}
```

### Output: no provider

```json
{
  "search_id": "uuid",
  "appointment_id": null,
  "dispatch_kind": "PITTEC_AUTO_SEQUENTIAL",
  "status": "NO_PROVIDER",
  "revision": 1,
  "offer_expires_at": null,
  "available_actions": []
}
```

### Transactional behavior

1. Validate caller, service, coordinate and absence of another active PitTec
   search for the client. Legacy service simultaneity is not changed.
2. Reuse the entity for the same `p_request_id`, if already committed.
3. Insert search with private request point.
4. Select eligible providers by exact distance in 10/25/50 km order, locking
   availability as needed and excluding all prior candidates.
5. Insert candidate and `PENDING` appointment, set expiration to server time +
   60 seconds, then publish a private invalidation event after commit.
6. If none exists, commit `NO_PROVIDER`.

The user does not choose a provider and there is no intermediate
`OPTION_FOUND` result for PitTec.

## `advance_pittec_dispatch`

Private/security-definer helper, not granted directly to app users. Called by
start, rejection, timeout and provider cancellation.

It marks the current candidate terminal exactly once, increments revision and
selects the next untried provider. Concurrent calls serialize on the search;
only one can create the next appointment. An expired worker or late response
must not close a newer appointment.

## `respond_to_mobile_dispatch` (extended existing contract)

### Input

```json
{
  "p_request_id": "uuid",
  "p_appointment_id": "uuid",
  "p_action": "ACCEPT",
  "p_expected_revision": 3
}
```

`p_action` is `ACCEPT` or `REJECT`.

### PitTec branch

- Only the assigned provider may respond to a non-expired `PENDING` offer.
- `ACCEPT` atomically sets appointment/search to `ACCEPTED`, records acceptance,
  copies the client's request point to `client_current_locations` and removes
  the provider's exact discovery row. Both participants receive invalidation
  events after commit.
- `REJECT` marks the candidate/appointment and calls the non-repeating advance
  helper.
- A response after expiration triggers/returns the already advanced state and
  cannot accept the expired offer.
- Existing `MOBILE_SEQUENTIAL` behavior remains unchanged.

## `expire_pittec_offer`

Backend/scheduled helper. Given appointment ID and expected revision, it expires
only a still-`PENDING` appointment whose server expiration has passed, then
advances the search. It is safe for polling clients to trigger an authorized
wrapper, but client clocks never decide expiration.

## `get_my_active_pittec_dispatch`

Returns at most one active PitTec dispatch for the caller as client or provider.
It may share the existing repository transport internally, but has an explicit
PitTec database contract so a simultaneous legacy flow is never restored by
mistake. Guarantees:

- `dispatch_kind` in every response;
- `revision`, offer expiry and actor-specific available actions;
- PitTec semantic label derived from state (`SEARCHING`, `A_CAMINHO`, `CHEGOU`);
- for the offered provider, approximate distance and coarse region only. Use
  saved client city/state when available, never street/number; otherwise return
  a generic distance band;
- participant profile only from `ACCEPTED` onward;
- no location coordinates in the dispatch snapshot.

Unknown future `dispatch_kind` values fail closed with `UNSUPPORTED_DISPATCH`,
not as `BROWSE`.

## `cancel_pittec_search`

Used by the requesting client before acceptance.

### Input

```json
{
  "p_request_id": "uuid",
  "p_search_id": "uuid",
  "p_expected_revision": 2
}
```

### Rules

- Accept only `SEARCHING` or `AWAITING_PROVIDER` owned by the client.
- Cancel the pending appointment/candidate, make the search terminal and publish
  invalidation to an offered provider.
- A reason is not required before acceptance.
- Repeated cancellation returns the terminal snapshot.

## `arrive_pittec_appointment`

Provider confirms “Cheguei”.

### Input

```json
{
  "p_request_id": "uuid",
  "p_appointment_id": "uuid",
  "p_expected_revision": 5
}
```

### Rules

- Assigned provider only; PitTec dispatch only; status must be `ACCEPTED`.
- Atomically change appointment/search to `IN_PROGRESS`, set `arrived_at` once,
  increment revision and notify both participants.
- Retry returns the same `IN_PROGRESS` snapshot.

## `complete_mobile_appointment` (extended existing contract)

For `PITTEC_AUTO_SEQUENTIAL`, only the assigned provider may complete and status
must be `IN_PROGRESS`. The transaction sets `COMPLETED`, records `ended_at`,
deletes participant locations and route snapshot, applies pending modality and
publishes invalidation. Preserve existing dispatch-kind behavior.

## `cancel_mobile_appointment` (extended existing contract)

### Input

```json
{
  "p_request_id": "uuid",
  "p_appointment_id": "uuid",
  "p_reason": "Imprevisto no deslocamento",
  "p_expected_revision": 6
}
```

### PitTec rules

- Caller must be the client or assigned provider and status must be `ACCEPTED`
  or `IN_PROGRESS`.
- Trimmed reason is mandatory and stored according to existing cancellation
  audit policy.
- Client cancellation makes the search terminal `CANCELLED`.
- Provider cancellation closes the current appointment, excludes that provider
  permanently from the search and advances to the next eligible untried PitTec.
- Cleanup route/live locations before changing provider; copy the request point
  again only after the next provider accepts.

## Error codes

| Code | Meaning / UI behavior |
|---|---|
| `AUTH_REQUIRED` | session ended; return to sign-in |
| `CLIENT_REQUIRED` | account cannot request PitTec |
| `PROVIDER_REQUIRED` | account cannot respond/arrive/complete |
| `PITTEC_SERVICE_REQUIRED` | service is absent/inactive/wrong dispatch kind |
| `INVALID_LOCATION` | request a new location fix |
| `STALE_LOCATION` | request a new location fix |
| `ACTIVE_PITTEC_DISPATCH_EXISTS` | restore the existing PitTec request |
| `OFFER_EXPIRED` | refresh snapshot; provider must not see accept action |
| `REVISION_CONFLICT` | refresh snapshot; do not retry blindly |
| `INVALID_TRANSITION` | refresh and display authoritative state |
| `CANCELLATION_REASON_REQUIRED` | keep dialog open with validation |
| `NO_PROVIDER` | show terminal no-provider state with retry option |
| `UNSUPPORTED_DISPATCH` | fail closed and prompt app update |

## Security acceptance criteria

- Anonymous users cannot execute any RPC.
- A client cannot forge a provider response or read candidate identities.
- A provider cannot inspect a request coordinate until that provider accepts.
- Direct table DML cannot bypass RPC transitions.
- Concurrent accept/timeout/cancel operations produce one valid terminal branch.
- Realtime payloads contain IDs/revision/state only, never coordinates.
