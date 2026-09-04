# Quickstart: Validação da experiência mockada de UI do PitTec

## 1. Build and static checks

From the repository root:

```bash
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
```

Expected: both commands pass without adding dependencies, migrations, Edge Functions, secrets or
test fixtures outside the Android source.

## 2. Test preparation

Prepare:

- one existing authenticated client account whose Home contains the PitTec card;
- one existing authenticated provider account with effective mode PITTEC;
- optionally one FIXED/MOBILE provider for regression checks;
- a device/emulator with controllable precise location;
- Android Studio Network Inspector or equivalent to observe requests.

Authentication, session verification, catalog loading and map tiles may still use their existing
network paths. Do not create or alter Supabase data for these mock scenarios.

## 3. Client initial screen and estimate

1. Log in as the client and open the PitTec card.
2. Expected: the screen before any action keeps its current top bar, map, approximate markers and
   CTA. No estimate/search card is shown yet.
3. Tap “Buscar PitTec”.
4. Expected: a price card appears before search, showing 5 km, R$ 50,00 fixed, R$ 10,00 additional
   and R$ 60,00 total, with “Confirmar” and “Voltar”.
5. Tap “Voltar”.
6. Expected: initial screen returns and no search feedback appears.
7. Open the estimate again and tap “Confirmar” repeatedly.
8. Expected: exactly one search starts; the action disables immediately.

## 4. Progressive search and empty result

Follow the primary deterministic fixture:

1. Observe Procurando and Aguardando resposta as separate textual states.
2. Expected after the first decline: the candidate is not repeated, the UI announces continuity,
   expands from 5 km to 10 km and updates total from R$ 60,00 to R$ 70,00.
3. Expected after the second decline: expands from 10 km to 15 km and updates total to R$ 80,00.
4. Expected on acceptance: found state occurs once and tracking opens with R$ 80,00 frozen.
5. Complete this appointment and start the next deterministic attempt.
6. Expected for the no-provider fixture: every candidate is attempted once, expansion stops when
   fixtures end, and the UI shows an empty state with “Tentar novamente” and “Voltar”.

At every step, verify the same local search ID remains during refusal/expansion and a new ID is
created only on explicit retry.

## 5. Client tracking, camera and stale position

1. On accepted tracking, verify client and PitTec markers are both visible and identified.
2. Expected: the text card shows both participants, estimated distance, 15 km perimeter, R$ 80,00
   and Deslocando-se.
3. Pan/zoom the map manually and wait 30 seconds.
4. Expected within 28–32 seconds: PitTec marker/distance updates, but camera stays where the user
   left it.
5. Tap “Centralizar”.
6. Expected: both current markers are framed again.
7. Hide/stop the experience for more than 35 seconds, then return.
8. Expected: last point is retained and marked stale; the next 30-second step clears the warning,
   without screen reload or external route request.
9. If both points coincide, expected distance is “menos de 100 m” and the textual participant
   list remains unambiguous.

## 6. Bilateral meeting and completion — client

1. Tap “Já nos encontramos” repeatedly.
2. Expected: one local confirmation, disabled action and text saying the PitTec is still pending.
3. Before 3 seconds, state remains awaiting meeting.
4. At approximately 3 seconds, expected: simulated counterpart confirmation appears once and state
   becomes Serviço em andamento.
5. Tap “Finalizar serviço” repeatedly.
6. Expected: one local confirmation; service remains open while waiting.
7. At approximately 3 seconds, expected: state becomes Concluído once, movement jobs stop and
   “Voltar para a Home” appears.
8. Navigate away during either 3-second wait and return.
9. Expected: the current state is preserved and no confirmation/transition is duplicated.

## 7. Provider location gate

1. Log in as the PITTEC provider with location permission denied.
2. Expected: map is not composed, no fake marker appears, and the screen explains that precise
   location is required.
3. Grant only approximate location where the OS supports it.
4. Expected: map remains blocked with an action to allow precise location.
5. Grant precise location but disable device location services.
6. Expected: map remains blocked and “Ativar localização” opens system location settings.
7. Return with services enabled.
8. Expected: permission/services are rechecked automatically; a real fix is requested and, when
   available, the map shows the device position plus the textual Disponível state.
9. Force the fix acquisition to timeout/unavailable.
10. Expected: actionable unavailable state with “Tentar novamente”, never a mock fallback.

## 8. Provider alert and response

1. With provider Home ready, wait for the first fixture or tap “Simular nova solicitação”.
2. Expected: exactly three purple pulses complete within 3 seconds, followed by the same persistent
   purple card with client, perimeter/distance, total, “Aceitar” and “Recusar”.
3. Navigate to another bottom tab and back.
4. Expected: card remains, but pulses do not replay.
5. Tap “Recusar” repeatedly.
6. Expected: only the first action is accepted; alert ends and Home returns to Disponível without
   an appointment.
7. Simulate another request and tap “Aceitar” repeatedly.
8. Expected: one local appointment is created and tracking opens in at most 2 seconds.

## 9. Reduced motion and accessibility

To validate Android animations disabled, save the current device setting, set animator duration
scale to zero, run the incoming request scenario, and restore the original setting afterward. For
an adb-controlled test device this can be done with:

```bash
adb shell settings put global animator_duration_scale 0
```

Expected: no pulse is shown; the persistent purple card and both actions appear immediately. Do
not leave the shared/device setting changed after the test.

Then validate:

- TalkBack announces the new request, statuses, participants, distance, values and waiting party;
- 200% font scale keeps all content reachable through scrolling;
- every action has a meaningful text label and adequate touch target;
- light/dark/system themes preserve contrast;
- map, color, motion and icons are never the only source of required information.

## 10. Provider bottom navigation and last appointment

1. Verify the order Mapa, Dados da corrida, Valores and Perfil.
2. Before accepting any request, open Dados da corrida and Valores.
3. Expected: each shows an explanatory empty state, not a balance/history.
4. Accept and complete a request, then open Dados da corrida.
5. Expected: current/last client, PitTec, perimeter, last distance, state and relevant timestamps.
6. Open Valores.
7. Expected: the same appointment's R$ 50,00 fixed amount, R$ 10,00 × bands, additional amount and
   total; no balance, statement, earnings, payment or historical list.
8. Open Perfil.
9. Expected: basic provider data and existing logout action; selected tab remains identifiable by
   text and indicator.

## 11. State retention boundary

1. During search, incoming request, tracking and each pending confirmation, navigate away and back
   without closing the process.
2. Expected: stage, attempted candidates, alert-consumed flag, last appointment and confirmations
   are restored without replay.
3. Rotate/recreate the Activity without process death.
4. Expected: the host-scoped ViewModel retains the same state.
5. Fully terminate the process and relaunch.
6. Expected: mock state may reset; no DataStore/Room/SavedState restoration occurs.
7. Change/logout the authenticated account.
8. Expected: the next session receives isolated mock state and never sees the former account's
   participants or appointment.

## 12. Prove absence of real effects

Observe network/service activity while performing every PitTec action. Expected:

- no `list_nearby_pittecs`, start/cancel/restore/transition dispatch RPC;
- no PitTec Realtime subscription or polling;
- no provider location publication;
- no route Edge Function request;
- no external/system incoming-request notification;
- no payment, balance or charge action;
- no coordinates or participant details logged.

Existing authentication, catalog and managed map tile/style traffic is allowed and should be
distinguished from feature actions. Confirm an already-running PitTec tracking service is stopped
locally without a server availability mutation.

## 13. Non-regression

1. Log in as FIXED and MOBILE providers.
2. Expected: they still use the existing Provider Dashboard, availability and tracking behavior.
3. Exercise client Home, Profile and a non-PitTec service.
4. Expected: existing bottom navigation and flows are unchanged.
5. Exercise Android back from the client PitTec estimate and tracking screens.
6. Expected: navigation is predictable and no removed real-resume effect pulls the client back.

## Exit criteria

- All acceptance scenarios in `spec.md` are demonstrated.
- R$ 60,00/R$ 70,00/R$ 80,00, rejection order, 3 s confirmations, 30 s movement and 35 s stale
  behavior match the contracts.
- Provider map never shows a simulated fallback when real location is unavailable.
- Recomposition/navigation/repeated taps do not duplicate actions or animations.
- Network inspection proves the prototype has no PitTec business side effects.
- Debug build, lint, themes, font scale and TalkBack checks pass; FIXED/MOBILE do not regress.
