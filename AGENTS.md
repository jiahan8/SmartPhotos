# SmartPhotos

Android app (Kotlin) for organizing photos/notes with ML-based tagging. Firebase backend + a
Node.js Cloud Functions project in `functions/`. Sixteen Gradle modules, plus `build-logic/` — an
included build holding the six convention plugins.

**This file is loaded into every agent conversation, so it states rules, not reasoning. When a rule
here has a longer story behind it — the incident that produced it, the alternative that was tried —
that story is in [ARCHITECTURE.md](ARCHITECTURE.md).**

| Module | What lives there |
| --- | --- |
| `:app` | `MainActivity`, `MyApp`, `MainViewModel`, `SmartPhotosApp`, `navigation/`, the messaging service, `di/AppModule.kt`, `util/`. Hosts the NavHost, supplies each screen's navigation lambdas, installs the Hilt bindings — **no feature screen renders here**. |
| `:core:domain` | Pure Kotlin JVM (no AGP, no Hilt/KSP): domain models, repository *interfaces*, `safeCall`, the `ErrorHandler` interface, DI qualifiers. |
| `:core:common` | Android library, deliberately not Compose: `validateUsername`/`validateDisplayName` + their strings, the `MediaFileRepository` contract, `util/MediaUriExt.kt`, and the two `@ViewModelScoped` classes every feature shares (`NoteShareDelegate`, `NoteErrorReporter` — why it has Hilt/KSP). |
| `:core:data` | Android library holding every implementation of a `:core:domain`/`:core:common` contract: the `Default*`/`Firebase*` repositories, Room, DataStore, `FirebaseModule`, `DataModule`. |
| `:core:ui` | Android library, shared Compose vocabulary: `common/` (14 composables), `ui/theme/`, `util/DateTimeUtils.kt`/`FlowUtils.kt`. |
| `:feature:*` | One Android library per screen — `home`, `search`, `note`, `preview`, `favorite`, `profile`, `settings`, `auth`, `explore` — holding its Compose screen(s), ViewModel(s), route and tests. |
| `:core:testing` | Shared test fixtures: nine `fake/` repository doubles + `MainDispatcherRule`. `testImplementation` only (plus `androidTestImplementation` wherever a `sharedTest/` runs in both). |
| `:core:screenshot-testing` | `BaseScreenshotTest` + the four artifacts it names (Robolectric, Roborazzi ×2, compose `ui-test-junit4`). No build file declares it — `smartphotos.android.screenshot` pulls it in. |

Sources sit at `<module>/src/main/kotlin/com/jiahan/smartcamera/` (`:app` uses `java/`).

### Module rules

- **An Android-typed *contract* goes in `:core:common`; its implementation stays in `:core:data`.**
  `:core:common` is the module closest to needing a split — if an unrelated fifth tenant lands,
  split it before it needs a name like `:core:misc`.
- **Neither fixtures module may depend on `:core:data`**, and both are `api` throughout (a fixtures
  module's API surface is *other* modules' types — `FakeNoteRepository` **is** a `NoteRepository`).
  Every fake implements an interface, and those interfaces live in `:core:domain`/`:core:common`
  precisely so a test never resolves a `Default*`. **A fixtures module is a supplier to the data
  layer or a consumer of it, never both.**
- **Keep the two apart:** `:core:testing` is fixtures every test module wants,
  `:core:screenshot-testing` a harness only the four capturing modules want. Both are **regular
  library modules, not AGP's `testFixtures`** — that was tried and doesn't work, since the Kotlin
  Android plugin generates no Kotlin compilation for that variant.

### Dependency rules

Arrows run one way: `:app` → `:core:data` → `:core:common` → `:core:domain`; `:app` → `:core:ui` →
`:core:domain`; `:app` → each `:feature:*` → the `:core` libraries it needs. `:core:ui` and
`:core:data` are siblings. Nothing depends on `:app`, so a repository implementation can never reach
a ViewModel, an `:app` `R` string, or `BuildConfig`. The fixtures modules hang off test classpaths
only. Inspect a graph with `./gradlew :feature:profile:dependencies --configuration
debugCompileClasspath`.

**No feature depends on another feature or on `:core:data` — enforced at configuration time**, not
just documented: `smartphotos.android.feature` fails with a named error, scanning every declaration
bucket rather than only the compile ones. Three feature edges are worth knowing: `:feature:preview`
is the only module carrying ExoPlayer/Coil (the only screens that play video or load a full-screen
image); `:feature:note` owns `IncomingShareHandler` and `:feature:search` owns
`SEARCH_DEEP_LINK_URI_PATTERN`, both read *downward* by `:app`.

**Kotlin package names are identical across modules** (`com.jiahan.smartcamera.util`, `.di`,
`.data.repository`, …), so a type moving between modules is usually a pure `git mv` with no import
churn. Namespaces are not: a feature's namespace is `com.jiahan.smartcamera.feature.<name>` while
its Kotlin packages stayed bare, so its own `R` is reached as `import
com.jiahan.smartcamera.feature.<name>.R` — including from a file inside that module. `:core:common`
and `:core:ui` are the same shape (`com.jiahan.smartcamera.core.common.R` / `.core.ui.R`).

## Build, test, lint

Run from the repo root (Gradle wrapper):

| Task | Command |
| --- | --- |
| Debug APK | `./gradlew assembleDebug` |
| Unit tests (425 across 14 modules) | `./gradlew testDebugUnitTest :core:domain:test` |
| Hilt graph + androidTest sources | `./gradlew compileDebugAndroidTestKotlin` |
| Release variant | `./gradlew assembleRelease` |
| Screenshot diff / re-record | `./gradlew verifyRoborazziDebug` / `recordRoborazziDebug` |
| Lint | `./gradlew lintDebug` |
| Instrumented (needs a device) | `./gradlew connectedDebugAndroidTest` |
| Cloud Functions lint (Node 24) | `npm --prefix functions run lint` |

- **Run `compileDebugAndroidTestKotlin` and `assembleRelease` after changing any module's dependency
  block or an `@Inject` constructor.** They cover the two variants nothing else reaches: the first
  is the only thing compiling androidTest sources (so the only thing catching a Hilt graph error,
  via `di/HiltGraphSmokeTest.kt`), the second the only thing compiling release (so the only thing
  catching a dependency that reaches a module through `debugImplementation` alone). CI runs both.
- **A wall of `InjectProcessingStep was unable to process 'X(…,Foo,…)' because 'Foo' could not be
  resolved` after moving a type between modules is stale KSP state, not a dependency bug.**
  `./gradlew clean` fixes it. The tell is that the unresolved name is unqualified while its
  neighbours from the same module are fully qualified; check this before assuming an
  `api`/`implementation` misconfiguration (see [Conventions](#conventions)).
- Debug build via Firebase App Distribution: `firebase login` once, then `./gradlew assembleDebug
  appDistributionUploadDebug` (the `testers` group must exist in the Firebase console first).
  Cloud Functions: `npm --prefix functions run serve` / `deploy` (needs Firebase CLI auth).

### Unit tests

`:core:domain` is a plain Kotlin JVM module, so its tests run under `test`, not the Android-variant
`testDebugUnitTest` every other module uses (as do `lintDebug` and `connectedDebugAndroidTest`) —
hence both tasks above. `:core:testing` and `:core:screenshot-testing` have no tests of their own.

- Single class: `--tests "com.jiahan.smartcamera.home.HomeViewModelTest"`; single method: append
  `.methodName`.
- **Assert on a settled `StateFlow` by reading `.value`.** Reach for
  [Turbine](https://github.com/cashapp/turbine) (`.test { ... }`) when the *sequence* matters, or
  for a `SharedFlow` event (no `.value` to read at all). Never hand-roll a collector into a list.
- **ViewModel tests replace `Dispatchers.Main` with `MainDispatcherRule`** (`:core:testing`) —
  `@get:Rule val mainDispatcherRule = MainDispatcherRule()`, since `viewModelScope` dispatches to
  Main. Defaults to `UnconfinedTestDispatcher`; pass `StandardTestDispatcher` when a test needs
  virtual-time control (e.g. a debounce).

### Screenshot tests

Roborazzi goldens live beside the composable they capture, in four modules: `:core:ui`,
`:feature:home`, `:feature:search`, `:feature:settings`. Each keeps its own under
`src/test/screenshots/`.

- **A capturing module applies `smartphotos.android.screenshot` and nothing else** — the plugin
  brings the Roborazzi tasks, the VCS-tracked `outputDir`, `unitTests.isIncludeAndroidResources`
  **and the harness module itself**. It still needs
  `debugImplementation(libs.androidx.ui.test.manifest)` on top (supplied by the feature convention,
  declared by `:core:ui` for itself), without which `createComposeRule()` can't resolve an activity.
- `testDebugUnitTest` *runs* these tests but does **not** diff them; only `verifyRoborazziDebug`
  does. Re-record only for an intended change, and inspect the new PNGs before committing.
- **The unit-test JVM is pinned to UTC/en-US** (`configureTestJvm()` in `build-logic`), because a
  screenshot rendering a timestamp goes through `Long.toFormattedDateTime()`, whose `zone`/`locale`
  default to the system's. If you change the pin, re-record. **A golden diff appearing only on CI is
  far more likely non-determinism in the test than a platform rendering difference** — check for a
  clock, locale or random value in the fixture first.
- **A golden that renders a build-varying value is a hoisting problem, not a re-recording chore** —
  `SettingsScreen` takes `versionName` as a parameter and the test pins `"1.0.0"`.

### Instrumented tests

Ten modules — `app/`, `core/data/`, and every `:feature:*` except `:feature:explore`. `:app` uses a
custom `HiltTestRunner`, the AndroidX Test Orchestrator and `clearPackageData=true` for hermetic
runs; **don't remove these without understanding why.** Library modules declare no
`testInstrumentationRunner` — their suites build a ViewModel from `:core:testing`'s fakes. The same
suites run on Firebase Test Lab via `./scripts/run-test-lab.sh` (needs `gcloud`, auth, Blaze).

**`sharedTest/` is the arrangement to copy for a new screen test.** A Compose behaviour suite placed
there compiles into *both* the unit-test and androidTest source sets, so it runs under Robolectric
and on-device, written once — `:feature:home`'s `HomeScreenTest` and `:feature:auth`'s
`AuthScreenTest` do this (two `sourceSets` lines plus `unitTests.isIncludeAndroidResources = true`).
**Check an existing androidTest-only suite's own note before promoting it**: `ProfileScreenTest` is
device-only because its bottom-anchored save button and inline validation text depend on real
viewport/scroll behaviour.

### CI

`.github/workflows/ci.yml` runs on every push to `main`, every PR, and on demand: debug APK, release
APK (`-x uploadCrashlyticsMappingFileRelease`), androidTest compile, then unit tests, screenshot
comparison and `lintDebug` as separate steps on JDK 21 — each runs even if an earlier one failed, so
one run reports every problem. A parallel job lints `functions/`.

- It needs one repository secret, `GOOGLE_SERVICES_JSON` (`app/google-services.json` is gitignored
  and the Google Services plugin fails without it): `base64 -i app/google-services.json`, pasted
  into Settings > Secrets and variables > Actions as a *repository* secret.
- On a screenshot failure download the `screenshot-and-lint-reports` artifact — its `*_compare.png`
  files show reference/diff/actual side by side.
- **The artifact path lists are globs** (`*/build/…` and `*/*/build/…`) so a new module is collected
  with no edit. **Keep them globs** — as literal paths they drifted three times.

### Convention plugins

`build-logic/` is an included build (`includeBuild("build-logic")` from `pluginManagement` in
`settings.gradle.kts`), holding six plugins that every module applies by id instead of restating the
same settings:

| Plugin | Applied by | Applies | Sets |
| --- | --- | --- | --- |
| `smartphotos.android.application` | `:app` | AGP application, Kotlin Android | compileSdk 37, minSdk 28, Java 11, JVM target 11, test-JVM pin |
| `smartphotos.android.library` | `:core:common`, `:core:data`, `:core:ui`, `:core:testing`, `:core:screenshot-testing` | AGP library, Kotlin Android | the same |
| `smartphotos.android.compose` | `:app`, `:core:ui`, `:core:screenshot-testing` | Compose compiler | `buildFeatures.compose = true` |
| `smartphotos.android.feature` | all nine `:feature:*` | the library + compose conventions, KSP, Hilt, kotlin-serialization | the `:core:domain`/`:core:ui` edges, the Compose set, icons, lifecycle, `ui-test-manifest`, the test baseline (`:core:testing`, junit, mockk, coroutines-test, Turbine) and the androidTest baseline; **enforces the feature layering** |
| `smartphotos.android.screenshot` | `:core:ui`, `:feature:home`, `:feature:search`, `:feature:settings` | Roborazzi | `outputDir` → `src/test/screenshots`, `unitTests.isIncludeAndroidResources`, `testImplementation(:core:screenshot-testing)`; **refuses to apply to the harness module** |
| `smartphotos.jvm.library` | `:core:domain` | Kotlin JVM — **nothing Android** | Java 11, JVM target 11, test-JVM pin |

- **A feature's build file contains only what that feature alone needs beyond the convention** —
  explore keeps `coil-compose`/`activity-compose`; settings keeps `androidx-core-ktx`/Roborazzi.
- **Put a setting here only when more than one module wants it.** `targetSdk` stays per-app-module,
  `namespace` per-library, `buildConfig = true` in `:app` only. **One module is a sample size of one
  — wait for the second before writing a shared convention.**
- **`build-logic` targets Java 17, the modules target 11.** Not drift: the plugins run in the Gradle
  daemon (needs 17+), 11 is what the app compiles against. Don't "fix" either.
- **The plugin artifacts are `compileOnly`**, so the modules' `pluginManager.apply(...)` calls
  resolve against the build classpath the root `build.gradle.kts` establishes with its `apply false`
  block — which must keep listing them. `.android.screenshot` is the exception needing a real plugin
  artifact (`roborazzi-gradlePlugin`), since it configures Roborazzi's extension.
- **An alias typo in `build-logic`'s catalog lookup fails at configuration time in the consuming
  module, not at compile time in `build-logic`** — it looks the catalog up by string name
  (`Project.libs`), since the generated `libs.*` accessors are build-script-only.
- **AGP 9's `CommonExtension` is not generic and exposes only property accessors** —
  `defaultConfig.minSdk = …`, not the `defaultConfig { }` block form of AGP-8-era guides.

## Architecture

MVVM, one Gradle module per feature. See [ARCHITECTURE.md](ARCHITECTURE.md) for the system diagram,
the Firestore collections, and the Cloud Functions' division of labour.

- **UI** — Compose screens (`*Screen.kt`) + the graph in `navigation/SmartPhotosNavGraph.kt`, each
  destination's route type living in the feature package that owns it.
- **ViewModel** — `@HiltViewModel` classes exposing a `*UiState` data class via `StateFlow`. The
  loading/loaded/error branch is a **nested sealed sub-type** (e.g. `HomeContent`), kept separate
  from flat fields on the outer `*UiState` for orthogonal UI state (`isRefreshing`, dialogs,
  pagination) that shouldn't force a full state-machine branch.
- **Repository** (`data/repository/`) — one interface + one `Default*` implementation each, bound in
  `data/di/DataModule.kt`. Interfaces live in `:core:domain`; implementations and `DataModule` in
  `:core:data`. **Two interfaces can't live in `:core:domain` because their signatures carry Android
  types:** `AppUpdateRepository` (`ActivityResultLauncher`/`IntentSenderRequest`) stays in
  `:core:data` beside its `Default*` since only `:app`'s `MainViewModel` injects it;
  `MediaFileRepository` (`Bitmap`/`Uri`) sits in `:core:common` because a feature injects it and
  must not depend on `:core:data`. Move the next Android-typed interface down only when a feature
  needs it.
- **Domain** (`domain/`, `:core:domain`) — plain data classes shared across features.
- **Local** — Room in `database/` (schemas exported to `core/data/schemas/`), DataStore in
  `data/datastore/` (contract + model in `:core:domain`, wiring in `:core:data`). **A note's media
  list persists into `notes.media_list` as `kotlinx.serialization` JSON keyed by `MediaDetail`'s
  property names** — an on-disk format, so renaming one needs `@SerialName` to keep old rows
  decodable.
- **Remote** — Firebase (Auth, Firestore, Storage, Remote Config, Analytics, Crashlytics, FCM) plus
  Cloud Functions in `functions/index.js` calling Google Cloud Vision.

### Separation of concerns

- Composables render state and forward user intents; they never call Firebase/Room/DataStore
  directly or hold business logic beyond UI-only state (scroll position, sheet visibility).
- ViewModels depend on repository *interfaces*, never `Default*` implementations or Firebase/Room
  types, so they stay unit-testable without a real backend.
- Repositories expose domain models — never Firestore `DocumentSnapshot`/`QuerySnapshot` or Room
  entities — across the interface boundary. **Every fallible operation returns `Result<T>` rather
  than throwing** (wrap the body in `util/safeCall`), so callers never wrap a call in try/catch. The
  exemptions are the ones that can't carry a `Result`: a `Flow`-returning stream, and
  fire-and-forget work like `quickUploadMediaToFirebase`, which logs its failures internally.
- **A local media location crosses the domain boundary as `domain/MediaUri.kt`, never
  `android.net.Uri`** — convert with `toMediaUri()`/`toPlatformUri()` (`util/MediaUriExt.kt`,
  `:core:common`) at the ViewModel boundary on the way down, or inside a `Default*` on the way out.

When adding a feature, prefer extending this layering over reaching across it.

### Source of truth

**Firestore is the source of truth; Room is written only after the Firestore write returns.** A
deliberate inversion of the offline-first guide — the four note-rendering screens *read* the Room
mirror, which is the read half of offline-first, not the write half. Each exposes a `content:
StateFlow` built with `combine(<a Room query>, <a fetch status>)` and shared `WhileSubscribed`; the
remote call still happens, but its result fills the `notes` table and the screen re-reads it:

| Screen | Query | Fetch that fills it |
| --- | --- | --- |
| Home | `getNotesStream(limit)` | `getNotes(cursor)` |
| Search | `searchNotesStream(query)` | `searchNotes(query)` |
| NotePreview | `getNoteStream(noteId)` | `getNote(noteId)` |
| Favorite | `getFavoriteNotesStream(query)` | `syncFavoriteNotes()` |

**Copy this `combine` shape for a new mirrored list.** Fetch status decides only what an *empty*
result means (nothing fetched yet = loading, fetch failed = error) — any cached rows beat both, so a
failed refresh keeps the list on screen and reports itself through `actionError`.

- **`getNotesStream` takes a `limit`**, widened one page at a time as the cursor advances (and reset
  wherever the cursor resets) — without it Home renders the whole table, not just what it has paged.
- **A mirror-write failure must fail the fetch, not log-and-continue.** A swallowed write is a note
  the user can't see, with the cursor already advanced past it.
- **Every remote read writes what it fetched into the table before returning** — one that skips this
  renders nothing, since screens never look at return values. `addNote` reads its own note back via
  `getNote`, because the createNote function returns only `{documentPath}`.
- `syncFavoriteNotes` reconciles only the favorited rows; nothing reconciles the rest of the table.
- **Room answers before the first fetch does**, so a fresh install must not render "create your
  first note" while the first fetch is in flight — see `HomeViewModel`'s `FetchStatus.Pending`
  branch and its two pinning tests. The mirror image is a *failed* fetch: full-screen error on an
  empty cache, `actionError` snackbar on a populated one.

**Known gaps, so nobody rediscovers them as bugs:** no offline writes, and no reconciliation (a row
deleted server-side lingers locally until something rewrites it). Both are deliberately out of scope.

### Cross-feature communication

**There is no cross-feature communication mechanism, and that is deliberate. Do not reintroduce
one.** A screen that must reflect a mutation made on another screen observes the Room mirror
([Source of truth](#source-of-truth)). If a list *could* be backed by a live query, back it with the
query.

**For an event a screen must never miss** — the one still in use is `note/IncomingShareHandler.kt` —
use a `StateFlow` holding the pending value plus an explicit `consume()`, not a `SharedFlow`: a
default `MutableSharedFlow` has no replay, so a subscriber that isn't collecting yet misses it.

### Error handling

Route thrown errors through `util/ErrorHandler`, never `Throwable.localizedMessage`. Its two methods
belong to different layers:

- **`logError(throwable, tag)` — any layer, repositories included.** Only touches `Log` (debug) and
  Crashlytics (release), so a repository logging a failure it swallows is correct.
- **`getErrorMessage(throwable)` — ViewModel layer only.** It resolves a string resource, making its
  result presentation, not data. Repositories log and then propagate or fold into a `Result`/null;
  the ViewModel converts that into a `*UiState` error field.

**A repository that raises its own failure throws a `domain/AppError`, never a message** —
`IllegalStateException(context.getString(...))` puts ViewModel-layer work in the data layer and
forces a `Context` into a class that needs none. `AppError` is a sealed type carrying an identity
(`NotAuthenticated`, `NoteUnavailable`, `UsernameTaken`, …); `appErrorMessageResId` maps each to a
string *inside* `getErrorMessage`. **Add a case to the sealed type and the mapper together.**

**This splits the test as well as the code.** A repository test asserts the `AppError` raised; the
string it resolves to is `ErrorMessageMappersTest`'s. A data-layer test asserting user-facing
English is reaching a layer up — the tell is mechanical: it can't move into `:core:data` with its
subject, because `DefaultErrorHandler` and `:app`'s `R` don't exist there.

**Fold a Firebase type into an `AppError` below the repository boundary**, inside the `Default*`
(see `DefaultNoteRepository.foldNoteValidationError`) — never in a ViewModel-layer mapper, which
would put `firebase-functions` on a feature module's classpath.

The three pieces live in three files, by layer: `util/ErrorHandler.kt` (interface + `ErrorTag`,
`:core:domain`), `util/DefaultErrorHandler.kt` (implementation, `:app`), `util/ErrorMessageMappers.kt`
(the `R`-resolving mapper, `:app`). Keep a new mapper in the third rather than reuniting them.

### Kotlin Multiplatform readiness

We may migrate `domain/`, repository interfaces and other business logic to KMP later. Not a mandate
to add tooling now, but between otherwise-equivalent approaches prefer the cheaper one to migrate:

- **Keep `android.*` out of the *contracts*** — domain models, repository interfaces, and the data
  classes they carry. `Default*` implementations are Android-bound by definition and aren't what
  this targets; `MediaFileRepository` is deliberately exempt, so don't cite it as precedent.
- **Prefer `kotlinx` libraries** (`kotlinx.coroutines`, `kotlinx.datetime`, `kotlinx.serialization`)
  over equivalents with no `commonMain` implementation (`java.time`, Gson) in shared-leaning code.
- **Don't report module extraction as KMP progress** — `:core:data`, `:core:ui` and every
  `:feature:*` are Android libraries full of Firebase/Room/Compose, so nothing in them became
  shareable by moving. Don't rename `:core:domain` to `:core:model`; it holds more than models.

The next step, and the Hilt ceiling that limits how far it can go, are in
[ARCHITECTURE.md](ARCHITECTURE.md#kotlin-multiplatform).

## Follow official Android guidance

Prefer solutions aligned with Google's official guidance over ad-hoc approaches. **If an official
recommendation conflicts with an existing pattern here, follow the official recommendation, update
the codebase convention to match, and call out the discrepancy.**

[App architecture](https://developer.android.com/topic/architecture) (unidirectional data flow,
`StateFlow`/`UiState` from ViewModels, repositories as single source of truth — with the documented
exception in [Source of truth](#source-of-truth)) ·
[Compose](https://developer.android.com/develop/ui/compose/documentation) (state hoisting,
`remember`, no side effects outside `LaunchedEffect`/`DisposableEffect`) ·
[Coroutines & Flow](https://developer.android.com/kotlin/coroutines/coroutines-best-practices) ·
[Material 3](https://m3.material.io/) ·
[Kotlin conventions](https://kotlinlang.org/docs/coding-conventions.html). Two project-specific
sharpenings:

- **Use `derivedStateOf` only where a frequently-changing state feeds a rarely-changing derived
  value** (a scroll offset driving an `isScrolled` boolean); applied more broadly it adds overhead
  instead of removing it.
- **Inject `CoroutineDispatcher`s rather than hardcoding `Dispatchers.IO`, and never `GlobalScope`.**
  The UI is Compose-only, so `LaunchedEffect`/`rememberCoroutineScope` are the composition-side
  equivalents — **`lifecycleScope` in a new file is a smell, not a convention.**

### Compose state hoisting

Screen-level composables (`*Screen.kt`) collect `UiState` via `collectAsStateWithLifecycle()` and
hoist it down as plain parameters/lambdas; **child composables stay stateless and never take a
ViewModel reference**, so they can be previewed and tested with plain state.

**There are three tiers, not two:**

| Tier | For |
| --- | --- |
| `remember`/`mutableStateOf` | State genuinely local and ephemeral to composition (IME visibility, an animation trigger) |
| `rememberSaveable` | Local state that must survive configuration change and process death (a half-typed field, an expanded section) |
| ViewModel `UiState` | Anything another screen needs or that outlives the composition — with `SavedStateHandle` for the parts that must survive process death |

"Put it in the ViewModel" does not by itself mean "it survives": a ViewModel is cleared when its
`NavBackStackEntry` is popped, and none survives process death. `rememberSaveable` appears nowhere
in `app/src/main` today — that's a gap, not a convention to copy.

### Composable parameter order

Required parameters first, then optional (defaulted) ones, with the ViewModel **last**:

```kotlin
fun PhotoPreviewScreen(
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: PhotoPreviewViewModel = hiltViewModel()
)
```

This is [Compose's own API guideline](https://developer.android.com/develop/ui/compose/api-guidelines),
and the cost of getting it wrong is concrete rather than stylistic: a default sitting ahead of a
required parameter can never be taken positionally, forcing every caller past that point into named
arguments.

**The exception is a trailing lambda, which stays last even though the parameters before it carry
defaults** — `bounceClick`'s `onClick`, `SearchBar`'s `placeholder` and `SmartCameraTheme`'s
`content`, matching Material3's own shape (`Button(onClick, modifier, enabled, …, content)`). Those
three are the only places a defaulted parameter should precede a required one.

### One-off UI events

Snackbars and other fire-and-forget signals travel from ViewModel to screen on a
`MutableSharedFlow(extraBufferCapacity = 1)` exposed as a read-only `SharedFlow`, collected in the
screen's `LaunchedEffect` and shown through `SnackbarHostState`. `actionError` (via `:core:common`'s
`NoteErrorReporter`) and `ProfileViewModel.events` are the existing instances — **follow their shape
rather than inventing a third.**

This deviates from the [official guidance](https://developer.android.com/topic/architecture/ui-layer/events)
and stands because these signals have no state to restore — a snackbar already shown must not
reappear. Two limits come with it:

- **Anything that must survive configuration change or process death is not one of these** — error
  text a screen keeps displaying belongs in `UiState` (e.g. `HomeContent.Error`).
- **`tryEmit` into a one-slot buffer drops silently** when events land back-to-back with no
  collector ready, and a `LaunchedEffect` collector only exists while the composition does. Don't
  put anything the user must not miss on this flow.

### Navigation

Navigation Compose's type-safe routes: each destination is a top-level `@Serializable` `data
object`/`data class`, registered with `composable<HomeRoute> { ... }` and reached via
`navController.navigate(NotePreviewRoute(id))`. **No hand-built path strings, no `navArgument`
lists, no manual URL escaping.**

- **The route type lives in the feature package, beside the screen it names.** What stays in
  `navigation/` is the wiring that needs to see every route at once: `SmartPhotosNavGraph.kt`,
  `TopLevelDestination.kt`, `NavTransitions.kt`. Keeping a route with its screen is what lets a
  feature's ViewModel read its own arguments back with `toRoute<…>()` without importing upward.
- **The routes share no supertype, deliberately** — `startDestination` and
  `TopLevelDestination.route` are typed `Any`, as Navigation Compose itself does. A sealed supertype
  isn't merely inconvenient but impossible: Kotlin requires every direct subtype in the same module
  *and* package as the declaration. **Don't reintroduce a marker interface to get exhaustiveness
  back.**
- **Route types stay plain data.** UI-only metadata (bottom-bar icon, title, whether re-tapping the
  tab scrolls its list to the top) lives in `navigation/TopLevelDestination.kt`, because only some
  destinations appear in the bottom bar. It is an `enum`, not a data class plus a list, so its `Any`
  route can't be widened: the constructor is private, the set is closed at five, and a `when` over
  it stays exhaustive across module boundaries.
- **A route's property names are its argument names** — Navigation serializes by property name, and
  ViewModel tests build a `SavedStateHandle` from the same keys (`mapOf("noteId" to …)`). Renaming
  one breaks its test; renaming the route *class* invalidates a back stack saved by an older build.
- **An enum used as a route argument needs `@Keep`** (see `MediaSourceType` in
  `preview/PreviewRoutes.kt`). Navigation resolves enum arguments through `Class.forName()`, so R8
  renaming one breaks navigation in release builds only — invisible to debug runs and unit tests.

## Conventions

### Dependency injection

Hilt, all *modules* `@InstallIn(SingletonComponent::class)` — but constructor-injected classes may
be narrower (`NoteErrorReporter` is `@ViewModelScoped`), so "all modules are singleton" isn't
"everything is a singleton". App-wide bindings live in `di/AppModule.kt` (`:app`); other
cross-cutting layers get their own module — `util/di/UtilModule.kt` (`:app`),
`data/di/DataModule.kt`, `data/di/FirebaseModule.kt`, `database/di/DatabaseModule.kt`
(`:core:data`). There is no per-feature `di/` package; follow this layer-scoped pattern.

**A `@Provides` module belongs in the module its bindings are consumed from, not in `:app`.** Hilt
aggregates every singleton module into one component generated in `:app`, so a provider works from
anywhere and nothing fails if it sits too high — which is how `FirebaseModule` sat in `:app`,
putting the whole Firebase surface in `:app`'s dependency block for code `:app` doesn't contain.
**Ask where a binding is *injected*, not where it's convenient to declare.**

**Don't reference `Dispatchers.IO` directly in new code.** Inject `@param:IoDispatcher private val
ioDispatcher: CoroutineDispatcher` (qualifier in `di/Qualifiers.kt`, `:core:domain`; provider in
`di/AppModule.kt`, `:app`) so tests can substitute a `TestDispatcher`. `@ApplicationScope` provides
the app-lifetime `CoroutineScope` for work that must outlive a ViewModel. The one deliberate
exception is `data/datastore/DataStoreModule.kt`, where DataStore's own scope is built at module
level.

### Dependency configurations

- **In `:core:data`, anything whose type appears in an `@Inject constructor` parameter must be
  `api`, not `implementation`** — `:app`'s annotation processor has to resolve those types itself,
  and hiding one fails with `InjectProcessingStep was unable to process 'x' because 'Y' could not be
  resolved`. **Under Hilt a library's constructor parameters are effectively part of its API.** A
  dependency used only inside function bodies stays `implementation` (`firebase-storage`,
  `play-app-update-ktx`).
- **In `:core:ui`, a Compose artifact whose type appears in a public signature is `api`** —
  `Modifier`, `SnackbarHostState`, `Typography`, `Color`, `Shape`, `ImageVector`, `LazyListState`,
  so `compose-bom`, `ui`, `ui-graphics`, `material3` and `foundation` all are. `coil-compose`,
  `kotlinx-coroutines-android`, `ui-tooling-preview` and the icon packs stay `implementation`. Check
  with `./gradlew :core:ui:dependencies --configuration api`.

### Dependencies

**Every version lives in `gradle/libs.versions.toml`, referenced through the generated `libs.*`
accessors** — never an inline `implementation("group:artifact:1.2.3")`.

**A module declares what its own sources name, and a dependency it stops naming is deleted, not
left.** Two exceptions, both flagged in place where they're declared: an artifact loaded
reflectively or auto-initialising (`firebase-perf`, `firebase-inappmessaging-display`,
`coil-network-okhttp`, `coil-gif`), and a compiler plugin whose absence changes codegen rather than
resolution (`kotlin-serialization` in `:core:data`).

**That exception list is the dangerous half of this rule, and has been got wrong in both
directions:**

- **Check for an injection *site* before treating a `@Provides` as load-bearing** — an unused Hilt
  binding reads exactly like a live one, with no import to be missing and no compile error to raise.
- **Before deleting a dependency nothing imports, look inside the artifact** (`unzip -p <aar>
  classes.jar | ...`, or its `META-INF/services`). A service file, a `ContentProvider` in its
  manifest, or a Gradle plugin expecting the SDK all mean "used" in a way grep cannot see — under
  Coil 3, classpath presence **is** the registration, so a *working* GIF setup is precisely one with
  no `components { add(...) }` block to find. The debug APK is ground truth: `unzip -l app-debug.apk
  | grep META-INF/services` (release renames them under R8, so compare counts there, not names).

### Tests and resources

- **A test lives in the module that owns its subject.** Tests don't move themselves when a class
  does, and nothing fails when they stay. **When a test cannot follow its subject down, that is a
  finding, not a reason to leave it** — it means the test asserts something from a layer above.
- **Resources belong to the module whose code resolves them**, and `android.nonTransitiveRClass=true`
  means each module's `R` holds only its own — reached from elsewhere as `import
  com.jiahan.smartcamera.core.ui.R as UiR` (`:core:common`'s as `CommonR`). `:app` may read a
  *feature* module's `R` downward when one string is genuinely the same copy for the same thing in
  both places.
- **A resource or function moves to the module that owns it, and "owns" means the only consumer** —
  as soon as the *first* second caller appears, not a hypothetical second feature. A consumer
  *count* can't tell "shared vocabulary" (`cd_back`, `no_results_found`) from "two strings that
  happen to share a word" (a screen title vs. a `contentDescription`); only the call sites can. A
  shared *return type* (e.g. `ValidationResult`) lands where every caller sees it.
- A vector drawable moved out of `:app` may stop resolving `?attr/colorControlNormal` (AppCompat
  reaches `:app` only transitively). **If it's drawn only through Compose's `Icon(painter = …)`,
  delete the `android:tint` line rather than adding AppCompat** — `Icon` overrides it anyway.
- **Don't declare a cross-module string in both modules**: the application's value wins the merge,
  but it silently duplicates user-visible text and its translations, and they drift. **Don't turn
  `nonTransitiveRClass` off** either. For a genuinely reusable component, consider hoisting the
  string out as a parameter instead of having the component resolve product copy itself.

### Pagination

**Repositories hold no position state** — the caller owns its place in the list, so two callers
paginating at once can't corrupt each other. The key depends on the source: notes page by an opaque
`NoteCursor` (`getNotes(cursor)` returns a `NotePage` carrying the next one, null = first page),
Explore by page index. The ViewModel keeps that key plus `pageSize` (defaulting to
`AppConstants.DEFAULT_PAGE_SIZE` in `:core:domain`) and `hasMoreData`, with
`isRefreshing`/`isLoadingMore` as fields on the `*UiState` (not separate `StateFlow`s).

- **Derive "is there another page" from the rows the data source returned, never from the mapped
  domain list's size** — mapping can drop rows (a failed author lookup), and a short list would then
  read as "end of feed".
- **Route every path that rebuilds the list from the first page** (pull-to-refresh, a cross-feature
  add, the initial load) **through one `reload()`**, cancelling any in-flight load-more before
  resetting position, with load-more no-opping while a reload is active. A page fetched against the
  old position that lands after the reset splices a stale window into the new list. A ViewModel
  running two paginated lists needs one reload/load-more job pair per list.
- **Paging 3 is deliberately not a dependency** — a cost/benefit call, not impossibility. Revisit if
  the notes feed needs `PagingSource`/`RemoteMediator`.

### Build type

**Below `:app`, don't read `BuildConfig.DEBUG`** — inject `@param:DebugBuild private val
isDebugBuild: Boolean`. `com.jiahan.smartcamera.BuildConfig` belongs to the application module's
namespace, so it's a compile error below `:app`, not a silent wrong value. **Don't generalize this
to application-module code**: `BuildConfig.DEBUG` is a `static final boolean`, so R8 constant-folds
it and strips the dead branch, while an injected flag is a runtime value that ships both — `MyApp.kt`
and `util/DefaultErrorHandler.kt` read it directly for that reason. For a value that's *rendered*
rather than branched on (`versionName`, `logoRes`), hoist it as a parameter instead.

### Backend

- **`firestore.rules` denies all access by default**; per-collection rules are additive (OR'd). Keep
  new collections behind an explicit `request.auth != null` or stricter.
- **Cloud Functions style is enforced by `eslint-config-google`** — run the functions lint command
  before committing changes under `functions/`.

### Kotlin coding conventions

Beyond the official style guide (`kotlin.code.style=official`):

- **Prefer read-only collection types (`List`, `Map`) across public API surfaces — but read-only is
  not immutable.** Upcasting a `MutableList` to `List` hands the caller a live view the owner can
  still mutate underneath them, so when a property or return value is backed by a mutable field,
  copy at the boundary (`.toList()`).
- **Prefer a sealed type over a nullable field** where both express the same state.
- **Name booleans and boolean-returning functions as predicates** (`isRefreshing`, `hasMoreData`,
  `canRetry`).

### Formatting

No `ktlint`/`spotless`/`detekt` plugin is configured — formatting is enforced by convention. After
Kotlin changes, reformat touched files with Android Studio's formatter (**Code → Reformat Code**)
using the project's default settings, and avoid unrelated whitespace/import-order diffs in files you
didn't otherwise change.
