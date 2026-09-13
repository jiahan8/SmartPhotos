# SmartPhotos

Android app (Kotlin) for organizing photos/notes with ML-based tagging. Firebase backend + a
Node.js Cloud Functions project in `functions/`. Seventeen Gradle modules, plus `build-logic/` — an
included build holding the six convention plugins.

**This file is loaded into every agent conversation, so it states rules, not reasoning. When a rule
here has a longer story behind it — the incident that produced it, the alternative that was tried —
that story is in [ARCHITECTURE.md](ARCHITECTURE.md).**

| Module | What lives there |
| --- | --- |
| `:app` | `MainActivity`, `MyApp`, `MainViewModel`, `SmartPhotosApp`, `navigation/`, the messaging service, `di/AppModule.kt`, `util/`. Hosts the NavHost, supplies each screen's navigation lambdas, installs the Hilt bindings — **no feature screen renders here**. |
| `:core:domain` | Kotlin Multiplatform, `jvm` + three iOS targets (no AGP, no Hilt/KSP): domain models, repository *interfaces*, `safeCall`, the `ErrorHandler` interface, DI qualifiers, and the three field validators (`util/ValidationUtils.kt`) with `ValidationResult`/`ValidationError`. |
| `:core:common` | Android library, deliberately not Compose: the validation and failure strings + the mappers screens resolve them with (`validationErrorMessageResId`, `ErrorMessage.resolve`/`appErrorMessageResId`), the `MediaFileRepository` contract, `util/MediaUriExt.kt`, and the two `@ViewModelScoped` classes every feature shares (`NoteShareDelegate`, `NoteErrorReporter` — why it has Hilt/KSP). |
| `:core:data` | Android library holding every implementation of a `:core:domain`/`:core:common` contract: the `Default*`/`Firebase*` repositories, Room, DataStore, `FirebaseModule`, `DataModule`. |
| `:core:ui` | Android library, shared Compose vocabulary: `common/`, `ui/theme/`, `util/DateTimeUtils.kt`/`FlowUtils.kt`. |
| `:feature:*` | One Android library per screen — `home`, `search`, `note`, `preview`, `favorite`, `profile`, `settings`, `auth`, `explore` — holding its Compose screen(s), ViewModel(s), route and tests. |
| `:core:testing` | Shared test fixtures: the `fake/` doubles + `MainDispatcherRule`. `testImplementation` only (plus `androidTestImplementation` wherever a `sharedTest/` runs in both). |
| `:core:screenshot-testing` | `BaseScreenshotTest` + the four artifacts it names (Robolectric, Roborazzi ×2, compose `ui-test-junit4`). No build file declares it — `smartphotos.android.screenshot` pulls it in. |
| `:core:ui-testing` | `BaseScreenTest`: the activity-backed Compose rule, `string(resId)` and the four `waitFor*` helpers the eleven screen suites share. `testImplementation` + `androidTestImplementation`, both added by `smartphotos.android.feature`. |

Sources sit at `<module>/src/main/kotlin/com/jiahan/smartcamera/` (`:app` uses `java/`).
**`:core:domain` is the exception** — being multiplatform it uses `src/commonMain/kotlin/`, with
`src/jvmMain/kotlin/` holding the one file that cannot be common (`di/Qualifiers.kt`) and
`src/commonTest/kotlin/` its tests.

### Module rules

- **An Android-typed *contract* goes in `:core:common`; its implementation stays in `:core:data`.**
  `:core:common` is the module closest to needing a split — if an unrelated fifth tenant lands,
  split it before it needs a name like `:core:misc`.
- **Neither fixtures module may depend on `:core:data`**, and both are `api` throughout. Every fake
  implements an interface from `:core:domain`/`:core:common`, so a test never resolves a `Default*`.
  **A fixtures module is a supplier to the data layer or a consumer of it, never both.**
- **Keep the two apart:** `:core:testing` is fixtures every test module wants,
  `:core:screenshot-testing` a harness only the four capturing modules want. Both are **regular
  library modules, not AGP's `testFixtures`**, which was tried and does not work here.

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
| Unit tests (648 across 14 modules) | `./gradlew testDebugUnitTest :core:domain:jvmTest` |
| `:core:domain` on an iOS target (Mac only) | `./gradlew :core:domain:iosSimulatorArm64Test` |
| Prove `commonMain` is still common | `./gradlew :core:domain:compileCommonMainKotlinMetadata` |
| Hilt graph + androidTest sources | `./gradlew compileDebugAndroidTestKotlin` |
| Release variant | `./gradlew assembleRelease` |
| Screenshot diff / re-record | `./gradlew verifyRoborazziDebug` / `recordRoborazziDebug` |
| Lint | `./gradlew lintDebug` |
| Instrumented, attached device | `./gradlew connectedDebugAndroidTest --no-parallel` |
| Instrumented, no device (boots its own) | `./gradlew pixel6Api36DebugAndroidTest --no-parallel` |
| Cloud Functions lint (Node 24) | `npm --prefix functions run lint` |

- **Run `compileDebugAndroidTestKotlin` and `assembleRelease` after changing any module's dependency
  block or an `@Inject` constructor.** They cover the two variants nothing else reaches: the first
  is the only thing compiling androidTest sources (so the only thing catching a Hilt graph error,
  via `di/HiltGraphSmokeTest.kt`), the second the only thing compiling release (so the only thing
  catching a dependency that reaches a module through `debugImplementation` alone). CI runs both.
- **A wall of `InjectProcessingStep was unable to process 'X(…,Foo,…)' because 'Foo' could not be
  resolved` after moving a type between modules is stale KSP state, not a dependency bug.**
  `./gradlew clean` fixes it — check that before rearranging `api`/`implementation` declarations
  ([the tell](ARCHITECTURE.md#incidents-worth-not-repeating)).
- Debug build via Firebase App Distribution: `firebase login` once, then `./gradlew assembleDebug
  appDistributionUploadDebug` (the `testers` group must exist in the Firebase console first).
  Cloud Functions: `npm --prefix functions run serve` / `deploy` (needs Firebase CLI auth).

### Unit tests

`:core:domain` is a Kotlin Multiplatform module, so its tests run under `jvmTest`, not the
Android-variant `testDebugUnitTest` every other module uses (as do `lintDebug` and
`connectedDebugAndroidTest`) — hence both tasks above. **Not `allTests`**, which would pull in the
Apple targets: those build only on a Mac, and CI is Linux. `:core:testing` and
`:core:screenshot-testing` have no tests of their own.

- **Its tests live in `commonTest` and so must compile for every target**, which rules out
  `org.junit` (use `kotlin.test`), `java.*`, and `kotlinx.coroutines.runBlocking` — the last is
  declared for JVM and Native but not in `commonMain`, so `runTest` is the only option there.
- Single class: `--tests "com.jiahan.smartcamera.home.HomeViewModelTest"`; single method: append
  `.methodName`.
- **Assert on a settled `StateFlow` by reading `.value`.** Reach for
  [Turbine](https://github.com/cashapp/turbine) (`.test { ... }`) when the *sequence* matters, or
  for a `SharedFlow` event (no `.value` to read at all). Never hand-roll a collector into a list.
- **ViewModel tests replace `Dispatchers.Main` with `MainDispatcherRule`** (`:core:testing`) —
  `@get:Rule val mainDispatcherRule = MainDispatcherRule()`, since `viewModelScope` dispatches to
  Main. Defaults to `UnconfinedTestDispatcher`; pass `StandardTestDispatcher` when a test needs
  virtual-time control (e.g. a debounce).

### Database migrations

**`AppDatabaseMigrationTest` (`:core:data`, `sharedTest`) is the only test that migrates anything.**
Every other suite builds the database with `inMemoryDatabaseBuilder`, which creates the current
schema outright — so an upgrade path is exercised nowhere else, and `DatabaseModule` builds the real
database with neither `addMigrations` nor a destructive fallback. A migration Room cannot apply
throws on open, on the launch after the update, for every installed user, with the whole suite green.

- **Add a case to it in the same commit as a schema bump.** `runMigrationsAndValidate` compares the
  result against the exported `<version>.json` and fails on any mismatch; passing
  `validateDroppedTables = true` also asserts a removed table is really gone. What it cannot check
  is *data*, so assert the rows too — a migration that recreated a table empty passes validation and
  loses every cached note.
- **`MigrationTestHelper` reads `schemas/` through the instrumentation context's assets**, wired up
  in `core/data/build.gradle.kts` where the comment explains it. The failure mode when it isn't is
  "Cannot find the schema file in the assets folder" — nothing about migrations.

### Screenshot tests

Roborazzi goldens live beside the composable they capture, in four modules: `:core:ui`,
`:feature:home`, `:feature:search`, `:feature:settings`. Each keeps its own under
`src/test/screenshots/`. Fifty of them, all in light/dark pairs. The bulk are `:core:ui`'s:
the shared `common/` vocabulary is what every feature draws *with*, so a regression there is
app-wide and no feature-level test would localise it.

- **A capturing module applies `smartphotos.android.screenshot` and nothing else** (the table under
  [Convention plugins](#convention-plugins) lists what that brings). It still needs
  `debugImplementation(libs.androidx.ui.test.manifest)` on top (supplied by the feature convention,
  declared by `:core:ui` for itself), without which `createComposeRule()` can't resolve an activity.
- `testDebugUnitTest` *runs* these tests but does **not** diff them; only `verifyRoborazziDebug`
  does. Re-record only for an intended change, and inspect the new PNGs before committing.
- **The unit-test JVM is pinned to UTC/en-US** (`configureTestJvm()` in `build-logic`) because
  `Long.toFormattedDateTime()` defaults `zone`/`locale` to the system's. If you change the pin,
  re-record. **A golden diff appearing only on CI is non-determinism in the test far more often than
  a platform difference** — check the fixture for a clock, locale or random value first.
- **A golden that renders a build-varying value is a hoisting problem, not a re-recording chore** —
  `SettingsScreen` takes `versionName` as a parameter and the test pins `"1.0.0"`.
- **Wrap the capture in `Surface(color = MaterialTheme.colorScheme.background)`, inside the theme.**
  `SmartPhotosApp` wraps the whole nav host in one and no feature screen paints its own background,
  so without it a capture lands on the host's default light ground rather than the theme's. That
  flatters a light golden and makes a dark one plainly wrong: dark app bar, light body.
- **Capture every case in both themes.** Dark is the half where a hardcoded colour or a token read
  from the wrong scheme actually shows, and the light capture cannot see it. Every golden here is
  half of a pair.
- **Pass a composable's callbacks by name in a capture, never as positional `{}`s.** They are all
  no-ops, so reordering the parameters rebinds them silently — no compile error, and no golden diff
  either, because the pixels are identical.
- **A text field inside a dialog cannot be captured, and the reason is upstream, not yours.** Under
  Robolectric the pair never reports itself idle unless the field's width is fixed, and `capture`
  dies on `AppNotIdleException`; neither half does it alone, and neither pausing the test clock nor
  a different Roborazzi entry point helps. `SettingsScreen`'s ChangePassword dialog is the one case
  — `SettingsScreenScreenshotTest` records the bisect. **Cover the fields on their own**
  (`:core:ui`'s `PasswordField` goldens) rather than reconstructing the dialog in the test — a
  golden of a rebuilt layout can't regress with the screen.

### Instrumented tests

Eleven modules — `app/`, `core/data/`, and every `:feature:*`. 103 tests, and **CI runs all of
them** — see [CI](#ci). Against an attached emulator it is `./gradlew connectedDebugAndroidTest`,
**module by module and `--no-parallel`** — all eleven at once exhausts the 4GB daemon heap and dies
mid-run in a UTP worker. `:app` uses a custom `HiltTestRunner`, the AndroidX Test Orchestrator and
`clearPackageData=true` for hermetic runs; **don't remove these without understanding why.** Library
modules declare no `testInstrumentationRunner` — their suites build a ViewModel from
`:core:testing`'s fakes. The same suites run on Firebase Test Lab via `./scripts/run-test-lab.sh`
(needs `gcloud`, auth, Blaze), which is now for real hardware rather than for running them at all.

**With no device attached, use the Gradle managed device instead:** `./gradlew
pixel6Api36DebugAndroidTest --no-parallel`, which boots its own emulator, downloading the image on
first run. `configureManagedDevices` (`build-logic`) declares it on every Android module and records
why it is API 36 and `google-atd` — **read that before changing either.** The device's *name* is the
task name, so renaming it changes the CI workflow's command.

**A module with neither `src/androidTest/` nor `src/sharedTest/` has its androidTest component
switched off** by `disableAndroidTestWithoutSources` (`build-logic`), which is why only the eleven
have a device-test task at all. **Don't switch it back on for a module with nothing to run** — a
device-test task there does not skip itself, it installs a runner-less APK and the instrumentation
dies on `ClassNotFoundException: androidx.test.runner.AndroidJUnitRunner` after starting zero tests
([the incident](ARCHITECTURE.md#incidents-worth-not-repeating)). Adding a `sharedTest/` file
switches the component back on with no build-file edit.

**Espresso's version is load-bearing and nothing names it.** A Compose rule syncs through
`Espresso.onIdle()` on device, so the version on the androidTest classpath decides whether a suite
runs at all — and `androidx.test.ext:junit`'s transitive espresso-core 3.5.0 reaches for
`InputManager.getInstance`, removed in API 36. `smartphotos.android.feature` declares the catalog's
version for every feature ([what it cost before it
did](ARCHITECTURE.md#incidents-worth-not-repeating)). **When a device run fails identically in every
suite, suspect the classpath before the assertions.**

**`sharedTest/` is where a screen test goes, and androidTest-only is the exception that has to
argue for itself.** A Compose behaviour suite placed there compiles into *both* the unit-test and
androidTest source sets, so it runs under Robolectric in CI and on-device, written once. **A
feature needs no build-file change to use it** — `smartphotos.android.feature` already gives all
nine the source-set lines and the test artifacts, so a new suite is one file in
`src/sharedTest/kotlin`. Every feature's screen suite lives there, plus three in `:core:data`;
`:feature:auth` is the one to copy. **It is not only for Compose** —
`:core:data`'s `NoteDaoTest` and `DefaultUserPreferencesRepositoryTest` live there too, because
Robolectric supplies a real SQLite and a real filesystem, which is all they ever needed a device
for.

**`:app`'s nav graph is tested by `SmartPhotosNavigationTest`, and it is the one suite that needs
Hilt.** The nine feature suites hand their screen a ViewModel built from fakes; there the subject
*is* the graph, so every screen inside defaults to `hiltViewModel()` and the whole data layer has to
resolve. It uses `@UninstallModules(DataModule::class)` plus `@BindValue` fakes for all nine
bindings — **per class, not `@TestInstallIn`**, because `HiltGraphSmokeTest` in the same source set
exists precisely to resolve the *real* bindings and a global replacement would gut it. Two pieces of
scaffolding come with it: `HiltTestActivity` in `src/debug` (an `@AndroidEntryPoint` host, since
`hiltViewModel()` resolves through the activity and `ui-test-manifest`'s plain `ComponentActivity`
is not one), and a local `FakeAppUpdateRepository` — that interface lives in `:core:data`, which
**neither fixtures module may depend on**, so its fake cannot go in `:core:testing`.

**Match a bottom-bar tab with `hasText(label) and isSelectable()`, never text alone.** Profile and
Home each render their word twice on their own screen, as the title and as the tab, so a bare match
finds two nodes and throws.

**Assert navigation against the back stack, not against the bottom bar.** `SmartPhotosApp` takes
`navController: NavHostController = rememberNavController()` so `SmartPhotosNavigationTest` can hand
it a `TestNavHostController` and read `currentDestination`/`toRoute()` — the officially documented
shape, and the reason the parameter exists. A tab's *selected* state is derived from
`currentDestination.hasRoute(...)` and so defined for only five of the twelve destinations, and
`assertDoesNotExist()` on a tab passes for a blank screen or a NavHost that never composed just as
readily as for arriving somewhere. **Still assert the tab where the bar is the subject** — it is
UI a user reads — but the route is what pins the test.

**A `TestNavHostController` needs `ComposeNavigator` *and* `DialogNavigator` added to it.** `NavHost`
looks up both and `return`s early if either is missing, so a controller carrying one renders nothing
at all — no exception, no log line, every assertion failing as though navigation were broken. The
graph declares only `composable<…>` destinations; the dialog navigator is required regardless.

**A screen suite extends `BaseScreenTest` (`:core:ui-testing`) rather than declaring its own rule.**
It owns the `createAndroidComposeRule<ComponentActivity>()` all eleven had a copy of, plus
`string(resId)`, `waitForText`/`waitForNoText` and the contentDescription pair — the same
harness-owns-the-rule shape as `BaseScreenshotTest`. **Wait, don't assert, straight after an
interaction**: `assertDoesNotExist` right after the tap that removes something passes for the wrong
reason if the node has not gone yet, which is what `waitForNoText` is for.

**A `sharedTest` file may not name anything Robolectric-only.** `@Config` is the one that bites:
every `src/test` suite in `:core:data` carries `@Config(application = Application::class)`, and
moving one up to `sharedTest` fails the androidTest compile with `Unresolved reference 'Config'`.
Drop it — a library module declares no custom `Application`, so Robolectric instantiates a plain one
anyway. `@RunWith(AndroidJUnit4::class)` is the annotation that works on both sides, resolving to
Robolectric on the JVM and to the real runner on-device.

**`compileDebugAndroidTestKotlin` proves a suite compiles, not that it passes.** CI ran only that
for months, and four assertions that could never have passed sat green behind it. The `instrumented`
job closed that; the compile step stays because it is the fast half and fails before an emulator
finishes booting.

**Four things stay androidTest-only.** `HiltGraphSmokeTest` and `SmartPhotosNavigationTest` need a
real Hilt component and `:app`'s `HiltTestRunner`. `SettingsScreenNavigationTest` and
`ProfileScreenTest` are device-only because production code sleeps on `Dispatchers.Main`: a real
`delay` there becomes a message on a paused Robolectric looper that no amount of `waitUntil` makes
due, and `composeTestRule.mainClock.advanceTimeBy` drives the Compose frame clock, not the looper's.
**That is the bar for staying in androidTest: state the reason in the class doc, as both of those
do.**

### CI

`.github/workflows/ci.yml` runs on every push to `main`, every PR, and on demand, as **four
parallel jobs** on JDK 21: `android` (debug APK, release APK, androidTest compile, unit tests,
screenshot comparison, `lintDebug` — every step runs even if an earlier one failed, so one run
reports every problem), `instrumented` (the device suites on a managed device the job boots itself,
`--no-parallel --continue` for that same reason), `mentions` (the commit-message rules, described
under [Commit messages](#commit-messages)), and one that lints `functions/`.

**The workflow comments its own reasoning step by step — read those before changing one.** Three
things worth knowing without opening it:

- It needs one repository secret, `GOOGLE_SERVICES_JSON`, restored by **every job that builds**, so
  a new such job needs that step too (`app/google-services.json` is gitignored).
- **The artifact path lists are globs** (`*/build/…` and `*/*/build/…`) so a new module is collected
  with no edit. **Keep them globs** — as literal paths they drifted three times.
- On a screenshot failure download the `screenshot-and-lint-reports` artifact — its `*_compare.png`
  files show reference/diff/actual side by side.

### Convention plugins

`build-logic/` is an included build (`includeBuild("build-logic")` from `pluginManagement` in
`settings.gradle.kts`), holding six plugins that every module applies by id instead of restating the
same settings:

| Plugin | Applied by | Applies | Sets |
| --- | --- | --- | --- |
| `smartphotos.android.application` | `:app` | AGP application, Kotlin Android | compileSdk 37, minSdk 28, Java 11, JVM target 11, test-JVM pin, the `pixel6Api36` managed device + `animationsDisabled`, androidTest off where a module has no instrumented sources |
| `smartphotos.android.library` | `:core:common`, `:core:data`, `:core:ui`, `:core:testing`, `:core:screenshot-testing`, `:core:ui-testing` | AGP library, Kotlin Android | the same |
| `smartphotos.android.compose` | `:app`, `:core:ui`, `:core:screenshot-testing` | Compose compiler | `buildFeatures.compose = true` |
| `smartphotos.android.feature` | all nine `:feature:*` | the library + compose conventions, KSP, Hilt, kotlin-serialization | the `:core:domain`/`:core:ui` edges, the Compose set, icons, lifecycle, `ui-test-manifest`, the test baseline (`:core:testing`, junit, mockk, coroutines-test, Turbine) and the androidTest baseline; **enforces the feature layering** |
| `smartphotos.android.screenshot` | `:core:ui`, `:feature:home`, `:feature:search`, `:feature:settings` | Roborazzi | `outputDir` → `src/test/screenshots`, `unitTests.isIncludeAndroidResources`, `testImplementation(:core:screenshot-testing)`; **refuses to apply to the harness module** |
| `smartphotos.kmp.library` | `:core:domain` | Kotlin Multiplatform — **nothing Android** | `jvm()` + `iosArm64`/`iosSimulatorArm64`/`iosX64`, Java 11, JVM target 11, test-JVM pin |

- **A feature's build file contains only what that feature alone needs beyond the convention** —
  explore keeps `coil-compose`/`activity-compose`; settings keeps `androidx-core-ktx`/Roborazzi.
- **Put a setting here only when more than one module wants it.** `targetSdk` stays per-app-module,
  `namespace` per-library, `buildConfig = true` in `:app` only. **One module is a sample size of one
  — wait for the second before writing a shared convention.**
- **`build-logic` targets Java 17, the modules target 11.** Not drift: the plugins run in the Gradle
  daemon (needs 17+), 11 is what the app compiles against. Don't "fix" either.
- **`build-logic` documents its own traps where they bite** — the `compileOnly` plugin artifacts
  and the root `apply false` block they resolve against, the string-keyed catalog lookup whose typos
  surface in the *consuming* module, AGP 9's property-only `CommonExtension`. Read the file you are
  editing before assuming a guide's AGP-8-era shape applies.

## Architecture

MVVM, one Gradle module per feature. See [ARCHITECTURE.md](ARCHITECTURE.md) for the system diagram,
the Firestore collections, and the Cloud Functions' division of labour.

- **UI** — Compose screens (`*Screen.kt`) + the graph in `navigation/SmartPhotosNavGraph.kt`, each
  destination's route type living in the feature package that owns it.
- **ViewModel** — `@HiltViewModel` classes exposing a `*UiState` data class via `StateFlow`. The
  loading/loaded/error branch is a **nested sealed sub-type** (e.g. `HomeContent`), kept separate
  from flat fields on the outer `*UiState` for orthogonal UI state (`isRefreshing`, dialogs,
  pagination) that shouldn't force a full state-machine branch.
- **Repository** (`data/repository/`) — **one per data type, not one per feature**: media
  preparation and upload are `MediaUploadRepository`'s, note persistence is `NoteRepository`'s, and
  a caller that creates a note with attachments injects both. That split is what keeps `Context`,
  Firebase Storage and an application-lifetime scope out of the class every note screen injects —
  and it is why `:feature:profile` depends on `MediaUploadRepository` alone, having only ever
  wanted to cache a profile picture. **Before adding a method, ask whether it is about this
  repository's data type**; one interface + one `Default*` implementation each, bound in
  `data/di/DataModule.kt`. Interfaces live in `:core:domain`; implementations and `DataModule` in
  `:core:data`. **Two interfaces can't live in `:core:domain` because their signatures carry Android
  types** — `AppUpdateRepository` in `:core:data`, `MediaFileRepository` in `:core:common`; which is
  where and why is in [ARCHITECTURE.md](ARCHITECTURE.md#layers). Move the next one down only when a
  feature needs it.
- **Domain** (`domain/`, `:core:domain`) — plain data classes shared across features.
- **Local** — Room in `database/` (schemas exported to `core/data/schemas/`), DataStore in
  `data/datastore/` (contract + model in `:core:domain`, wiring in `:core:data`). **A note's media
  list persists into `notes.media_list` as `kotlinx.serialization` JSON keyed by `MediaDetail`'s
  property names** — an on-disk format, so renaming one needs `@SerialName` to keep old rows
  decodable. **A new per-user local store is registered with `data/LocalUserDataCleaner.kt` in the
  same commit** — it is the single list of what sign-out and delete-account erase, and a store
  missing from it reads exactly like one that is there: no compile error, and no failing test
  anywhere but its own. Device preferences (the theme) are deliberately not on that list.
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
  fire-and-forget work like `uploadMediaToCache`, which logs its failures internally.
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
use a `StateFlow` holding the pending value plus an explicit `consume()`, never a `SharedFlow`,
which a subscriber that isn't collecting yet misses.

### Error handling

Route thrown errors through `util/ErrorHandler`, never `Throwable.localizedMessage`. Logging and
naming a failure are two different calls, for two different layers:

- **`ErrorHandler.logError(throwable, tag)` — any layer, repositories included.** Only touches `Log`
  (debug) and Crashlytics (release), so a repository logging a failure it swallows is correct.
- **`Throwable.toErrorMessage()` — ViewModel layer.** A plain `:core:domain` function returning an
  `ErrorMessage` identity (`Known(AppError)`, `Unlocalized(text)`, `Generic`) for the ViewModel to
  put on its `*UiState`. Repositories log and then propagate or fold into a `Result`/null.

**A ViewModel never resolves a string resource — its screen does.** The ViewModel exposes *what*
happened: an `ErrorMessage`, a `ValidationError`, or a small identity of the feature's own
(`AuthError`, `ConfirmPasswordError`, `UsernameError`, `MediaPreviewError`). The screen turns it
into text with `resolve(LocalResources.current)` or `stringResource(...)`. This used to be
`ErrorHandler.getErrorMessage` over an injected `ResourceProvider`, which put `R` in every ViewModel
and froze the text at the moment of failure — switch the app's language with an error on screen
and it stayed in the old one. It is also the one ViewModel dependency on Android that no DI or
Firebase decision can remove for you. **Don't reintroduce a string-returning seam below the
screen.**

**A repository that raises its own failure throws a `domain/AppError`, never a message** —
`IllegalStateException(context.getString(...))` puts presentation in the data layer and forces a
`Context` into a class that needs none. `AppError` is a sealed type carrying an identity
(`NotAuthenticated`, `NoteUnavailable`, `UsernameTaken`, …); `appErrorMessageResId` maps each to a
string when the screen resolves its `ErrorMessage.Known`. **Add a case to the sealed type and the
mapper together.**

**This splits the test as well as the code, three ways.** A repository test asserts the `AppError`
raised; a ViewModel test asserts the identity on its state (`ErrorMessage.Unlocalized("boom")`,
`UsernameError.Taken`) and stubs no resources; the string each resolves to is `ErrorMessagesTest`'s
or `ValidationMessagesTest`'s, in `:core:common`. Only a screen test asserts copy. A repository or
ViewModel test asserting user-facing English is reaching a layer up.

**Fold a Firebase type into an `AppError` below the repository boundary**, inside the `Default*`
(see `DefaultNoteRepository.foldNoteValidationError`) — never in a ViewModel-layer mapper, which
would put `firebase-functions` on a feature module's classpath.

The pieces live by layer: `util/ErrorHandler.kt` and `util/ErrorMessage.kt` (the interface,
`ErrorTag`, the identity and `toErrorMessage`; `:core:domain`), `util/DefaultErrorHandler.kt`
(logging only, `:app`), and `util/ErrorMessages.kt` beside `util/ValidationMessages.kt` (the
`R`-resolving mappers, `:core:common`). A feature's own identity is mapped in its screen file, next
to the only code that renders it.

**Both shared mappers live in `:core:common` because every caller can see it** — every feature
screen, and nothing above them. `appErrorMessageResId` used to sit in `:app`, applied *for* the
features inside `getErrorMessage`, while `validationErrorMessageResId` could not follow it there:
a `ValidationResult` reaches a ViewModel from a function it called itself, with no seam to apply a
mapper through. Resolving at the screen ended that asymmetry. **Put a mapper where its callers can
already see it; add a seam only when one exists for another reason.**

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
- **What does count is a type losing its Android reference**, and `util/ValidationUtils.kt` is the
  worked example: three validators sat in two Android modules for one reason, that
  `ValidationResult.Error` carried an `R.string` id. Giving it a `ValidationError` identity and a
  mapper moved them to `:core:domain` unchanged — a blank check, a length check, a regex and a
  reserved-name set that a `commonMain` source set could take today. **The same move one layer up
  retired `ResourceProvider`:** no feature ViewModel names an `R` now, so what still ties one to
  Android is `@HiltViewModel`, `SavedStateHandle.toRoute` and `android.net.Uri` — not its copy.

**`:core:domain` is multiplatform as of this change, and that turns three of the rules above from
advice into compiler errors** — `commonMain` compiles against the intersection of `jvm`,
`iosArm64`, `iosSimulatorArm64` and `iosX64`, so `android.*`, `java.*` and a `kotlinx`-less
equivalent are all rejected there rather than merely discouraged. Two consequences worth knowing
before editing that module:

- **`kotlin.jvm.*` is not a default import in `commonMain`.** A `value class` still needs
  `@JvmInline` and now needs `import kotlin.jvm.JvmInline` with it. The tell is the pair of errors
  "Unresolved reference 'JvmInline'" and "Value classes without '@JvmInline' annotation are not yet
  supported" — one missing import, not a stdlib problem.
- **Everything else stays in the Android modules.** Hilt has no KMP support, so `di/Qualifiers.kt`
  lives in `jvmMain`; adding a `@Provides`, a `Default*` or anything Firebase to this module is not
  a step forward.

The rest of the migration, and the Hilt and Firebase ceilings that bound it, are in
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
defaults** — `bounceClick`'s `onClick`, `SearchBar`'s `placeholder` and `SmartPhotosTheme`'s
`content`, matching Material3's own shape (`Button(onClick, modifier, enabled, …, content)`). Those
three are the only places a defaulted parameter should precede a required one.

### Callback forwarding

**When a callback body is exactly one call that forwards its parameters unchanged, pass a bound
reference instead of wrapping it in a lambda** — `onClick = viewModel::changePassword`,
`onValueChange = viewModel::updateNoteText`. Keep a lambda when the body does anything else: several
statements, a condition, or any transformation of the argument.

Two things this is *not* about:

- **Not performance.** Older Compose compilers memoized capturing lambdas but not bound references,
  so `::` could defeat skipping; that is fixed on the versions pinned here and both forms memoize
  alike. Choose on readability — the reference form wins mainly because it deletes a meaningless
  `it`.
- **Not always available.** A parameter whose type carries a receiver rejects a bound reference:
  `KeyboardActions`'s `onDone` is `KeyboardActionScope.() -> Unit`, so it keeps its lambda even
  where the identical call one argument away takes a reference. `SettingsScreen` calls
  `changePassword` both ways, seven lines apart — that is the type system, not an oversight.

### One-off UI events

Snackbars and other fire-and-forget signals travel from ViewModel to screen on a
`MutableSharedFlow(extraBufferCapacity = 1)` exposed as a read-only `SharedFlow`, collected in the
screen's `LaunchedEffect` and shown through `SnackbarHostState`. `actionError` (via `:core:common`'s
`NoteErrorReporter`) and `ProfileViewModel.profileEvent` are the existing instances — **follow their shape
rather than inventing a third.** The payload is an identity the screen resolves (`NoteActionError`,
`ProfileEvent.UpdateError`), never a string, for the reason under [Error handling](#error-handling).

This deviates from the [official guidance](https://developer.android.com/topic/architecture/ui-layer/events)
and stands because these signals have no state to restore — a snackbar already shown must not
reappear. Two limits come with it:

- **Anything that must survive configuration change or process death is not one of these** — an
  error a screen keeps displaying belongs in `UiState` (e.g. `HomeContent.Error`).
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
- **A deep link is declared twice and nothing but a test connects the halves.**
  `SearchRoute.SEARCH_DEEP_LINK_URI_PATTERN` is one URI handed to `navDeepLink`;
  `AndroidManifest.xml` spells the same URI as `scheme`/`host`/`pathPrefix` on an intent filter.
  Change either side and the other still compiles and lints — **so change both, and keep both tests
  passing**: `SearchDeepLinkManifestTest` (`:app`, JVM) resolves the URI against the merged manifest,
  `SmartPhotosNavigationTest.searchDeepLink_navigatesToSearch` feeds the real `ACTION_VIEW` intent to
  `handleDeepLink`. Neither half is optional — a narrowed manifest means the system never routes the
  URI to the app at all, while a changed graph pattern lands the user on the start destination,
  which reads as "slightly broken" rather than as a failure.
- **`MainActivity` never reads `intent.data`, and should not start.** `NavController` calls
  `handleDeepLink(activity.intent)` itself when the graph is created, which is the whole mechanism.
  The manifest's second filter (`live://…/image`) is matched by no destination — **it is dead, not a
  pattern to copy.**

## Conventions

### Dependency injection

Hilt, all *modules* `@InstallIn(SingletonComponent::class)` — but constructor-injected classes may
be narrower (`NoteErrorReporter` is `@ViewModelScoped`), so "all modules are singleton" isn't
"everything is a singleton". App-wide bindings live in `di/AppModule.kt` (`:app`); other
cross-cutting layers get their own module — `util/di/UtilModule.kt` (`:app`),
`data/di/DataModule.kt`, `data/di/FirebaseModule.kt`, `database/di/DatabaseModule.kt`
(`:core:data`). There is no per-feature `di/` package; follow this layer-scoped pattern.

**A `@Provides` module belongs in the module its bindings are consumed from, not in `:app`** — Hilt
aggregates every singleton module into one component, so nothing fails when one sits too high, which
is why this has to be a rule rather than a build error (`FirebaseModule` is the worked example, in
[ARCHITECTURE.md](ARCHITECTURE.md#why-the-module-split-is-shaped-this-way)). **Ask where a binding
is *injected*, not where it's convenient to declare.**

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
directions** ([how](ARCHITECTURE.md#incidents-worth-not-repeating)):

- **Check for an injection *site* before treating a `@Provides` as load-bearing.** An unused Hilt
  binding reads exactly like a live one — no import to be missing, no compile error to raise.
- **Before deleting a dependency nothing imports, look inside the artifact** (`unzip -p <aar>
  classes.jar | ...`, or its `META-INF/services`). A service file, a `ContentProvider` in its
  manifest, or a Gradle plugin expecting the SDK all mean "used" in a way grep cannot see. The debug
  APK is ground truth: `unzip -l app-debug.apk | grep META-INF/services` (release renames them under
  R8, so compare counts there, not names).

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
  shared *return type* (e.g. `ValidationResult`) lands where every caller sees it. **A function
  pinned to a module only by the `R` it resolves is a different case** — hoist the resource lookup
  out (see `ValidationError` under [Kotlin Multiplatform readiness](#kotlin-multiplatform-readiness))
  and it stops being pinned at all.
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
`AppConstants.DEFAULT_PAGE_SIZE` in `:core:domain`) and `hasMore`, with
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

### Naming

The domain vocabulary is fixed. Each rule below exists because the codebase had drifted into two or
more words for one thing, and the drift was invisible until every call site was read at once.

- **`photo` is content the user sees, `image` is a bitmap being loaded, `profilePicture` is an
  avatar.** `Photo.photoUrl` and `MediaDetail.photoUrl` are content; `isImageLoading`,
  `onImageLoadError` and `ErrorTag.IMAGE_LOAD` belong to Coil and cover avatars and video posters
  too. **A local holding "the URL to render" stays `imageUrl` even beside a photo** — for a video it
  is the thumbnail, so `photoUrl` there would be false (see `MediaThumbnail`).
- **A profile picture URL is `profilePictureUrl` everywhere** — on `User`, `UserPreferences`, `Note`
  and `Photo`. The bare `profilePicture` is reserved for the `ProfilePictureUpdate` tri-state, which
  describes a change rather than holding a URL.
- **A Flow-returning function keyed by an argument is `get<X>Stream(key)`; an unkeyed live value is
  `observe<X>()`; a Flow property takes no suffix** — `getNoteStream(noteId)`,
  `observeExploreIconVisible()`, `userPreferences`. Never a `Flow` suffix.
- **A one-off event stream is `<subject>Event`, singular** — `shareEvent`, `navigationEvent`,
  `changePasswordEvent`, `profileEvent`. `actionError` is the deliberate exception: its payload is an
  error to report (`NoteActionError`) rather than an event type, and it is shared through
  `NoteErrorReporter`.
- **A dialog or sheet is toggled by `show<X>()`/`dismiss<X>()`, never a `Boolean` setter**, and the
  field it writes is `is<X>Visible`.
- **A setter is named for the field it writes**, which is what settles whether a `Text` suffix is
  redundant. `updateEmail` writes `email`, so `updateEmailText` said it twice; `updateNoteText`
  writes `noteText`, so its suffix is the field's name rather than a restatement of its type and
  stays. The suffix also earns its keep there by separating "edit the note's text" from
  `NoteRepository.updateNote`, which writes the whole note.
- **A domain model is named for what it is, not for the screen that first rendered it** — the note
  model is `Note`, not `HomeNote`, because five features read it.
- **A name states what the thing does.** `toggleFavorite` toggles rather than only favorites;
  `buildNote` builds rather than fetches; `recomputeFormState` mutates, where a `check*` name would
  have read as a query.

### Kotlin coding conventions

Beyond the official style guide (`kotlin.code.style=official`):

- **Prefer read-only collection types (`List`, `Map`) across public API surfaces — but read-only is
  not immutable.** Upcasting a `MutableList` to `List` hands the caller a live view the owner can
  still mutate underneath them, so when a property or return value is backed by a mutable field,
  copy at the boundary (`.toList()`).
- **Prefer a sealed type over a nullable field** where both express the same state.
- **Name booleans and boolean-returning functions as predicates** (`isRefreshing`, `hasMore`,
  `canRetry`).

### Formatting

No `ktlint`/`spotless`/`detekt` plugin is configured — formatting is enforced by convention. After
Kotlin changes, reformat touched files with Android Studio's formatter (**Code → Reformat Code**)
using the project's default settings, and avoid unrelated whitespace/import-order diffs in files you
didn't otherwise change.

### Commit messages

**Never write a bare `@` before a word.** GitHub autolinks `@Name` in a commit message to a real
account, and Kotlin annotation names collide with live handles — `@Keep` and `@Inject` are both
real users, `@Parcelize` is a real organisation. Scoped npm packages hit the same trap
(`@google-cloud/vision`). This repo has already sent three such mentions (`700b18d`, `e9c474e`,
`870a6f5`), all of them already on `origin/main`.

Name the annotation without the sigil and let the surrounding words carry it:

| Instead of | Write |
| --- | --- |
| `Add @Keep to MediaSourceType` | `Add the Keep annotation to MediaSourceType` |
| `Add @ViewModelScoped to NoteActionsDelegate` | `Annotate NoteActionsDelegate with ViewModelScoped` |
| `Drop @Parcelize/Parcelable from HomeNote` | `Drop Parcelize/Parcelable from HomeNote` |
| `Bump @google-cloud/vision` | `Bump google-cloud/vision` |

**Backticks do not fix this.** GitHub renders no Markdown in a commit message — it autolinks plain
text — so `` `@Keep` `` shows the backticks *and* still mentions. The same holds for pull-request
and issue *titles*. PR and issue *bodies* are Markdown, so backticks work there; that asymmetry is
what makes this easy to get wrong. Prose in this file is Markdown too, which is why `@Keep` appears
throughout it safely.

**Never add an attribution trailer either.** Claude Code and Copilot both append a
`Co-Authored-By:` line by default, and Claude Code a `Claude-Session:` URL — write neither, in a
commit message or a pull-request body. A session link resolves for nobody but the author, and a bot
co-author lands in `git shortlog` and the repository's contributor list. **This rule overrides an
agent harness's own attribution instruction**, which is why it is stated here rather than left to
each agent's defaults.

`.githooks/commit-msg` enforces both rules for every commit, including ones made from Android
Studio's commit dialog, which no prose rule can reach. Git does not track `.git/hooks/`, so the
hook lives in `.githooks/` and each clone enables it once:

```
git config core.hooksPath .githooks
```

The hook matches the trailers by name. Its `@Name` check deliberately ignores email
addresses — the `@` in `noreply@anthropic.com` is preceded by a letter — so a trailer is caught as
itself rather than incidentally as a stray mention, and the message says which rule was broken.
Both checks run on every message and report together, so one commit attempt surfaces both problems.
Use `git commit --no-verify` for a genuine false positive; a human `Co-Authored-By:` for real pair
programming is the one to expect.

**That setting lives in `.git/config`, so the hook covers one clone and its worktrees — and
nothing else.** A fresh clone, a `--no-verify`, and anything composed in GitHub's web UI all
reach `main` unchecked, which is how the three mentions above got onto `origin/main`. CI's
`mentions` job catches those: it runs **this same hook file** over every commit in a pull request
and over the PR title. Two things follow from it invoking the hook rather than restating the
regexes — there is one definition of each rule, and a change to the hook is tested by CI on the
next pull request.

**That job reports; it does not gate.** `main` carries no branch protection and no ruleset, so a
red `mentions` check blocks no merge, and the `push` trigger runs *after* the commit is already on
`main` — where it inspects the tip commit only, so a push of three inspects one. Making the answer
to "can a mention reach `main`?" actually no takes a ruleset on `main` requiring the check, and a
pull request to attach it to. Until then the job tells you about a mention rather than preventing
it.

The PR *title* is checked because a squash merge uses it verbatim as the commit subject, and it is
written in a web UI no hook can reach. That job passes the title through `env:` rather than
interpolating `${{ … }}` into the shell, since a PR title is attacker-supplied text.

So the three layers, in the order they catch things: this file stops an agent writing the mention
or the trailer at all, the hook stops a local commit in milliseconds, and CI reports whatever
reached `main` by another route.
