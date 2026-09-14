# UI Branding Contract

## Launcher icon

- Android launcher entry MUST continue resolving `@mipmap/ic_launcher`.
- The resolved asset MUST represent the complete `pitgo-icon.png` within the safe area used by
  adaptive launcher masks.
- Legacy density resources and adaptive resources MUST resolve to the same brand artwork.

## Startup splash

- Startup presentation MUST show `pitgo-image1200.png` centered with preserved aspect ratio.
- The image MUST remain visible for at least 500 ms and MUST be removed once the first functional
  screen is ready.
- Missing/unreadable artwork MUST use a non-empty fallback and MUST NOT block app startup.

## Compatibility

- The contract applies to Android API 24+.
- Light and dark system themes MUST retain a readable background and status/navigation bar treatment
  during startup.
- Client and Pittec profiles MUST use the same branding resources.
