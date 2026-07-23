# SmartPhotos

Android app (Kotlin, single `:app` module) for organizing photos/notes with ML-based tagging.
Firebase backend + a Node.js Cloud Functions project live in `functions/`.

## Build, test, lint

Run from the repo root (Gradle wrapper):

- Build debug APK: `./gradlew assembleDebug`
- Unit tests (JVM, Robolectric-backed): `./gradlew testDebugUnitTest`
  - Single class: `./gradlew testDebugUnitTest --tests "com.jiahan.smartcamera.home.HomeViewModelTest"`
  - Single method: `./gradlew testDebugUnitTest --tests "com.jiahan.smartcamera.home.HomeViewModelTest.methodName"`
- Screenshot tests use Roborazzi and live under `app/src/test/.../screenshot/` — they run as part of
  `testDebugUnitTest`. Record new/updated golden images with `./gradlew recordRoborazziDebug`.
- Instrumented tests (`app/src/androidTest`) require a device/emulator and run via
  `./gradlew connectedDebugAndroidTest`. They use a custom `HiltTestRunner` (installs
  `HiltTestApplication`), the AndroidX Test Orchestrator, and `clearPackageData=true` for hermetic,
  isolated runs — don't remove these from `app/build.gradle.kts` without understanding why.
- Cloud Functions (`functions/`, Node 24): `npm --prefix functions run lint` (eslint, google config).
  Deploy/emulate with `npm --prefix functions run serve` / `deploy` (requires Firebase CLI auth).

## Architecture

MVVM with a layered structure, one Kotlin package per feature under
`app/src/main/java/com/jiahan/smartcamera/` (e.g. `home`, `note`, `favorite`, `search`, `profile`,
`settings`, `auth`, `preview`). Cross-cutting layers:

- **UI** — Jetpack Compose screens (`*Screen.kt`) + Navigation Compose graph in
  `navigation/SmartPhotosNavGraph.kt` / `navigation/Screen.kt`.
- **ViewModel** — `@HiltViewModel` classes exposing a sealed `*UiState` (`Loading` / `Success` /
  `Error`) via `StateFlow`, following the pattern in `home/HomeViewModel.kt`.
- **Repository** (`data/repository/`) — one interface + one `Default*` implementation per
  repository (e.g. `NoteRepository` / `DefaultNoteRepository`), bound in Hilt modules under
  `data/di/` or feature `di/` packages. Coordinates Firebase Firestore/Storage (remote) and
  Room/DataStore (local).
- **Domain** (`domain/`) — plain data classes shared across features (e.g. `HomeNote`,
  `MediaDetail`, `User`).
- **Local** — Room database in `database/` (schemas exported to `app/schemas/`), DataStore
  preferences in `data/datastore/`.
- **Remote** — Firebase (Auth, Firestore, Storage, Remote Config, Analytics, Crashlytics, FCM) plus
  Cloud Functions in `functions/src` calling Google Cloud Vision API for text/label/object
  detection on uploaded photos.

### Cross-feature communication

Features that need to react to events in other features use a per-domain `*Handler` singleton
injected via Hilt (e.g. `note/NoteHandler.kt`) exposing `MutableSharedFlow`s (`noteAddedEvent`,
`noteDeletedEvent`, `noteFavoritedEvent`, …). ViewModels emit into these handlers on mutation and
collect them in `init {}` to keep other screens (e.g. Home, Favorite) in sync without a shared
ViewModel — follow this pattern instead of adding direct cross-ViewModel references.

### Error handling

Route all thrown errors through `util/ErrorHandler` (`logError` then `getErrorMessage`) rather than
reading `Throwable.localizedMessage` directly — this ensures Crashlytics logging in release builds
and consistent user-facing messages via `ResourceProvider`.

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
  (already enforced here via `kotlin.code.style=official` in `gradle.properties`).

If an official recommendation conflicts with an existing pattern in this codebase, follow the
existing codebase convention for consistency, but call out the discrepancy.

## Conventions

- DI: Hilt. Global bindings in `di/AppModule.kt` / `di/FirebaseModule.kt`; feature-scoped bindings
  in that feature's own `di/` subpackage (e.g. `data/di/DataModule.kt`, `util/di/UtilModule.kt`).
- Pagination: list screens use a `currentPage` / `pageSize` (see `AppConstants.DEFAULT_PAGE_SIZE`) /
  `hasMoreData` triplet with separate `isRefreshing` and `isLoadingMore` `StateFlow`s, as in
  `HomeViewModel`.
- Firestore security rules (`firestore.rules`) deny all access by default; per-collection rules are
  additive (OR'd). Keep new collections behind an explicit `request.auth != null` (or stricter) rule.
- Cloud Functions code style is enforced by `eslint-config-google` — run the functions lint command
  above before committing changes under `functions/`.
