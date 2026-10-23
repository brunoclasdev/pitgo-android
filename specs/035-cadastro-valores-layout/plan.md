# Implementation Plan: Cadastro, valores e layout

**Branch**: `feature/ajuste-cadastro-layout` | **Date**: 2026-10-22 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/035-cadastro-valores-layout/spec.md`

## Summary

Add an explicit PitTec/guincho/loja choice to provider signup, use the typed business address while resolving coordinates in the background for provider discovery, collect a guincho vehicle, and add a store-owned product catalog. Move PitTec and guincho quote inputs to versioned PitGo-managed configuration protected by the existing dashboard staff authorization and audit model. Preserve existing quote snapshots and provider service price columns for compatibility. Simplify company screens and use one purple-bordered floating address card on address-entry map screens.

Pricing administration will be exposed through secure Supabase RPC contracts for the existing operational dashboard. This repository contains the Android app and Supabase backend but no dashboard frontend, and dashboard staff accounts are distinct from mobile accounts; the plan therefore does not add a staff-only screen to the mobile app.

## Technical Context

**Language/Version**: Kotlin/JVM 11, Android SDK; PostgreSQL migrations and Supabase SQL functions.

**Primary Dependencies**: Existing Jetpack Compose, Navigation Compose, ViewModel/StateFlow, Supabase Kotlin 3.2.6, Ktor 3.3.1, MapLibre 13.5.0, and Coil 3.3.0. No new dependency is planned.

**Storage**: Supabase PostgreSQL and Storage. Add provider kind and provider-owned vehicle/product records; extend versioned guincho pricing and add per-catalog-service PitTec pricing. Supabase Storage will hold optional store product images under owner-scoped policies.

**Testing**: Per constitution, do not generate tests unless requested. Validate with `./gradlew :app:assembleHomolDebug`, `git diff --check`, targeted SQL/RPC permission and quote-snapshot checks, and the manual acceptance walkthrough in `quickstart.md`.

**Target Platform**: Android API 24+ (compile SDK 37, target SDK 36) plus Supabase PostgreSQL backend.

**Project Type**: Android mobile app with Supabase backend; no web dashboard source in this repository.

**Performance Goals**: Keep signup and map screens responsive; do not block signup on map interaction. Resolve coordinates server-side from the typed address before committing a provider profile because discovery requires geographic coordinates. Pricing RPCs must return one active, versioned configuration without per-provider tariff reads.

**Constraints**: Keep typed address text as the canonical profile value; server-side geocoding may derive discovery coordinates but must not require map selection or device-location proof. Protect price writes with `private.dashboard_access_at_least('MANAGER')` and audit them. Quote and appointment snapshots must remain stable when a configuration changes. Preserve legacy provider records and `provider_services.price`/`duration_minutes` columns for compatibility, while excluding provider-entered values from new PitTec/guincho calculations. Do not put staff credentials or service-role keys in Android.

**Scale/Scope**: Existing Android signup, provider registration/dashboard, guincho and PitTec quote flows, store registration/catalog, two map-address screens, and additive Supabase migrations/RPCs. Existing mobile marketplace flows and confirmed quotes remain compatible.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Evidence / decision |
|---|---|---|
| I. Simplicity and requirement fit | PASS | Reuse the existing provider signup, registration, pricing, quote, dashboard staff, and map components. Add only the provider-kind, provider vehicle, store product, and per-service tariff data required by the spec. |
| II. Native Android and MVVM | PASS | Compose screens emit events; ViewModels expose StateFlow; repositories own Supabase calls. Existing dependencies cover the work. |
| III. Predictable state and accessibility | PASS | Explicit loading/success/error states, responsive card layout, readable labels and keyboard/source-size handling are part of the acceptance walkthrough. |
| IV. Data security and explicit failures | PASS | PostgreSQL remains authoritative; owner RLS and staff-only audited RPCs enforce writes. Address geocoding, missing tariffs, upload failures, and unauthorized writes return actionable errors. No privileged key enters the app. |
| V. Proportional quality | PASS | No speculative checkout, inventory, new dashboard app, or dependency. No tests are generated unless requested; compile and manual validation are specified. |

No constitution exception is required.

## Project Structure

### Documentation (this feature)

```text
specs/035-cadastro-valores-layout/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── pricing-dashboard.md
│   └── provider-store-registration.md
└── tasks.md                 # Created by $speckit-tasks
```

### Source Code (repository root)

```text
app/src/main/java/com/trustarx/pitgo/
├── core/navigation/                         # Provider-kind choice and routes
├── features/providersignup/{ui,viewmodel,repository}/
├── features/providerregistration/{ui,viewmodel,model,repository}/
├── features/providerdashboard/{ui,viewmodel,repository}/
├── features/store/{ui,viewmodel,repository,model}/ # New owner catalog and read-only showcase
├── features/guincho/{ui,viewmodel,repository,model}/
├── features/pittec/{ui,viewmodel,repository,model}/
└── core/ui/                                 # Reuse existing design system for address card

supabase/migrations/                         # Additive schema, RLS, quote and staff RPC changes
supabase/functions/                          # Reuse/adjust provider address geocoding where needed
```

**Structure Decision**: Keep the Android app organized by existing features and place all persistent business rules and access control in Supabase. Add a store feature because there is no current store/product feature. Add migrations and RPC contracts in Supabase; do not create a separate backend or dashboard frontend. The existing external dashboard can consume the manager RPCs described in `contracts/pricing-dashboard.md`.

## Complexity Tracking

No constitution violations or exceptions.
