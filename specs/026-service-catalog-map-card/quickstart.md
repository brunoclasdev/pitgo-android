# Quickstart: Catálogo de serviços por categoria e mapa PitTec

## Prerequisites

- Supabase local/project environment configured as in the repository.
- An authenticated fixed-provider test account and a client/PitTec test pair.
- Android device or emulator with location permission available.
- Gradle JDK configuration used by the project.

## 1. Validate migration and catalog seed

Use `npx supabase` for Supabase commands. Apply the new forward-only migration only
in a disposable test environment; do not use the linked production database for these
checks. Confirm:

1. The seven initial categories have at most 15 selectable FIXED specialties each; exclude the preserved implicit Guincho/PitTec services from this count.
2. Service names are unique within their category and all initial rows are active as intended.
3. Existing `Guincho` and `PitTec` dispatch kinds remain implicit for Móvel/PitTec registration.
4. Unauthorized users cannot mutate catalog rows; authorized dashboard staff can perform the documented management operations and an audit record is created.

## 2. Validate fixed-provider registration

1. Open provider registration and choose `FIXED`.
2. Select `Borracharia` as the category.
3. Confirm only Borracharia services appear; select two services and fill valid price/duration.
4. Try to add a duplicate service and confirm it is rejected.
5. Complete registration and verify the provider has exactly the two selected offers and one category projection.
6. Deactivate one selected service before submit in a controlled test and confirm submission fails with a catalog-changed message.
7. Repeat with `MOBILE` and `PITTEC` and confirm no manual service selection appears.
8. Register a FIXED provider offering a specialty from Guincho/PitTec; confirm client discovery and contracting use the fixed-service flow, with no automatic dispatch.

## 3. Validate PitTec map presentation

1. Create a PitTec request, accept the quote and approve payment.
2. Open tracking in `ACCEPTED` while the provider is moving.
3. On opening and explicit recenter, confirm available markers and route/estimate fit in the usable area above the compact summary.
4. Tap the summary; confirm details and cancel appear without leaving the map.
5. Collapse it; confirm cancel is hidden and status/distance remain visible.
6. Pan/zoom the map and wait for at least 20 location updates; confirm the viewport is preserved.
7. Move through arrival, `IN_PROGRESS` and terminal states; confirm text/actions update and cancel is unavailable after termination.
8. Record the validation device and observe that location updates do not recreate the map, interrupt pan/zoom or cause redundant catalog queries.

## 4. Build and static checks

```bash
./gradlew assembleDebug
git diff --check
```

Expected result: debug APK assembles successfully, no whitespace errors are reported, and the manual scenarios above pass without changing unrelated flows.
