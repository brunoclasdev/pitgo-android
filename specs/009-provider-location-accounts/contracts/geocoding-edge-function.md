# Contract: Provider address geocoding

## Runtime boundary

Endpoint: authenticated Supabase Edge Function `geocode-provider-address`.

Authorization: valid PROVIDER session in `ONBOARDING` or owner-edit flow. The function validates the
JWT/account context; no account ID is accepted from the request.

Request:

```json
{
  "address": {
    "street": "Av. Paulista",
    "number": "1500",
    "neighborhood": "Bela Vista",
    "zip_code": "01310200",
    "city": "São Paulo",
    "state": "SP",
    "country": "BR",
    "complement": null
  },
  "device_fix": {
    "latitude": -23.5613,
    "longitude": -46.6559,
    "accuracy_m": 24.5,
    "captured_at": "2026-08-21T15:00:00Z"
  }
}
```

The function accepts only structured Brazilian addresses, high-quality current fixes, fixed Google
endpoint/field mask, language pt-BR and region BR. It never accepts an arbitrary upstream URL.

Success:

```json
{
  "outcome": "VERIFIED",
  "verification_id": "uuid",
  "normalized_address": "Av. Paulista, 1500 - Bela Vista, São Paulo - SP",
  "distance_m": 42.7,
  "requires_confirmation": false,
  "expires_at": "2026-08-21T15:15:00Z"
}
```

Closed failures: `NO_MATCH`, `AMBIGUOUS_OR_PARTIAL`, `WRONG_REGION`, `FIX_TOO_OLD`,
`FIX_TOO_IMPRECISE`, `INVALID_ADDRESS`, `RATE_LIMITED`, `UPSTREAM_UNAVAILABLE`, `SESSION_BLOCKED`.
Raw Google payload, API key and technical error details never reach UI/logs.

The returned ID references a private 15-minute proof. The response intentionally omits canonical
coordinates; registration consumes the proof server-side. A distance over 500 m sets
`requires_confirmation=true`; the UI must show the distance and submit an explicit confirmation.

## Retention and refresh

- Android Maps key: Gradle property `MAPS_API_KEY`, injected only as manifest placeholder, restricted
  to package/signing SHA-1 and Maps SDK for Android.
- Web Geocoding key: Supabase secret, restricted to Geocoding API; never in Gradle/APK.
- Persist place ID and coordinates for at most 30 days from geocode.
- `refresh-provider-geocodes` runs daily and refreshes rows from day 25 using the stored structured
  address/place ID. It uses service credentials only inside the Edge runtime.
- A material coordinate/address mismatch marks the row for reverification rather than silently moving
  a business. Expired rows are excluded from discovery until refreshed/reverified.
- Google attribution and map terms remain visible/obeyed; keys are separated by environment.

## Android state contract

The screen owns permission/settings launchers; ViewModel owns the verification state. Editing any
address field cancels an in-flight request, invalidates proof and clears divergence confirmation.
Device fix lives only in memory and is cleared after success/failure/navigation/session change.
