# Quickstart: validar PitTec sob demanda

Este roteiro valida o desenho e, depois da implementação, a entrega ponta a
ponta. Todos os comandos Supabase destrutivos abaixo são somente para a stack
local.

## Prerequisites

- JDK/toolchain 21 disponível; o app gera bytecode Java 11.
- Android SDK com API 37 e emuladores/dispositivos API 24 e API 36.
- Docker e Supabase CLI compatíveis com o projeto.
- Arquivo local de propriedades do app com a URL/chave pública Supabase e estilo
  MapLibre conforme o README existente. Não versionar valores reais.
- Endpoint HTTPS gerenciado compatível com OSRM Route v1 e suas credenciais
  configurados como Supabase Secrets para a Edge Function.
- Um cliente e pelo menos três prestadores de teste em posições/distâncias
  distintas, todos com oferta ativa do serviço PitTec.

## 1. Static checks

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
./gradlew :app:lintDebug
supabase db lint
deno check supabase/functions/pittec-route/index.ts
```

Expected: commands succeed without secrets in output, manifest conflicts,
serialization warnings or SQL security warnings introduced by PitTec.

## 2. Prepare local backend

Start the isolated local stack, then rebuild its local database:

```bash
supabase start
supabase db reset
```

Do not run `supabase db reset` against a linked/remote production project.

Serve the function with an ignored local environment file:

```bash
supabase functions serve pittec-route --env-file supabase/.env.local
```

Verify through Studio/SQL that:

- `services.dispatch_kind` accepts `PITTEC_AUTO_SEQUENTIAL` while legacy values
  remain valid;
- provider mode accepts `PITTEC` and has nullable pending mode;
- one active PitTec category/service is seeded;
- the home catalog returns the PitTec card even with zero offers;
- `appointment_route_snapshots` has RLS enabled and no direct app-user DML;
- RPC grants are restricted as specified in the contracts.

## 3. Prepare actors

Create/login these independent sessions:

- Client C at a known test point.
- Provider P1 within 10 km.
- Provider P2 farther than P1 but within 10 km.
- Provider P3 inside 25 km (or 50 km for radius expansion).

For every provider, complete the existing provider registration, add an active
PitTec service offer and select modality PitTec. Enable P1–P3 availability and
confirm the foreground tracking notification is visible.

Use synthetic/test coordinates only. Never put real home coordinates in issue
attachments or logs.

## 4. Registration and availability

1. Open provider company registration/editing.
2. Confirm three modality choices: Fixo, Móvel and PitTec.
3. Save PitTec and reopen the screen; the value remains selected.
4. Deny notification permission (API 33+) and try to become available.
5. Expected: availability is not falsely enabled; UI explains how to allow
   PitTec alerts.
6. Grant permission but silence the PitTec channel in system settings.
7. Expected: UI detects the disabled/low channel and offers a settings action.
8. Restore the channel and enable availability successfully.

## 5. Discovery privacy

1. As C, tap the PitTec home card.
2. Expected: the map shows C and nearby anonymous PitTec markers; the button
   `Chamar PitTec` is visible.
3. Compare RPC payload with database points.
4. Expected: response contains no provider identity and marker positions are
   approximate, stable across calls in the same time bucket, and not the exact
   stored values.
5. Disable every provider or make points stale.
6. Expected: the map returns an empty list safely and still explains whether a
   request can be attempted/retried.
7. Attempt direct select of discovery/location tables as C.
8. Expected: RLS denies exact data.

## 6. Automatic sequential dispatch

1. Re-enable P1–P3, tap `Chamar PitTec` once and tap repeatedly while loading.
2. Expected: exactly one search/offer exists; client card shows
   `Buscando PitTec...` with Cancelar and no provider choice.
3. Confirm P1 (the nearest eligible provider) receives the first 60-second
   offer.
4. Reject as P1.
5. Expected: P2 receives the next offer automatically; P1 never receives this
   search again.
6. Let P2 expire according to server time.
7. Expected: P3 receives the next offer without client action.
8. Exhaust all providers.
9. Expected: client sees `NO_PROVIDER` with retry; no candidate cycles.

Repeat with providers placed across 10/25/50 km boundaries and confirm radius
expansion order. Repeat two client requests concurrently against one available
provider; only one valid offer/accept branch may reserve that provider.

## 7. Background and lock-screen alert

1. Make P1 available as PitTec, put its app in background and start a new C
   request.
2. Expected: high-priority notification appears once, sounds according to channel
   settings, reveals no address/name on lock screen and opens the correct offer.
3. Repeat with device locked.
4. Disconnect Realtime while keeping network available, then start an offer.
5. Expected: fallback restore/polling discovers it within about 5 seconds.
6. Reconnect and inject duplicate/out-of-order invalidations.
7. Expected: no duplicate sound and no state rollback.
8. Sign out while the FGS is restarting.
9. Expected: no prior-user offer is restored or displayed.

Documented limitation: this version does not promise delivery after force-stop
or device reboot because it does not introduce FCM.

## 8. Accept, exact map, route and distance

1. Start a request and accept as P1 before its timeout.
2. Expected transaction results:
   - search and appointment are `ACCEPTED`;
   - client request point exists in participant-only current locations;
   - P1's discovery point is no longer client-discoverable;
   - unrelated accounts cannot read participant locations.
3. Expected UI on both devices: exact C/P1 markers, PitTec identified, status
   `A caminho`, distance in m/km and a solid route.
4. Move P1 by less than 50 m or within 15 s.
5. Expected: no unnecessary upstream route request.
6. Move P1 beyond threshold and wait up to 15 s.
7. Expected: a newer shared route snapshot replaces the prior one; map updates
   without continually stealing manual pan/zoom.
8. Stop P1 location updates for more than 30 seconds.
9. Expected: last marker/route remain with an explicit stale warning and are not
   described as real time.
10. Inspect the APK/network configuration.
11. Expected: routing secret is absent from APK and coordinates are not logged.

## 9. Routing failure

1. Configure the local routing endpoint to timeout or return malformed geometry.
2. Accept a request.
3. Expected: attendance remains usable, distance is labeled approximate and a
   dashed direct line is shown.
4. Restore the endpoint.
5. Expected: bounded retry/event refresh replaces fallback with a solid routed
   line; only one current snapshot row exists.

## 10. Arrival and completion

1. While `ACCEPTED`, verify P1 sees `Cheguei` and cannot complete directly.
2. Tap `Cheguei` twice rapidly.
3. Expected: one idempotent transition to `IN_PROGRESS`, one `arrived_at`, both
   UIs show `PitTec chegou`.
4. Tap `Concluir`.
5. Expected: `COMPLETED`, terminal UI for both parties, live location/route rows
   removed and no further tracking updates accepted.

## 11. Cancellation branches

### Before acceptance

Cancel from C while searching/waiting. Expected: pending offer is withdrawn,
provider notification disappears, search becomes terminal, and no reason is
required.

### After acceptance — client

Try blank reason: validation blocks it. Provide a reason and cancel. Expected:
appointment/search end, both participants update, route/locations are removed.

### After acceptance — provider

Provide a reason and cancel as P1. Expected: P1 is excluded and the request
automatically goes to the nearest untried provider. If none remains, client sees
`NO_PROVIDER`.

Race cancellation against accept/timeout in separate sessions. Expected: locks
and revision checks commit exactly one valid branch and every screen converges
after snapshot refresh.

## 12. Deferred modality

1. Accept as a PitTec, then choose Fixo or Móvel during the appointment.
2. Expected: effective mode remains PITTEC and selected value is stored as
   pending; tracking/appointment continues.
3. Complete/cancel.
4. Expected: pending mode becomes effective atomically and is cleared.
5. Verify the inverse case for an active legacy mobile appointment to ensure the
   shared rule does not regress it.

## 13. Resume, compatibility and accessibility

- Kill/recreate the activity (not force-stop) during search and after acceptance;
  the correct active flow is restored.
- Open a stale provider notification; authoritative terminal state routes safely
  to dashboard.
- Exercise legacy Guincho flow; it remains manual and may retain its existing
  cycling semantics.
- Use increased font scale, TalkBack and portrait sizes supported by the app.
  Status/action text remains visible; route vs estimate is not color-only.
- Validate unknown future dispatch kind fails closed with an update message.

## Exit criteria

- Every acceptance scenario in `spec.md` passes on the local stack.
- Builds/lint/SQL/function checks pass.
- Security checks demonstrate no pre-accept exact provider position and no
  non-participant post-accept access.
- Sequential order, non-repetition, timeout, idempotency and concurrent races are
  evidenced with sanitized IDs/timestamps, never coordinates.
- Legacy fixed browsing and mobile Guincho smoke tests pass.
