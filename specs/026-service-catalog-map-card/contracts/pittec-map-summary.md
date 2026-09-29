# Contract: Mapa e resumo do acompanhamento PitTec

## State behavior

| Appointment state | Map | Summary default | Available actions |
|---|---|---|---|
| `ACCEPTED` after payment | Main content; exact markers and route/estimate visible | Compact | Expand to reveal details and cancel |
| `IN_PROGRESS` | Main content; latest route may remain | Compact or state-preserving | Expand for current actions |
| `COMPLETED`/`CANCELLED` | Terminal map behavior already defined | Terminal message | No cancel |

## Interaction

- Compact summary exposes status and distance/arrival information.
- Tap on compact summary toggles expanded state without navigation away from the map.
- Expanded summary exposes cancellation only when the backend reports the action as allowed.
- Location updates refresh markers/route but do not overwrite a user-controlled viewport.
- On first opening or explicit recenter, fit available markers and route within the usable map area above the measured compact summary, excluding controls/insets.
- Manual pan/zoom may move markers out of view; location updates must preserve that choice.
- On the validation device, check that location updates neither recreate the map nor interrupt pan/zoom.

## Accessibility

The expanded/collapsed state has a content description and announced status; cancellation has a visible text label and minimum touch target; route type (real/estimated) remains represented by text and line style, not color alone.
