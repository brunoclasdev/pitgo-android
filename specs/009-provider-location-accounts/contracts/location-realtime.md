# Contract: Mobile provider current location and Realtime

## Android device-location boundary

`DeviceLocationRepository` exposes:

- a cancellable precise one-shot fix for registration;
- a callback/Flow of high-accuracy fixes for provider tracking;
- no session, provider or appointment knowledge;
- no disk persistence.

Valid provider fix: WGS84, accuracy `<=100 m`, age `<=30 s`. Registration one-shot times out after
15 seconds. Tracking requests best-effort 10-second interval, min interval 10 seconds, no batching
and no minimum distance.

## Foreground service

`ProviderLocationTrackingService` is non-exported, type `location`, `START_NOT_STICKY`. Start Intent
contains only `appointmentId` and action START/STOP; provider identity comes from session/RPC.

Preconditions:

- service started from visible accepted-appointment action;
- session `Authenticated.Provider.Ready` for the same process account;
- Provider MOBILE/active/available;
- FINE granted and location settings enabled;
- appointment snapshot `ACCEPTED` or `IN_PROGRESS` and owned by Provider.

The service immediately promotes itself with an ongoing notification, then collects/publishes. It
observes session/tracking eligibility and stops on every terminal condition. No boot receiver,
WorkManager, background-location permission or persisted queue exists. Offline retains only the newest
valid fix in memory.

Required manifest declarations: COARSE, FINE, FOREGROUND_SERVICE,
FOREGROUND_SERVICE_LOCATION, POST_NOTIFICATIONS and the non-exported service. If notifications are
denied on Android 13+, tracking is visible-screen-only and stops when the screen backgrounds.

## Publish RPC

```text
publish_provider_location(
  p_appointment_id uuid,
  p_latitude numeric,
  p_longitude numeric,
  p_accuracy_m numeric,
  p_captured_at timestamptz,
  p_tracker_instance_id uuid,
  p_tracker_sequence bigint
) -> {outcome:PUBLISHED|IGNORED_OLDER|PUBLISHER_CONFLICT, version, published_at}
```

Server rules:

- derive `auth.uid()`; require PROVIDER owner, MOBILE, active/available;
- require participant appointment in ACCEPTED/IN_PROGRESS;
- accept captured time from `now()-30s` through `now()+10s`, valid point and accuracy <=100;
- advisory lock per appointment;
- same tracker requires higher sequence and nonregressing captured time;
- a different tracker is rejected while current `published_at` is <30 s old, then may take over;
- upsert the one row, increment version and use server `published_at`;
- rejected update never overwrites current data.

`stop_provider_location(appointmentId, trackerId)` deletes only the caller's active row. Database
triggers also delete it when appointment becomes terminal or Provider becomes fixed/inactive/unavailable.

`get_appointment_provider_location(appointmentId)` is participant-only and returns current point,
accuracy/timestamps/version plus server-derived `fresh`; terminal/third-party requests return no data.

## Private Broadcast

Topic: `appointment-location:<appointmentId>`, channel `isPrivate=true`.

An AFTER INSERT/UPDATE/DELETE database trigger emits a minimal payload:

```json
{
  "event": "UPSERT",
  "latitude": -23.5,
  "longitude": -46.6,
  "accuracy_m": 18.0,
  "captured_at": "...",
  "published_at": "...",
  "version": 12
}
```

Delete emits `event=CLEARED` and version/appointment topic context only. No account/provider/client
IDs or company data are broadcast.

RLS on `realtime.messages` permits SELECT/join only when `realtime.topic()` identifies an appointment
whose client or provider account is `auth.uid()` and status is ACCEPTED/IN_PROGRESS. Authenticated
clients have no Broadcast INSERT policy; only the database trigger publishes. Public channel access
is disabled.

Consumer order:

1. call `realtime.setAuth()`;
2. create and subscribe private channel;
3. fetch RPC snapshot after subscription;
4. merge snapshot/events only when version is newer;
5. on reconnect, refetch snapshot;
6. at `published_at+30s`, mark stale locally even without event;
7. remove channel on screen stop/logout/session change.

## Client discovery location

The client map does not use this table/channel. A foreground one-shot client fix is kept in memory
and sent as RPC search center. Permission denial falls back to selected/manual map region. Leaving
the visible map cancels acquisition and clears the point.
