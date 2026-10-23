# Research: Cadastro, valores e layout

**Date**: 2026-10-22

## Decisions

### Provider kind and registration compatibility

- Persist a commercial `provider_kind` (`PITTEC`, `GUINCHO`, `STORE`) independently from the legacy operational `location_mode` (`PITTEC`, `MOBILE`, `FIXED`). Map new guincho registrations to the existing `MOBILE` dispatch path. Keep existing provider rows valid; derive a legacy kind only for display or migration where a mapping is unambiguous.
- Put the new provider-kind choice before the current provider credentials/registration flow. The existing auth profile can remain `account_type=PROVIDER`; pass the selected kind through signup and into the registration RPC. Switching kind clears fields that are not part of the chosen flow.
- The repository has no store or product schema, UI, or navigation. Create a minimal store registration and product catalog feature; do not add checkout or stock management.

**Evidence**: `features/providersignup/ui/ProviderSignUpScreen.kt` hardcodes provider account type; `core/navigation` has no provider-kind selection. `features/providerregistration` currently models FIXED/MOBILE/PITTEC and sends service offers. `public.create_provider_registration(jsonb,jsonb,jsonb)` currently accepts only those operational modes and requires geocode proof and offers. Existing dispatch uses MOBILE for guincho. Client-owned `public.vehicles` references `profiles.id`, so it is not an appropriate owner link for provider vehicles, which are tied to auth-backed provider rows.

### Typed address and provider discovery

- Remove map/address selection and device-location proof from provider signup, but retain server-side geocoding of the typed address to produce the coordinates needed by provider discovery. The exact typed string remains the canonical saved address and is not replaced by a geocoder result.
- If geocoding cannot produce usable coordinates, do not create a provider profile that cannot participate in discovery; return an actionable error and preserve the form values for retry. An incomplete address can remain editable, but submission cannot succeed until discovery coordinates can be resolved.

**Evidence**: The current provider registration validator requires a one-use address proof token; `geocode-provider-address` supplies address verification; registration stores `latitude`, `longitude`, and `address_point`. `provider_geography_discovery` and dispatch use provider coordinates. The spec's initial assumption that coordinates need not be produced conflicts with this backend contract and must be updated to automatic geocoding without map selection.

### Central pricing and quote snapshots

- Keep provider-submitted `provider_services.price` and `duration_minutes` columns for existing records and compatibility. Remove them from new PitTec and guincho registration inputs and from the current PitTec/guincho customer quote calculations.
- Add versioned PitTec price/duration configuration keyed by service catalog ID. Candidate providers offering the same service receive the same central price and duration.
- Extend existing versioned `public.guincho_pricing_rules` with the PitGo base amount and duration, while retaining the guincheiro price as a stored field. Reuse its existing distance, commission percentage, fee percentage and fee-fixed calculation contract rather than introduce a parallel active-rule table.
- Change all quote creation/revalidation/payment checks to use the selected central configuration and frozen quote values. PitTec candidate selection/offer creation must use central price before provider acceptance. Existing snapshots stay unchanged after a new rule is activated.
- Price and duration writes go through manager-authorized `SECURITY DEFINER` RPCs, call `private.dashboard_access_at_least('MANAGER')`, validate ranges/effective version, and record the change with `record_dashboard_action`. Revoke direct table writes. The authenticated Android app reads applicable values through restricted read RPCs; the external dashboard is outside this repository.

**Evidence**: `public.pittec_pricing_rules` and `private.pittec_calculate_pricing` exist; `private.advance_pittec_dispatch` selects provider offers, and PitTec requests/appointments keep quote values. `public.guincho_pricing_rules` is already versioned and `public.store_guincho_quote` currently sources the offer price; `public.guincho_quotes` stores a quote snapshot. `public.reserve_order_payment` and guincho revalidation/payment code compare quotes with active configuration and need compatibility review so a later rule change cannot invalidate a still-valid quote. Staff authorization and audit primitives are in migrations `20260928050000_dashboard_staff.sql` and `20260928060000_dashboard_audit_log.sql`.

### Store catalog and vehicle ownership

- Add `provider_vehicles` referencing the authenticated provider record, with plate, make, model and color; enforce one or more valid vehicles for new GUINCHO providers. Normalize plate case/whitespace before uniqueness checks.
- Add `store_products` owned by a provider of kind STORE, with name, description, positive price, optional image object path, active flag and timestamps. Do not add stock or ordering entities.
- Store product CRUD is owner-only. Customer-facing reads expose active products only. Keep images in a dedicated `store-product-images` bucket with owner-scoped upload/update/delete policy; serve read access through the product read contract (signed URLs or a public read policy consistent with product visibility).

**Evidence**: No store/product feature or product image bucket exists. Existing `vehicles` is client garage data; reusing it would couple provider registration to client profile identity. Existing Supabase Storage use is private inspection media and provides no product image contract.

### Company and map UI

- Remove Fixo/Móvel/Pittec mode selectors from both provider registration's “Área da empresa” and the provider dashboard. Display the selected business kind as read-only and preserve availability, business data, and dashboard actions. Mode remains persisted for compatibility.
- Extract/reuse a purple-bordered floating address card for actual address-entry maps: `features/guincho/ui/GuinchoMapScreen.kt` and `features/mobiledispatch/ui/ClientMobileDispatchScreen.kt`. Do not add it to `features/providermap/ui/ServiceMapScreen.kt` if that screen only displays a provider marker/address and does not collect an address.
- Keep the card anchored over the map while panning, constrain height for compact screens and keyboard, and allow its contents to scroll where necessary.

**Evidence**: Mode controls are present in `ProviderRegistrationScreen.kt` and `ProviderDashboardScreen.kt`. `GuinchoMapScreen.kt` currently places address fields in a scrollable column beneath its map. `ClientMobileDispatchScreen.kt` is another map with address entry; `ServiceMapScreen.kt` is a provider map without address editing. Existing shared Material/design components and outlined-card patterns can provide the border and surface treatment without a dependency.

## Alternatives considered

| Decision | Alternative | Reason not selected |
|---|---|---|
| Store commercial kind separately from `location_mode` | Reuse FIXED as STORE | `location_mode` drives dispatch/location behavior; using it for commercial identity conflates separate concepts and makes legacy migration ambiguous. |
| Geocode typed address in the backend | Save only text and omit coordinates | Existing provider discovery requires coordinates; rows without coordinates would disappear from dispatch. |
| Extend guincho rules and add per-service PitTec rules | Replace all pricing tables in one redesign | Existing guincho rules and snapshots already support versioning, fees and audit-compatible updates; replacing them increases risk without helping current requirements. |
| Expose pricing through existing dashboard staff RPC pattern | Add staff login/screens to Android | Dashboard identities are separate from mobile accounts and there is no dashboard source project in this repository. |
| Add a provider-owned vehicle table | Reuse client garage `vehicles` | Client vehicle ownership references profiles, not the provider registration ownership model. |
