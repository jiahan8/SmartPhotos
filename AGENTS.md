# SmartPhotos

Android app (Kotlin, single `:app` module) for organizing photos/notes with ML-based tagging.
Firebase backend + a Node.js Cloud Functions project live in `functions/`.

## Build, test, lint

Run from the repo root (Gradle wrapper):

- Build debug APK: `./gradlew assembleDebug`
- Unit tests (JVM, Robolectric-backed): `./gradlew testDebugUnitTest`
  - Single class: `./gradlew testDebugUnitTest --tests "com.jiahan.smartcamera.home.HomeViewModelTest"`
  - Single method: `./gradlew testDebugUnitTest --tests "com.jiahan.smartcamera.home.HomeViewModelTest.methodName"`
  - ViewModel `StateFlow`/`SharedFlow` assertions use [Turbine](https://github.com/cashapp/turbine)
    (`.test { ... }`) rather than manually collecting into a list — follow this in new ViewModel
    tests.
- Screenshot tests use Roborazzi and live under `app/src/test/.../screenshot/`. Note that
  `testDebugUnitTest` *runs* them but does **not** diff them against the goldens in
  `app/src/test/screenshots/` — only `./gradlew verifyRoborazziDebug` compares. Run it before
  pushing UI changes; on its own it re-runs the whole suite, so to compare screenshots alone use
  `./gradlew verifyRoborazziDebug --tests "com.jiahan.smartcamera.screenshot.*"` (this is what CI
  does). Re-record with `./gradlew recordRoborazziDebug` when a diff reflects an intended change,
  and look at the new PNGs before committing them.
- Instrumented tests (`app/src/androidTest`) require a device/emulator and run via
  `./gradlew connectedDebugAndroidTest`. They use a custom `HiltTestRunner` (installs
  `HiltTestApplication`), the AndroidX Test Orchestrator, and `clearPackageData=true` for hermetic,
  isolated runs — don't remove these from `app/build.gradle.kts` without understanding why.
  - The same suite also runs on Firebase Test Lab's device farm via `./scripts/run-test-lab.sh`
    (requires the `gcloud` CLI, auth, and the Blaze plan — see the script's header comment).
- Distribute a debug build to testers via Firebase App Distribution: `firebase login` once, then
  `./gradlew assembleDebug appDistributionUploadDebug`. Configured on the `debug` build type in
  `app/build.gradle.kts`; the `testers` group must exist under Firebase console > App Distribution
  first.
- Cloud Functions (`functions/`, Node 24): `npm --prefix functions run lint` (eslint, google config).
  Deploy/emulate with `npm --prefix functions run serve` / `deploy` (requires Firebase CLI auth).

### CI

`.github/workflows/ci.yml` runs on every push to `main`, every pull request, and on demand: it
builds the debug APK, then runs the unit tests, the screenshot comparison and `lintDebug` as three
separate steps on JDK 21, and lints `functions/` in a parallel job. The three steps are split so a
failure says which kind of thing broke, and each runs even if an earlier one failed, so one run
reports every problem rather than revealing them one push at a time.

It needs one repository secret, `GOOGLE_SERVICES_JSON`, because `app/google-services.json` is
gitignored and the Google Services plugin fails the build without it. Generate the value with
`base64 -i app/google-services.json` and paste it into Settings > Secrets and variables > Actions
(a *repository* secret, not an environment one — the job declares no environment).

Each run uploads two artifacts: `unit-test-report` (the full suite) and
`screenshot-and-lint-reports`. When a run fails on a screenshot, download the latter — the
`*_compare.png` files show reference / diff / actual side by side.

Two things to know about the goldens:

- Any screenshot showing a note renders a formatted timestamp, and `Long.toFormattedDateTime()`
  resolves `ZoneId.systemDefault()`/`Locale.getDefault()` at render time. That made the goldens
  machine-dependent — they passed on a UTC+8 laptop and failed on the UTC CI runner, eight hours
  out. `app/build.gradle.kts` now pins the unit-test JVM to UTC/en-US (`tasks.withType<Test>`), so
  goldens recorded on any machine verify on every other one. If you change that pin, re-record.
  More generally: a golden diff that appears only on CI is far more likely to be non-determinism in
  the test than a rendering difference between platforms — check for a clock, locale, or random
  value in the fixture before assuming the environment is at fault.
- `settingsScreen_default.png` renders the app version string, so it goes stale on every version
  bump in `app/build.gradle.kts` and needs re-recording alongside one.

## Architecture

MVVM with a layered structure, one Kotlin package per feature under
`app/src/main/java/com/jiahan/smartcamera/` (e.g. `home`, `note`, `favorite`, `search`, `profile`,
`settings`, `auth`, `preview`). Cross-cutting layers:

- **UI** — Jetpack Compose screens (`*Screen.kt`) + Navigation Compose graph in
  `navigation/SmartPhotosNavGraph.kt` / `navigation/Screen.kt`.
- **ViewModel** — `@HiltViewModel` classes exposing a `*UiState` data class via `StateFlow`. The
  loading/loaded/error branch is a nested sealed sub-type (e.g. `HomeContent` in
  `home/HomeViewModel.kt`), kept separate from flat fields on the outer `*UiState` for orthogonal
  UI state (`isRefreshing`, dialogs, pagination) that shouldn't force a full state-machine branch.
- **Repository** (`data/repository/`) — one interface + one `Default*` implementation per
  repository (e.g. `NoteRepository` / `DefaultNoteRepository`), all bound in
  `data/di/DataModule.kt`. Coordinates Firebase Firestore/Storage (remote) and Room/DataStore
  (local).
- **Domain** (`domain/`) — plain data classes shared across features (e.g. `HomeNote`,
  `MediaDetail`, `User`).
- **Local** — Room database in `database/` (schemas exported to `app/schemas/`), DataStore
  preferences in `data/datastore/`. A note's media list is persisted into the `notes.media_list`
  column as `kotlinx.serialization` JSON keyed by `MediaDetail`'s property names, so those names are
  an on-disk format: renaming one makes already-cached rows undecodable unless you pin the old key
  with `@SerialName`.
- **Remote** — Firebase (Auth, Firestore, Storage, Remote Config, Analytics, Crashlytics, FCM) plus
  Cloud Functions in `functions/index.js` calling Google Cloud Vision API for text/label/object
  detection on uploaded photos.

### Separation of concerns

Each layer above has one job and talks to its neighbors through an interface, not around them:

- Composables render state and forward user intents; they never call Firebase/Room/DataStore
  directly or hold business logic beyond simple derived/UI-only state (e.g. scroll position, sheet
  visibility).
- ViewModels own presentation logic and state transformation; they depend on repository
  *interfaces* (dependency inversion — the SOLID principle easiest to forget under time pressure),
  never on `Default*` implementations or Firebase/Room types directly, so they stay unit-testable
  without a real backend.
- Repositories own data-source coordination (remote as source of truth, Room as a write-through
  cache — writes go to Firestore first, then Room) and
  expose domain models — never Firestore `DocumentSnapshot`/`QuerySnapshot` or Room entities — across
  the repository interface boundary.
- Domain models are plain data with no Android/Firebase/Room dependencies, so they can be shared
  and unit-tested without those frameworks on the classpath.

When adding a feature, prefer extending this layering over reaching across it (e.g. a screen
calling `FirebaseFirestore` directly, or a repository returning a Room `@Entity` to a ViewModel).

### Cross-feature communication

Features that need to react to events in other features use a per-domain `*Handler` singleton
injected via Hilt (e.g. `note/NoteHandler.kt`) exposing `MutableSharedFlow`s (`noteAddedEvent`,
`noteDeletedEvent`, `noteFavoritedEvent`, …). ViewModels emit into these handlers on mutation and
collect them in `init {}` to keep other screens (e.g. Home, Favorite) in sync without a shared
ViewModel — follow this pattern instead of adding direct cross-ViewModel references.

This exists because Home/Favorite hold paginated, in-memory-cached lists rather than a live
reactive query, so a plain Flow-from-repository wouldn't patch an already-loaded page on its own.
Note the tradeoff: a default `MutableSharedFlow` has no replay, so a subscriber must already be
collecting (i.e. its ViewModel must already be constructed and past its `init {}`) when the event
fires, or it silently misses it — don't rely on this pattern for events a screen must never miss.

### Error handling

Route all thrown errors through `util/ErrorHandler` (`logError` then `getErrorMessage`) rather than
reading `Throwable.localizedMessage` directly — this ensures Crashlytics logging in release builds
and consistent user-facing messages via `ResourceProvider`.

### Kotlin Multiplatform readiness

We may migrate parts of this codebase (`domain/`, repository interfaces, other business logic) to
Kotlin Multiplatform down the line. This isn't a mandate to add KMP tooling now, but when choosing
between otherwise-equivalent approaches, prefer the one that keeps that migration cheap:

- Keep `domain/` models and repository *interfaces* free of Android/Firebase/Room types — already
  required by the Separation of concerns rules above, and the main thing that makes this migration
  tractable later.
- Prefer `kotlinx` libraries (`kotlinx.coroutines`, `kotlinx.datetime`, `kotlinx.serialization`)
  over Android- or JVM-only equivalents (e.g. `java.time`, Gson) in shared-leaning code, when a
  choice exists.
- Avoid `android.*` imports leaking into anything below the ViewModel layer.

## Follow official Android guidance

When implementing or reviewing changes, prefer solutions aligned with Google's official Android
guidance over ad-hoc approaches:

- [Android app architecture guide](https://developer.android.com/topic/architecture) — unidirectional
  data flow, `StateFlow`/`UiState` exposed from ViewModels (not events polled by the UI), repositories
  as the single source of truth.
- [Jetpack Compose guidance](https://developer.android.com/develop/ui/compose/documentation) —
  state hoisting, `remember`/`derivedStateOf` for recomposition efficiency, avoiding side effects
  outside `LaunchedEffect`/`DisposableEffect`.
- [Kotlin coroutines & Flow best practices](https://developer.android.com/kotlin/coroutines/coroutines-best-practices) —
  scope coroutines to `viewModelScope`/`lifecycleScope`, inject `CoroutineDispatcher`s rather than
  hardcoding `Dispatchers.IO`, avoid `GlobalScope`.
- [Material Design 3](https://m3.material.io/) for new UI/theming work.
- Kotlin style: follow the [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
  (already enforced here via `kotlin.code.style=official` in `gradle.properties`) — see
  [Kotlin coding conventions](#kotlin-coding-conventions) below for project-specific notes.

If an official recommendation conflicts with an existing pattern in this codebase, follow the
official recommendation and update the existing codebase convention to match it for consistency,
calling out the discrepancy.

### Jetpack Compose state hoisting

- Screen-level composables (`*Screen.kt`) collect `UiState` from the ViewModel (via
  `collectAsStateWithLifecycle()`) and hoist it down as plain parameters/lambdas; child composables
  should stay stateless and never take a ViewModel reference, so they can be previewed and tested
  with plain state.
- Keep `remember`/`mutableStateOf` for state that's genuinely local and ephemeral to composition
  (e.g. IME visibility, an animation trigger); anything that survives navigation or is needed by
  another screen belongs in the ViewModel's `UiState` instead.

## Conventions

- DI: Hilt, all modules `@InstallIn(SingletonComponent::class)`. App-wide bindings live in
  `di/AppModule.kt` / `di/FirebaseModule.kt`; other cross-cutting layers have their own module
  (`data/di/DataModule.kt`, `util/di/UtilModule.kt`, `database/di/DatabaseModule.kt`) — there is no
  per-feature `di/` package yet (e.g. `note/`, `search/` have none); follow this layer-scoped
  pattern rather than introducing one.
- Pagination: list screens use a `currentPage` / `pageSize` (see `AppConstants.DEFAULT_PAGE_SIZE`) /
  `hasMoreData` triplet, with `isRefreshing` and `isLoadingMore` as separate fields on the
  ViewModel's `*UiState` (not separate `StateFlow`s), as in `HomeViewModel`.
- Firestore security rules (`firestore.rules`) deny all access by default; per-collection rules are
  additive (OR'd). Keep new collections behind an explicit `request.auth != null` (or stricter) rule.
- Cloud Functions code style is enforced by `eslint-config-google` — run the functions lint command
  above before committing changes under `functions/`.

### Kotlin coding conventions

Follows the [official Kotlin style guide](https://kotlinlang.org/docs/coding-conventions.html)
(`kotlin.code.style=official`). Project-specific points worth calling out beyond the official guide:

- Prefer immutable collections (`List`, `Map`) over mutable ones across public API surfaces —
  mutate locally, expose immutably.
- Prefer a sealed type over a nullable field where both express the same state, matching the
  nested loading/loaded/error content types each ViewModel's `*UiState` wraps (see Architecture
  above).
- Name booleans and boolean-returning functions as predicates (`isRefreshing`, `hasMoreData`,
  `canRetry`), matching the pagination/state fields already used in ViewModels.

### Formatting

There is no `ktlint`/`spotless`/`detekt` Gradle plugin configured in this project — formatting is
enforced by convention, not a CLI check. After making Kotlin changes, reformat touched files with
Android Studio's formatter (**Code → Reformat Code**, or `Cmd+Opt+L` / `Ctrl+Alt+L`) using the
project's default (official Kotlin style) settings before committing, and avoid introducing
unrelated whitespace/import-order diffs in files you didn't otherwise change.
