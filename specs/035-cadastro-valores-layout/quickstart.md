# Quickstart: Cadastro, valores e layout

## Build and static checks

From the repository root:

```bash
git diff --check
./gradlew :app:assembleHomolDebug
```

Apply the additive Supabase migrations to a disposable/local Supabase environment before checking the RPC flows. Do not run live pricing mutations against production. This feature's constitution does not authorize generating a test suite; use the walkthrough below for acceptance validation.

## Acceptance walkthrough

1. Start provider signup and verify PitTec, guincho and loja choices appear before modality-specific forms. Switch choices after entering data and confirm stale exclusive fields are not submitted.
2. Complete PitTec and store registration with a manually typed address. Confirm no map selection or device-location proof screen appears, the exact text is saved, and backend geocoding provides the discovery coordinates. Force geocoding failure and confirm the entered text remains available for retry with a clear error.
3. Complete guincho onboarding with plate, make, model and color. Try each field missing and a duplicate normalized plate; confirm submission is blocked and other form values remain. Verify provider and vehicle are committed together.
4. As manager staff, set PitTec price and duration for one catalog service. Verify two providers offering that service show the same current customer value. Attempt a write as a non-manager/mobile account and verify denial and no change to the active version.
5. Set guincho base amount, distance, PitGo percentage, fee percentage/fixed amount, duration and stored guinchiero price. Verify quote math follows the configured formula, the guinchiero price is excluded, and the breakdown/version/duration are frozen on the quote.
6. Change both configurations while an earlier quote remains valid. Verify the old quote can complete payment/fulfillment from its snapshot and a new quote uses the latest version. Verify a missing/invalid configuration does not yield a default price.
7. Register a store, add a product with required name/description/price and no photo, then add one with a photo. Verify active items appear in the customer-facing store catalog, inactive items do not, another store cannot modify them, and image upload retry preserves the form.
8. Open provider registration and provider dashboard. Verify Fixo/Móvel/Pittec selector buttons are absent, the provider kind is clear, and business/availability actions remain accessible.
9. Open guincho and mobile dispatch address-entry maps. Verify address fields use the floating fixed purple-bordered card, remain reachable during map interaction, and fit compact screens, enlarged text and keyboard-visible states. Confirm maps that only display a provider address do not gain an unnecessary input card.

## Backend safety checks

- Confirm direct authenticated/anonymous writes to pricing tables are revoked and manager RPCs enforce staff access and write an audit record.
- Confirm provider vehicle and store product RLS scope records to the owning provider; verify customer product reads only expose active catalog entries.
- Confirm quote creation reads one active pricing version transactionally and payment/revalidation paths trust the quote snapshot while it is valid.
- Confirm legacy provider rows and existing `provider_services.price`/`duration_minutes` values remain readable and unrelated dispatch paths still operate.

## Implementation validation — 2026-10-22

- Android homol debug APK assembled successfully; output: `app/build/outputs/apk/homol/debug/app-homol-debug.apk`.
- All eight new migrations parsed as SQL and PL/pgSQL.
- Actual migration application and targeted RPC/RLS checks passed in an isolated PostgreSQL/PGlite sandbox with a minimal prior schema and deterministic spatial helpers. Checked central price calculation, immutability across rule changes, staff rejection/audit, store/photo ownership, typed-address/vehicle registration, and STORE readiness without offers.
- Live geocoding, real PostGIS behavior, Supabase Storage uploads, and device layout/acceptance walkthrough remain to be validated against homol after deployment. The sandbox checks do not substitute for these steps.

## Homologação — atualização aplicada

- APK instalado e aberto em A32, A33, A70 e ASUS.
- As oito migrations desta feature foram aplicadas ao projeto homol vinculado com `npx supabase db push --linked --yes`. A conferência posterior com `--dry-run` retornou `upToDate: true`, sem migrations pendentes.
- As tarifas centrais precisam ser cadastradas pelos responsáveis; a publicação não inventa valores iniciais. A validação visual e o roteiro funcional nos aparelhos continuam pendentes.
