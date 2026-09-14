---

description: "Task list for applying PitGo launcher icon and startup splash branding"
---

# Tasks: Aplicar identidade visual do app

**Input**: Design documents from `/specs/021-app-branding-assets/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/ui-branding.md`, `quickstart.md`

**Organization**: Tasks are grouped by user story so each branding surface can be implemented and validated independently.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm supplied assets and establish the Android resource inputs.

- [X] T001 Confirm `pitgo-icon.png` and `pitgo-image1200.png` exist in the supplied `imagens/` input directory and record their dimensions/transparency before copying them
- [X] T002 [P] Stage `pitgo-image1200.png` as the source splash resource at `app/src/main/res/drawable-nodpi/pitgo_image1200.png`
- [X] T003 [P] Stage `pitgo-icon.png` as the source launcher artwork at `app/src/main/res/drawable-nodpi/pitgo_icon.png`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Prepare shared startup/resource configuration before either branding surface is integrated.

- [X] T004 Verify the staged PNG resources are included by the Android resource merger and do not exceed practical APK/startup size expectations in `app/build.gradle.kts`
- [X] T005 Confirm the existing manifest entry `android:icon="@mipmap/ic_launcher"` in `app/src/main/AndroidManifest.xml` remains the single launcher contract
- [X] T006 [P] Define shared startup background/color resources in `app/src/main/res/values/colors.xml` or the existing theme resource location, keeping light and dark themes readable

**Checkpoint**: Source assets and shared startup resources are available; user story work can proceed independently.

---

## Phase 3: User Story 1 - Ícone do aplicativo (Priority: P1) 🎯 MVP

**Goal**: Show the complete official PitGo icon in launcher surfaces while respecting adaptive-icon safe areas.

**Independent Test**: Install a clean APK and an update APK, then verify the launcher, recent-apps entry, and app-info icon on supported Android devices.

### Implementation for User Story 1

- [X] T007 [US1] Replace the adaptive launcher foreground reference in `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` with the staged PitGo artwork while preserving a safe-area presentation
- [X] T008 [P] [US1] Replace the adaptive round launcher reference in `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` with the same complete PitGo artwork and safe-area behavior
- [X] T009 [P] [US1] Update legacy launcher resources in `app/src/main/res/mipmap-mdpi`, `mipmap-hdpi`, `mipmap-xhdpi`, `mipmap-xxhdpi`, and `mipmap-xxxhdpi` so non-adaptive launchers resolve the same PitGo artwork
- [X] T010 [US1] Remove or stop referencing the generated placeholder artwork in `app/src/main/res/drawable/ic_launcher_foreground.xml` and `app/src/main/res/drawable/ic_launcher_background.xml` when it is no longer part of the final launcher path
- [X] T011 [US1] Build and install the APK from `app/build/outputs/apk/debug/app-debug.apk`, then validate clean-install and upgrade icon behavior on Android devices using `specs/021-app-branding-assets/quickstart.md`

**Checkpoint**: The launcher icon is independently complete and testable without the splash story.

---

## Phase 4: User Story 2 - Splash com imagem oficial (Priority: P1)

**Goal**: Display the complete centered PitGo splash image for at least 500 ms until the first functional screen is ready.

**Independent Test**: Cold-start the app on API 24+ devices with different screen sizes/densities and verify the image, timing, transition, and fallback behavior.

### Implementation for User Story 2

- [X] T012 [US2] Add the AndroidX Core Splashscreen dependency/version entry only if required for API 24+ compatibility in `gradle/libs.versions.toml` and `app/build.gradle.kts`
- [X] T013 [US2] Configure the startup theme and splash image/background resources in `app/src/main/res/values/themes.xml`, `app/src/main/res/values-night/themes.xml`, and any required `app/src/main/res/values-v31/` resources
- [X] T014 [US2] Integrate splash installation and startup readiness handling in `app/src/main/java/com/trustarx/pitgo/MainActivity.kt`, preserving the existing Compose theme/navigation setup and enforcing the 500 ms minimum without an indefinite delay
- [X] T015 [US2] Configure the startup theme reference in `app/src/main/AndroidManifest.xml` so Android 12+ and API 24+ use the same branded startup contract
- [X] T016 [US2] Add a non-empty visual fallback for missing/unreadable splash content in `app/src/main/res/drawable` and ensure startup proceeds without a crash or blank screen
- [X] T017 [US2] Build, install, cold-start, and validate splash image scaling, minimum duration, transition, fallback, and multi-density behavior using `specs/021-app-branding-assets/quickstart.md`

**Checkpoint**: The splash story is independently complete and does not change backend, authentication, or navigation behavior.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Verify the combined branding result and preserve existing application behavior.

- [X] T018 [P] Review launcher and splash resources for accessibility/readability in light and dark system themes using `specs/021-app-branding-assets/contracts/ui-branding.md`
- [X] T019 Run `./gradlew assembleDebug` and inspect the packaged APK resources for both branding files
- [X] T020 Run the complete quickstart validation on the three connected devices and confirm no regression in login, home, client, or Pittec startup flows
- [X] T021 Update `specs/021-app-branding-assets/data-model.md` or `specs/021-app-branding-assets/quickstart.md` only if the final Android resource layout differs from the planned contract

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies; must confirm the two input PNGs before resource work.
- **Foundational (Phase 2)**: Depends on Setup; blocks both user stories.
- **User Story 1 (Phase 3)**: Depends on Foundational; can proceed independently of User Story 2.
- **User Story 2 (Phase 4)**: Depends on Foundational; can proceed independently of User Story 1.
- **Polish (Phase 5)**: Depends on the completed stories selected for delivery.

### User Story Dependencies

- **US1**: No dependency on US2 after Phase 2; recommended MVP because it changes only launcher resources.
- **US2**: No dependency on US1 after Phase 2; may reuse shared startup colors from T006.

### Within Each User Story

- Resource inputs must exist before references are changed.
- Configuration changes precede build/install validation.
- The story checkpoint must pass before treating that story as complete.

### Parallel Opportunities

- T002 and T003 can run in parallel after T001.
- T006 can run in parallel with T004 and T005.
- T008 and T009 can run in parallel with T007 when they touch separate resource files.
- T012 and T013 can be prepared in parallel, but T014 depends on the chosen dependency/configuration.
- US1 and US2 can be implemented in parallel after Phase 2 when separate developers are available.

---

## Parallel Example: User Story 1

```text
Task T007: Update adaptive launcher foreground resource
Task T008: Update adaptive round launcher resource
Task T009: Update legacy density launcher resources
```

## Parallel Example: User Story 2

```text
Task T012: Resolve splash compatibility dependency
Task T013: Prepare startup themes and image resources
```

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 and Phase 2.
2. Complete User Story 1.
3. Install the APK and validate launcher behavior independently.
4. Continue to User Story 2 after the icon is confirmed.

### Incremental Delivery

1. Stage and verify the supplied assets.
2. Deliver the launcher icon as the smallest visible branding increment.
3. Add the branded splash with the same resources and validate cold starts.
4. Run cross-cutting validation on clean installs and upgrades.

## Notes

- No automated test tasks were generated because the feature specification does not request TDD or new automated tests; validation follows the runnable quickstart and device workflow.
- If either PNG is still absent, stop at T001 and request the assets rather than creating a substitute.
