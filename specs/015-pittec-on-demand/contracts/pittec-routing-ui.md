# Contract: PitTec route and map UI

## Edge Function `pittec-route`

Authenticated `POST /functions/v1/pittec-route`.

### Request

```json
{
  "appointment_id": "uuid",
  "provider_location_revision": 14
}
```

The JWT supplies the user. The revision lets the function short-circuit when a
current or newer snapshot already exists.

### Success response

```json
{
  "status": "ROUTE",
  "appointment_id": "uuid",
  "provider_location_revision": 14,
  "distance_meters": 6840,
  "geometry": {
    "type": "LineString",
    "coordinates": [[-46.6401, -23.5602], [-46.6333, -23.5505]]
  },
  "generated_at": "2026-09-02T15:12:20Z"
}
```

### Conditional/current response

If the stored snapshot has an equal/newer revision and remains usable, return it
with HTTP 200. The client never depends on a 304 body.

### Error response

```json
{
  "status": "UNAVAILABLE",
  "code": "ROUTER_TIMEOUT",
  "retryable": true
}
```

HTTP semantics:

| HTTP | Code | Meaning |
|---:|---|---|
| 400 | `INVALID_REQUEST` | malformed UUID/revision |
| 401 | `AUTH_REQUIRED` | missing/invalid session |
| 403 | `NOT_PARTICIPANT` | caller is not client/provider |
| 409 | `APPOINTMENT_NOT_TRACKABLE` | appointment not accepted/in progress |
| 409 | `LOCATION_NOT_READY` | one exact participant point is unavailable |
| 429 | `RATE_LIMITED` | request too soon; use current/fallback |
| 502 | `ROUTER_FAILURE` | upstream invalid/error response |
| 504 | `ROUTER_TIMEOUT` | upstream deadline exceeded |

## Function behavior

1. Validate JWT and appointment participation through the database.
2. Require `ACCEPTED` or `IN_PROGRESS` and read both exact current points using
   server credentials; never accept coordinates from request JSON.
3. Return a current stored snapshot when it covers the requested revision.
4. Enforce per-appointment rate limit: refresh only when the provider moved at
   least 50 m and the prior route is at least 15 s old. Coalesce concurrent calls.
5. Call configured HTTPS OSRM-compatible Route v1 endpoint with `overview=full`,
   `geometries=geojson` and coordinates ordered `longitude,latitude`.
6. Validate status, finite distance, LineString type, coordinate ranges and a
   configured maximum number of points/body size.
7. Upsert the one current route row if its revision is not older, then return
   normalized JSON.

Configuration is read from Supabase Secrets (for example routing base URL and
credential). It is never sent to or built into the APK. Logs contain request
correlation, latency, status and revision only—not coordinates/geometry/JWT.

## Refresh policy in Android

- Fetch when an appointment first becomes `ACCEPTED`, when no route exists, or
  after a provider location invalidation that meets the movement/time threshold.
- Maintain one in-flight request per appointment. Conflate newer revisions and
  request the latest after completion if necessary.
- Do not clear a valid route merely because a refresh fails; mark it stale and
  retain it until a replacement/fallback is available.
- When the latest provider fix is older than 30 seconds, preserve the last marker
  and route but show an explicit stale-location warning; do not label it live.
- Stop requests and clear in-memory geometry immediately on terminal state or
  sign-out.
- Both participants consume the same stored revision; the provider may be the
  primary refresh trigger, while either participant can request a missing route.

## Fallback

When no routed geometry is available:

- compute haversine distance locally from participant-only exact points;
- draw a direct two-point dashed line;
- label distance as approximate (`~6,8 km`) and expose a semantic description;
- retry only with bounded backoff and normal refresh triggers;
- never block acceptance, arrival, completion or cancellation on routing.

## Map presentation

MapLibre remains the renderer.

| Phase | Markers | Line | Bottom card / actions |
|---|---|---|---|
| discovery | client exact self-marker; anonymous approximate PitTec markers | none | `Chamar PitTec` |
| locating/requesting | client marker; existing approximate markers may remain muted | none | progress: `Buscando PitTec...`; Cancelar |
| provider offered | client sees generic search; provider sees destination only after acceptance | none | client continues progress; provider Accept/Reject/countdown |
| `ACCEPTED` | exact client + accepted PitTec, visually distinct | solid routed line or dashed fallback | distance, `PitTec a caminho`; client/provider cancellation |
| `IN_PROGRESS` | both exact markers | latest route may remain | `PitTec chegou`; provider Concluir/Cancelar |
| terminal | no live participant markers after cleanup | none | terminal result and navigation/retry |

Map behavior:

- Fit both exact markers and route bounds with safe padding after acceptance;
  do not continuously override a user's manual pan/zoom.
- Use a GeoJSON source and MapLibre `LineLayer`; update the source rather than
  recreating the entire map.
- Solid route and dashed estimate must differ by more than color alone.
- Format under 1 km in meters, otherwise localized kilometers with one decimal.
- Approximate discovery markers communicate uncertainty and do not expose a
  tappable provider profile.

## Compose state contract

The PitTec ViewModel exposes one immutable `PitTecUiState` containing:

- permission/location readiness;
- discovery loading/error/markers;
- authoritative dispatch status and revision;
- client/provider marker models permitted for that phase;
- route state, distance label and stale/fallback flag;
- available actions and per-action loading/error;
- one-shot navigation effects separated from durable state.

Composables are stateless and emit events such as `RequestService`,
`CancelSearch`, `RetryDiscovery`, `Accept`, `Reject`, `Arrive`, `Complete` and
`CancelAppointment`. They do not call Supabase, the router or location APIs.

## Navigation/deep links

- Home PitTec card opens the discovery/request route.
- Provider notification carries only appointment ID into `MainActivity`; the
  destination reloads the snapshot before showing details.
- App start/resume restores the active sequential dispatch before accepting a
  new request.
- Missing/terminal appointment deep links route to dashboard/home with a brief
  non-sensitive message.

## Accessibility and privacy

- All markers/actions have localized content descriptions and status is
  announced without rapid repeated live-region updates.
- Loading is represented in text, not only animation.
- Route type (real/estimated) is expressed in text and line pattern.
- Notification/lock-screen text contains no exact address or client identity.
- Screenshots/recents protection follows the application's existing privacy
  policy; this feature does not introduce coordinate logging or analytics.
