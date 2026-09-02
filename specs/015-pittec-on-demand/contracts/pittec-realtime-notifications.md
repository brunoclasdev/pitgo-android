# Contract: PitTec Realtime and provider alerts

## Objective

Deliver a visible and audible offer to an available PitTec while the app is in
foreground, background or behind the lock screen, without treating Realtime as
the source of truth and without exposing private coordinates in events.

## Availability lifecycle

1. Provider selects effective modality PITTEC and enables availability.
2. Android verifies fine/coarse location according to the existing tracker,
   `POST_NOTIFICATIONS` where required, and that the PitTec offers channel is
   enabled.
3. The existing foreground location service starts/stays active and displays its
   ongoing low-importance tracking notification.
4. The service publishes discovery points and observes the provider's private
   dispatch topic.
5. Disabling availability, losing eligibility or signing out stops observation
   and publication according to the existing lifecycle.

If notification permission/channel is unavailable, the UI must not advertise
successful PitTec availability. It explains the requirement and links to the
appropriate system setting.

## Private topics

Reuse the authenticated private-channel convention already present in the app.
Logical subjects:

- `provider-dispatch:<provider-id>`: offer created/changed/withdrawn;
- `client-dispatch:<client-id>`: search/appointment changed;
- `appointment:<appointment-id>`: accepted appointment state/location/route
  invalidated.

The concrete database channel naming remains compatible with existing migration
helpers. Authorization policies permit only the subject user or appointment
participant.

## Event envelope

```json
{
  "event": "DISPATCH_INVALIDATED",
  "entity_id": "uuid",
  "revision": 7,
  "state": "PENDING",
  "occurred_at": "2026-09-02T15:00:01Z"
}
```

Allowed event classes:

- `DISPATCH_INVALIDATED`
- `APPOINTMENT_INVALIDATED`
- `LOCATION_INVALIDATED`
- `ROUTE_INVALIDATED`

Events are hints. They contain no latitude/longitude, geometry, reason text,
name, phone, address or access token.

## Source of truth and recovery

- On service start/restart, foreground entry, Realtime reconnect and event,
  fetch the authoritative active-dispatch snapshot.
- Ignore an event whose revision is older than the latest applied revision.
- While the provider is available and Realtime is disconnected or suspected
  stale, poll the snapshot every 5 seconds with a single in-flight request and
  bounded retry/backoff.
- The database uses server time for the 60-second offer. Android renders a
  countdown derived from `offer_expires_at` but cannot accept after server
  expiration.
- Reconnecting never replays UI side effects from an already-notified
  `(appointment_id, revision)` pair.

## Local notification

Create a dedicated channel:

| Property | Contract |
|---|---|
| stable ID | `pittec_incoming_requests` |
| user-visible name | `Chamados PitTec` |
| importance | HIGH |
| sound | enabled by default using platform/channel sound |
| vibration | enabled where supported |
| lock-screen visibility | private; no client address/name in preview |
| category | call/service request equivalent supported by Android |

Notification content:

- title: `Novo chamado PitTec`;
- body: generic request and remaining response time, without exact address;
- ongoing: false; auto-cancel after action/state change;
- content intent: immutable/update-safe `PendingIntent` to the provider offer,
  carrying only appointment ID;
- notification ID: stable hash of appointment ID so updates replace instead of
  duplicate.

Sound/heads-up behavior is ultimately controlled by Android and the user. The
app checks channel importance before enabling availability and explains when the
channel has been silenced.

## Foreground service behavior

- Generalize the current tracking predicate from MOBILE-only to
  `location_mode in {MOBILE, PITTEC}` while available.
- Keep one FGS rather than starting a second competing location/alert service.
- Preserve `START_STICKY` restoration, but always revalidate authenticated
  account, effective mode and availability before subscribing.
- Never put the Supabase client, repositories or mutable UI state directly in a
  BroadcastReceiver.
- Notification tap opens the existing `MainActivity`, which restores the
  authoritative snapshot and routes to the correct provider screen.
- When an offer ends, cancel its alert and render the new state or next offer.

## UI reactions

| Authoritative state | Provider effect |
|---|---|
| no active offer | dashboard/FGS continues availability silently |
| `PENDING` and unexpired | notification once per revision; offer screen shows Accept/Reject and countdown |
| `ACCEPTED` | cancel alert; open tracking with “A caminho” action set |
| `IN_PROGRESS` | show “Cheguei”; enable Concluir/Cancelar |
| terminal/current offer replaced | cancel obsolete notification and fetch next snapshot |

## Failure behavior

- Realtime unavailable: polling maintains best-effort delivery.
- Network unavailable: retain ongoing availability UI but mark it reconnecting;
  stale discovery location makes the provider backend-ineligible.
- Notification permission/channel disabled: turn availability off or refuse to
  enable it, with an actionable settings prompt.
- FGS start denied by platform: report unavailable state; do not leave backend
  availability true.
- Force-stop/reboot/process permanently killed: no delivery guarantee in this
  version. A future requirement for that guarantee requires push messaging and
  token lifecycle design.

## Verification matrix

Validate on API 24 and API 36 where applicable:

- foreground, background and locked screen;
- permission granted/denied/revoked;
- channel high/silenced;
- Realtime connected/disconnected/reconnected;
- duplicated and out-of-order invalidations;
- offer accepted, rejected, expired and withdrawn while notification visible;
- logout or modality change while subscribed;
- process recreation via `START_STICKY` without leaking a prior user's offer.
