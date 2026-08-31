# SmartPhotos

Android app (Kotlin) for organizing photos/notes with ML-based tagging.
Firebase backend + a Node.js Cloud Functions project live in `functions/`.

Ten Gradle modules, plus an included build for the shared Gradle config:

- `:app` — UI, ViewModels, navigation, and the Android plumbing that is genuinely app-level:
  `MyApp`, the messaging service, `DefaultErrorHandler`/`ResourceProviderImpl`, `FirebaseModule`
  and `AppModule`. Sources under `app/src/main/java/com/jiahan/smartcamera/`.
- `:core:domain` — a pure Kotlin JVM module (no Android Gradle plugin, no Hilt plugin) holding the
  domain models, the repository *interfaces*, `safeCall`, the `ErrorHandler` interface and the DI
  qualifiers. Sources under `core/domain/src/main/kotlin/com/jiahan/smartcamera/`.
- `:core:common` — an Android library holding the shared vocabulary that is Android-bound but not
  Compose: `validateUsername`/`validateDisplayName` and the ten username/name/email strings, plus
  the `MediaFileRepository` contract and `util/MediaUriExt.kt`. It exists because `:feature:auth`
  needed those validators and neither neighbour could take them — `:core:domain` has no Android
  plugin, so it cannot hold string resources; `:core:ui` is the Compose vocabulary, and a validator
  is not a composable. `:feature:profile` then added the other two, and they give the module its
  clearest rule: **an Android-typed contract belongs here, its implementation stays in
  `:core:data`.** `MediaFileRepository`'s signatures carry `Uri`/`Bitmap`, so `:core:domain` cannot
  hold it, and it sat beside `DefaultMediaFileRepository` until a feature needed to inject it.
  It also holds `note/NoteShareDelegate.kt` and `note/NoteErrorReporter.kt`, which came down when
  `NoteActionsDelegate` was inlined — the two `@ViewModelScoped` classes all four remaining feature
  packages share. **They are why this module has Hilt and KSP**, and that is the one thing to know
  before adding to it: Dagger generates a class's factory in the module that owns it, so a
  `@Inject constructor` here needs the processor here, not only in `:app` where the component is
  assembled. Deliberately **not** Compose: the compose convention is not applied, so anything
  Compose-shaped that lands here fails to compile rather than quietly making it a second
  `:core:ui`. DI is not what the charter excludes; Compose is. Sources under
  `core/common/src/main/kotlin/com/jiahan/smartcamera/`. **This is the module closest to becoming a
  `:core:misc`** — it has taken four unrelated tenants now, held together by a rule rather than a
  theme (Android-typed, not Compose, contract-here-implementation-in-`:core:data`). If a fifth
  lands, split it before it needs a name like `misc`.
- `:core:data` — an Android library holding every implementation that satisfies one of those
  contracts: the `Default*`/`Firebase*` repositories, the Room database, the DataStore wiring and
  `DataModule`. Sources under `core/data/src/main/kotlin/com/jiahan/smartcamera/`.
- `:core:ui` — an Android library holding the shared Compose vocabulary: `common/` (14
  composables), `ui/theme/`, and the two `util/` helpers whose only callers are in those packages
  (`DateTimeUtils`, `FlowUtils`). Sources under `core/ui/src/main/kotlin/com/jiahan/smartcamera/`.
  It exists so a feature package can become its own module — while shared Compose sat in `:app`,
  nothing could leave.
- `:feature:explore` — an Android library holding the Explore screen, its ViewModel and its route.
  The first feature module, and deliberately the smallest slice: its route carries no arguments, so
  no ViewModel reads it back. It depends on `:core:ui` and `:core:domain` and **nothing else** — not
  even `:core:data`, since the repositories it injects are interfaces and Hilt binds them up in
  `:app`. Sources under `feature/explore/src/main/kotlin/com/jiahan/smartcamera/`.
- `:feature:settings` — the second feature module: the Settings screen, its ViewModel, its route and
  `validateNewPassword`. Same dependency shape as `:feature:explore`, and chosen at the time for a
  property that turned out to be the wrong criterion — see `:feature:auth` below. Three feature
  packages import nothing from `note/` — `auth`, `profile`, `settings` — and of those, settings was
  the only one with no reference *to* it outside `navigation/`, which read like the safest first
  cut. Sources under `feature/settings/src/main/kotlin/com/jiahan/smartcamera/`. The five packages
  left in `:app` — `home`, `search`, `favorite`, `preview`, `note` — no longer share
  anything but `NoteHandler`, and only `home` imports that; see
  [Cross-feature communication](#cross-feature-communication).
- `:feature:auth` — the third feature module: the Auth screen, its ViewModel, its route and its
  `sharedTest` Compose suite. It corrects something the settings entry above implies. Settings was
  picked for having no upward reference outside `navigation/`, and auth *does* have some —
  `MainViewModel`, `SmartPhotosApp` and `NavTransitions` all name `AuthRoute` — but every one of
  those is in `:app`, pointing *down*, which is the direction the dependency already runs. What
  decides whether a feature can move is what it reaches **up** for, and auth's list was three
  items: the shared validators (went down to `:core:common`), `usernameErrorMessageResId` (deleted,
  not moved — see [Error handling](#error-handling)) and `R.mipmap.ic_launcher`, which became a
  `logoRes` parameter for the same reason `versionName` did. Sources under
  `feature/auth/src/main/kotlin/com/jiahan/smartcamera/`.
- `:feature:profile` — the fourth: the Profile screen, its ViewModel and its route. It is the one
  the settings entry above named as blocked, and the block turned out to be two files rather than
  an architectural problem — `ProfileViewModel` injects `MediaFileRepository` and calls
  `toMediaUri()`, both of which came *down* to `:core:common` as a pure `git mv`, leaving
  `DefaultMediaFileRepository` in `:core:data`. **Read a `:core:data` injection as a two-file move
  until proven otherwise, not as a reason a feature cannot be extracted.** Its one interesting
  resource is `profile` itself, which the screen title and `TopLevelDestination`'s bottom-bar label
  both render: same word, same destination, so the string travelled here and `:app` reads it back
  as `ProfileR.string.profile` — see the `R`-alias notes under [Conventions](#conventions). Sources
  under `feature/profile/src/main/kotlin/com/jiahan/smartcamera/`.
- `:core:testing` — an Android library holding the fixtures more than one module needs: the nine
  `fake/` repository doubles, `MainDispatcherRule` and `BaseScreenshotTest`. Consumers take it with
  `testImplementation` (and `androidTestImplementation` in `:app`, whose `sharedTest/` runs in
  both), so nothing in it reaches a production classpath. It is a **regular library module, not
  AGP's `testFixtures`** — that was tried and does not work, since the Kotlin Android plugin creates
  no Kotlin compilation for the testFixtures variant (only `compileDebugTestFixturesJavaWithJavac`,
  `NO-SOURCE` against a `.kt` file; verified on Kotlin 2.4.10 / AGP 9.3.1). Its dependencies are
  `api` throughout, which inverts the usual advice for a good reason: a fixtures module's entire API
  surface is *other* modules' types — `FakeNoteRepository` **is** a `NoteRepository`, and a test
  assigning one to a ViewModel parameter has to resolve that interface. `:feature:auth` takes it
  in both test source sets for the same reason `:app` does — its `sharedTest/` runs in both.
- `build-logic/` — not a module but an included build, holding the five convention plugins
  (`smartphotos.android.application`, `.android.library`, `.android.compose`, `.android.feature`,
  `.jvm.library`) that carry `compileSdk`/`minSdk`, the Java 11 pair, the Kotlin JVM target, the
  unit-test JVM pin and the whole shared dependency set of a feature module.
  See [Convention plugins](#convention-plugins).

Kotlin package names are deliberately identical across all ten: `com.jiahan.smartcamera.util`,
`.di`, `.common`, `.data.repository` and `.data.datastore` each exist in more than one module,
which is what made the first two extractions a pure `git mv` with no import churn. A bare path like
`util/ErrorHandler.kt` below is therefore disambiguated by the module it is attributed to, not by
its package — `util/ErrorHandler.kt` is `:core:domain`, `util/DefaultErrorHandler.kt` is `:app`,
`util/DateTimeUtils.kt` is `:core:ui`, and `util/ValidationUtils.kt` and `util/MediaUriExt.kt` are
`:core:common`. The four feature modules follow the same rule: their Kotlin packages stayed
`com.jiahan.smartcamera.explore`, `.settings`, `.auth` and `.profile` when they moved, so
`SmartPhotosNavGraph`'s imports of them did not change at all — but their *namespaces* are
`com.jiahan.smartcamera.feature.explore`, `.feature.settings`, `.feature.auth` and
`.feature.profile`, so each `R` is reached as `import com.jiahan.smartcamera.feature.<name>.R`, and a file inside the module needs that
import too (its own `R` is not in its own Kotlin package). `:core:common` is the same shape:
`ValidationUtils.kt` is in `com.jiahan.smartcamera.util` and imports
`com.jiahan.smartcamera.core.common.R`.

The dependency arrows run one way: `:app` → `:core:data` → `:core:domain`, `:app` →
`:core:ui` → `:core:domain`, `:app` → `:core:data` → `:core:common` → `:core:domain`, and `:app` →
each `:feature:*` → the `:core` libraries it needs. `:core:testing` hangs off the test classpaths of
`:app`, `:core:ui` and the four feature modules, and depends on `:core:common`, `:core:data` and
`:core:domain`. No feature module depends on another, and none reaches `:core:data` — `./gradlew
:feature:profile:dependencies --configuration debugCompileClasspath` lists exactly `:core:domain`,
`:core:ui` and `:core:common`, which is the check to run after touching a feature's
dependencies. Nothing depends on `:app`, so a repository implementation can no longer reach a
ViewModel, an `R` string or `BuildConfig` even by accident. `:core:ui` and
`:core:data` are **siblings** — neither depends on the other, and `:core:ui` reaches no
repository, Room or DataStore. They are the build's first pair with anything to overlap, which is
why `org.gradle.parallel` is finally on.

## Build, test, lint

Run from the repo root (Gradle wrapper):

- Build debug APK: `./gradlew assembleDebug`
- Unit tests (JVM, Robolectric-backed): `./gradlew testDebugUnitTest :core:domain:test`
  - Both tasks, because they are different tasks. `testDebugUnitTest` is an Android *variant* task
    and `:core:domain` is a plain Kotlin JVM module, so its tests run under `test` and a bare
    `testDebugUnitTest` skips them without failing. CI names both for the same reason.
    Every other module needs no such mention — they are all Android libraries, so the
    unqualified `testDebugUnitTest` already reaches them, as do `lintDebug` and
    `connectedDebugAndroidTest`. Nine modules run unit tests today: `:app` 210, `:feature:auth` 46,
    `:feature:explore` 34, `:feature:settings` 30, `:feature:profile` 26, `:core:common` 27,
    `:core:ui` 17, `:core:data` 15, `:core:domain` 8 — 414 in total.
  - Single class: `./gradlew testDebugUnitTest --tests "com.jiahan.smartcamera.home.HomeViewModelTest"`
  - Single method: `./gradlew testDebugUnitTest --tests "com.jiahan.smartcamera.home.HomeViewModelTest.methodName"`
  - Assert on a settled `StateFlow` by reading `.value`; that is what most of the suite does, and
    it is the right tool, since a `StateFlow` always has a current value. Reach for
    [Turbine](https://github.com/cashapp/turbine) (`.test { ... }`) when the *sequence* matters —
    intermediate states, ordering — or for a `SharedFlow` event, which has no `.value` to read at
    all. Never hand-roll a collector into a list; that is what Turbine replaces.
  - ViewModel tests replace `Dispatchers.Main` with the `MainDispatcherRule` in
    `core/testing/src/main/kotlin/com/jiahan/smartcamera/MainDispatcherRule.kt`
    (`@get:Rule val mainDispatcherRule = MainDispatcherRule()`), since `viewModelScope` dispatches
    to Main. It defaults to `UnconfinedTestDispatcher` so coroutines run eagerly; pass
    `StandardTestDispatcher` when a test needs virtual-time control, such as a debounce.
- Screenshot tests use Roborazzi and live in **three** modules: `ScreenScreenshotTest` in
  `app/src/test/.../screenshot/`, `NoteItemScreenshotTest` in `core/ui/src/test/.../screenshot/`,
  and `SettingsScreenScreenshotTest` in `feature/settings/src/test/.../screenshot/` — each beside
  the composable it captures. Each module keeps its own goldens under its own
  `src/test/screenshots/` and applies the Roborazzi plugin with its own `outputDir`; the shared
  harness, `BaseScreenshotTest`, is in `:core:testing`. A module running these needs
  `debugImplementation(libs.androidx.ui.test.manifest)` — `createComposeRule()` launches a
  `ComponentActivity` that exists only in the manifest that artifact merges into the debug variant,
  and the merge is per-variant, so it cannot arrive through `:core:testing`. Without it every
  capture fails with *Unable to resolve activity for Intent … ComponentActivity*. Note that
  `testDebugUnitTest` *runs* the tests but does **not** diff them against the goldens — only
  `./gradlew verifyRoborazziDebug` compares. Run it before
  pushing UI changes; on its own it re-runs the whole suite, so to compare screenshots alone use
  `./gradlew verifyRoborazziDebug --tests "com.jiahan.smartcamera.screenshot.*"` (this is what CI
  does). Re-record with `./gradlew recordRoborazziDebug` when a diff reflects an intended change,
  and look at the new PNGs before committing them.
- **Moving a type between modules can leave KSP's incremental state stale.** The symptom is a wall
  of `InjectProcessingStep was unable to process 'X(…,Foo,…)' because 'Foo' could not be resolved`
  from `:app:kspDebugKotlin`, for a type that is on the classpath and whose module compiles fine on
  its own. It looks exactly like the `api`-vs-`implementation` failure described under
  [Conventions](#conventions) and is not that: the tell is that the unresolved name appears
  *unqualified* while its neighbours from the same module are fully qualified. `./gradlew clean`
  fixes it. Check this before re-plumbing dependency configurations.
- **`./gradlew compileDebugAndroidTestKotlin` catches Hilt graph errors that nothing else does.**
  `assembleDebug`, the unit tests, Roborazzi and lint all pass without ever compiling the
  androidTest sources, and `di/HiltGraphSmokeTest.kt`'s member injection is what walks the binding
  graph furthest — so a missing binding or an unresolvable constructor parameter shows up there
  first. Run it after any change to a module's dependencies or to an `@Inject` constructor, and
  especially when you can't run the instrumented suite for lack of a device.
- Instrumented tests live in five modules now — `app/`, `core/data/`, `feature/auth/`,
  `feature/profile/` and `feature/settings/` — require a device/emulator, and run via
  `./gradlew connectedDebugAndroidTest`.
  `:app` uses a custom `HiltTestRunner` (installs `HiltTestApplication`), the AndroidX Test
  Orchestrator, and `clearPackageData=true` for hermetic, isolated runs — don't remove these from
  `app/build.gradle.kts` without understanding why. The three library modules declare no
  `testInstrumentationRunner` of their own: their suites build a ViewModel from `:core:testing`'s
  fakes and inject nothing, so the default `AndroidJUnitRunner` is enough.
  - **`sharedTest/` is the arrangement to copy, and it is not `:app`-only.** A Compose behaviour
    suite placed there is compiled into *both* the unit-test and androidTest source sets, so it
    runs on the JVM under Robolectric for fast CI and on-device under the instrumentation runner,
    written once. `:app` has `HomeScreenTest` and `:feature:auth` has `AuthScreenTest`, each wired
    with two `sourceSets` lines plus `unitTests.isIncludeAndroidResources = true`.
    `SettingsScreenTest` is androidTest-only by comparison, which is the weaker of the two — prefer
    `sharedTest/` for a new screen test. **But check an existing test's own note before promoting
    it**: `ProfileScreenTest` says it is device-only because the bottom-anchored save button and
    the inline validation text depend on real viewport and scroll behaviour, and moving it to
    `sharedTest` during the `:feature:profile` extraction confirmed that exactly — three of its
    five tests passed under Robolectric and two failed, one on the error text never being displayed
    and one on a 5s timeout waiting for the save. "androidTest-only" is sometimes a finding rather
    than an omission.
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

Its `path:` list is literal per-module paths, not a glob, so **a new module that captures
screenshots or produces a lint report has to be added to it by hand.** `:core:ui` was missing from
that list for a release after `NoteItemScreenshotTest` moved there, which would have made a golden
failure in it report with no images at all. The **`unit-test-report`** list is the same kind of
hand-maintained list and had drifted further: `:core:ui`, `:feature:explore` and `:feature:settings`
all had suites reporting nothing, so a failure in one came back as a red X with no report to open.
Filled in when `:core:common` and `:feature:auth` were added — check both lists, not just the
screenshot one.

Two things to know about the goldens:

- Any screenshot showing a note renders a formatted timestamp through
  `Long.toFormattedDateTime()`, whose `zone`/`locale` parameters *default* to
  `ZoneId.systemDefault()`/`Locale.getDefault()` — and a composable calls it with those defaults.
  That made the goldens machine-dependent: they passed on a UTC+8 laptop and failed on the UTC CI
  runner, eight hours out. The unit-test JVM is pinned to UTC/en-US so goldens recorded on any
  machine verify on every other one. **That pin now lives in `build-logic`, not in
  `app/build.gradle.kts`** (`configureTestJvm()`, applied by every convention plugin), because
  more than one module has unit tests to keep deterministic. If you change it, re-record.
  The parameters were added when `:core:ui` was extracted, and it is worth being precise about what
  they did and did not fix. They made the formatter *directly* testable — `DateTimeUtilsTest` now
  passes an explicit zone and locale, which is how it can assert three zones and three locales in
  one run, something a single JVM-wide setting cannot express. They did **not** retire the pin: the
  goldens render through `NoteItem`, which calls the defaults, so the global read is still what a
  screenshot exercises. Hoisting zone/locale up to the composable's caller is what would retire it.
  More generally: a golden diff that appears only on CI is far more likely to be non-determinism in
  the test than a rendering difference between platforms — check for a clock, locale, or random
  value in the fixture before assuming the environment is at fault.
- `settingsScreen_default.png` used to go stale on every version bump in `app/build.gradle.kts`,
  because it rendered `BuildConfig.VERSION_NAME`. It no longer does: `SettingsScreen` takes
  `versionName` as a parameter (it had to — `:feature:settings` is a library and has no application
  `BuildConfig`), and the test pins `"1.0.0"`. That is the general lesson rather than a settings
  quirk — **a golden that renders a build-varying value is a hoisting problem, not a re-recording
  chore.** The same reasoning retired the timestamp non-determinism above, except there the pin is
  still needed because `NoteItem` calls the formatter's defaults.

### Convention plugins

`build-logic/` is an included build (`includeBuild("build-logic")` from `pluginManagement` in
`settings.gradle.kts`), holding five plugins that every module applies by id instead of restating
the same settings:

| Plugin | Applied by | Applies | Sets |
| --- | --- | --- | --- |
| `smartphotos.android.application` | `:app` | AGP application, Kotlin Android | compileSdk 37, minSdk 28, Java 11, JVM target 11, test-JVM pin |
| `smartphotos.android.library` | `:core:common`, `:core:data`, `:core:ui`, `:core:testing` | AGP library, Kotlin Android | the same, minus nothing |
| `smartphotos.android.compose` | `:app`, `:core:ui`, `:core:testing` | Compose compiler | `buildFeatures.compose = true` |
| `smartphotos.android.feature` | `:feature:auth`, `:feature:explore`, `:feature:profile`, `:feature:settings` | the library + compose conventions, KSP, Hilt | the `:core:domain`/`:core:ui` edges, the Compose set, icons, Hilt, lifecycle, `:core:testing` |
| `smartphotos.jvm.library` | `:core:domain` | Kotlin JVM — **nothing Android** | Java 11, JVM target 11, test-JVM pin |

`smartphotos.android.feature` is the only one that adds *dependencies* rather than just settings,
and it is worth knowing why it did not exist while `:feature:explore` was alone: the first rule
below. A feature module's build file should now contain only what that feature alone needs —
explore keeps `coil-compose`, `activity-compose` and the serialization plugin; settings keeps
`androidx-core-ktx`, Roborazzi and its instrumented-test set.

Reaching the version catalog from inside a convention plugin is not the same as from a build
script: the generated `libs.androidx.material3` accessors are a script feature, so `build-logic`
looks the catalog up by name (`Project.libs` in `buildlogic/VersionCatalog.kt`) and names entries as
strings. **An alias typo there fails at configuration time in the consuming module, not at compile
time in `build-logic`.**

Five rules worth knowing before editing them:

- **Put a setting here only when more than one module wants it, and wants it for the same reason.**
  `targetSdk` stays in `app/build.gradle.kts` because a library has no `targetSdk`; `namespace`
  stays in each library because every module needs its own; `buildConfig = true` stays in `:app`
  because no module below it should generate a `BuildConfig` (see the Build type rule under
  [Conventions](#conventions)).
- **One module is a sample size of one — wait for the second.** `smartphotos.android.feature` was
  deliberately not written while explore was the only feature, because there was no way to tell
  which of its twenty-odd lines were *the shape of a feature* and which were *the shape of Explore*.
  Extracting `:feature:settings` answered it, and answered it partly against expectation: the icon
  packs were predicted to be Explore-specific, and settings failed to compile on
  `Icons.Rounded.Check` within a minute of the first build. Guessing which lines generalize does not
  work; a second consumer is what tells you.
- **`build-logic` targets Java 17, the modules target 11.** That is not drift: the convention
  plugins run inside the Gradle daemon, which requires 17+, while 11 is what the app compiles
  against. Don't "fix" one to match the other.
- **The plugin artifacts are `compileOnly`.** `android-gradlePlugin`, `kotlin-gradlePlugin` and
  `compose-gradlePlugin` are catalog entries used only by `build-logic/convention`, sharing version
  refs with the `[plugins]` aliases so the code compiles against the AGP that is actually applied.
  The modules' `pluginManager.apply("com.android.application")` calls resolve against the build
  classpath the root `build.gradle.kts` establishes with its `apply false` block — which is why
  that block must keep listing them.
- **AGP 9's `CommonExtension` is not generic and exposes only property accessors.** The
  `defaultConfig { }` / `compileOptions { }` block forms are declared on the concrete
  `ApplicationExtension` and `LibraryExtension`, so shared code configures through properties
  (`defaultConfig.minSdk = …`). Guides written against AGP 8 show `CommonExtension<*, *, *, *, *, *>`
  and the block form; neither compiles here.

## Architecture

MVVM with a layered structure, one Kotlin package per feature. Five are still under
`app/src/main/java/com/jiahan/smartcamera/` (`home`, `note`, `favorite`, `search`, plus `preview`),
and none of them is held there by anything but `:app`'s `R` now;
`explore`, `settings`, `auth` and `profile` are their own modules. For the ones still in
`:app`, the module boundaries run underneath them — between the contracts in `:core:domain` and the
implementations that satisfy them, and between the feature screens and the shared Compose they all
draw with in `:core:ui`.
Cross-cutting layers:

- **UI** — Jetpack Compose screens (`*Screen.kt`) + Navigation Compose graph in
  `navigation/SmartPhotosNavGraph.kt`, with each destination's route type in the feature package
  that owns it (`home/HomeRoute.kt`, `preview/PreviewRoutes.kt`, …).
- **ViewModel** — `@HiltViewModel` classes exposing a `*UiState` data class via `StateFlow`. The
  loading/loaded/error branch is a nested sealed sub-type (e.g. `HomeContent` in
  `home/HomeViewModel.kt`), kept separate from flat fields on the outer `*UiState` for orthogonal
  UI state (`isRefreshing`, dialogs, pagination) that shouldn't force a full state-machine branch.
- **Repository** (`data/repository/`) — one interface + one `Default*` implementation per
  repository (e.g. `NoteRepository` / `DefaultNoteRepository`), all bound in
  `data/di/DataModule.kt`. Coordinates Firebase Firestore/Storage (remote) and Room/DataStore
  (local). The two halves now live in different modules: the interfaces are in `:core:domain`, the
  `Default*` implementations and `DataModule` in `:core:data`. Two interfaces cannot live in
  `:core:domain` at all — `AppUpdateRepository` takes `ActivityResultLauncher`/`IntentSenderRequest`
  and `MediaFileRepository` takes `Bitmap`/`Uri`, so neither compiles in a module without Android.
  They are handled differently, and the difference is the rule: `MediaFileRepository` moved to
  `:core:common` when `:feature:profile` needed to inject it, because **a feature module must not
  depend on `:core:data`**; `AppUpdateRepository` stays in `:core:data` beside its `Default*`
  because only `:app`'s `MainViewModel` injects it. Move the next one when, and not before, a
  feature needs it.
- **Domain** (`domain/`, in `:core:domain`) — plain data classes shared across features (e.g.
  `HomeNote`, `MediaDetail`, `User`). Their purity is no longer a convention to uphold by review:
  the module has no Android plugin, so a stray `import android.*` fails the build.
- **Local** — Room database in `database/` (schemas exported to `core/data/schemas/`), DataStore
  preferences in `data/datastore/`, whose contract and model (`UserPreferencesRepository`,
  `UserPreferences`) sit in `:core:domain` while the DataStore wiring sits in `:core:data`. Note that
  `UserPreferences` is a domain model living outside the `domain/` package: it keeps its
  `data.datastore` package so the extraction stayed a pure `git mv`. A note's media list is persisted into the `notes.media_list`
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
- Repositories own data-source coordination and expose domain models — never Firestore
  `DocumentSnapshot`/`QuerySnapshot` or Room entities — across the repository interface boundary.
  Every fallible operation returns `Result<T>` rather than throwing, so callers never wrap a call in
  try/catch; wrap the body in `util/safeCall`. The exemptions are the ones that can't carry a
  `Result`: a `Flow`-returning stream, and fire-and-forget work like `quickUploadMediaToFirebase`,
  which logs its failures internally. Match that shape in new methods.
  Firestore is the source of truth and writes go to Firestore first, then Room; see
  [Source of truth](#source-of-truth) below for what Room actually holds, and why that is a
  deviation from the offline-first guidance rather than an implementation of it.
- Domain models are plain data with no Android/Firebase/Room dependencies, so they can be shared
  and unit-tested without those frameworks on the classpath. A local media location crosses this
  boundary as `domain/MediaUri.kt`, never as `android.net.Uri` — convert with `toMediaUri()` /
  `toPlatformUri()` from `util/MediaUriExt.kt` in `:core:common`, at the ViewModel boundary on the
  way down or inside
  a `Default*` implementation on the way out. It mirrors how `MediaDetail` already carries remote
  locations as plain `String` URLs.

When adding a feature, prefer extending this layering over reaching across it (e.g. a screen
calling `FirebaseFirestore` directly, or a repository returning a Room `@Entity` to a ViewModel).

### Source of truth

**This is a deliberate deviation from the architecture guide**, and it is the root of the
[Cross-feature communication](#cross-feature-communication) deviation below. The offline-first
guidance makes the local database the source of truth: the UI observes Room, writes land locally
first, and a sync layer reconciles with the backend. This app does the opposite — Firestore is the
source of truth and Room is written only after the Firestore write returns. Home *reads* the Room
mirror now, which is the read half of that guidance and not the write half: there are still no
offline writes and no reconciliation. Search still renders point-in-time `QuerySnapshot`s.

**The `notes` table is mid-migration, and knowing which half you are in matters.** It used to hold
favorited notes only — every `noteDao` write was gated on the flag and unfavoriting deleted the row
— so it was a local mirror for one feature, not a read path for the notes list. That gating is gone:
`getNotes` now writes each fetched page through, `updateNote` caches unconditionally, and
`favoriteNote` upserts in both directions instead of deleting. The table holds every note the feed
has paged through.

**All four screens observe the mirror now.** Home, Search, NotePreview and Favorite each expose a
`content: StateFlow` built by `combine(<a Room query>, <a fetch status>)` and shared
`WhileSubscribed`. The remote call still happens — `getNotes(cursor)` pages the feed, `searchNotes`
reads the collection, `getNote` loads one — but none of them hands its result to the UI: each writes
through to the `notes` table and the screen re-reads it. Four queries back them:

| Screen | Query | Fetch that fills it |
| --- | --- | --- |
| Home | `getNotesStream(limit)` | `getNotes(cursor)` |
| Search | `searchNotesStream(query)` | `searchNotes(query)` |
| NotePreview | `getNoteStream(noteId)` | `getNote(noteId)` |
| Favorite | `getFavoriteNotesStream(query)` | `syncFavoriteNotes()` |

That retired every list transform these ViewModels used to apply by hand, and with them three of
`NoteHandler`'s four events — and the fourth followed later. **The `combine` shape is the thing
to copy**: the fetch status decides
only what an *empty* result means (nothing fetched yet = loading, fetch failed = error), and any
rows at all beat both — so a failed refresh keeps the cached list on screen and reports itself
through `actionError` rather than blanking the feed.

Four consequences worth knowing:

- **`getNotesStream` takes a `limit`, and Home widens it one page at a time.** Without it the feed
  would render the whole table, which is not the same list: `searchNotes` and `getNote` write into
  `notes` too, so a note the user only ever searched for would turn up in Home. The window keeps
  what is rendered equal to what was paged — reset it wherever the cursor resets.
- `cacheNotes` **fails the fetch** on a mirror write error, where it used to log and continue. The
  old behaviour was right while nothing read the table; with the screens rendering it, a swallowed
  write is a note the user cannot see *and* a cursor advanced past it, which makes the hole
  permanent. Failing leaves the cursor where it was, so the page is retried.
- **Search did not get narrower.** `searchNotes` still reads the whole collection from Firestore; it
  just writes the results through on the way out, so `searchNotesStream` covers notes the feed has
  never paged. Pointing Search at the table *without* that write would have quietly reduced it to
  searching whatever Home happened to have scrolled.
- `syncFavoriteNotes` is still favorites-only, and is now the odd one out: the DAO clears the
  favorited rows and reinserts what the server says is favorited, leaving mirrored non-favorites
  alone. Nothing reconciles the rest of the table.

What the deviation still costs, listed so nobody rediscovers it as a bug:

- No offline writes. A mutation with no network fails at the Firestore call and never reaches Room.
- No reconciliation. Nothing removes a mirrored row that no longer exists server-side, and a reload
  adds to the table rather than replacing it. Home's `limit` bounds what that can surface, but a
  note deleted on another device lingers locally until something rewrites the row.
- **That is the whole remaining gap.** The read path is done; offline writes and reconciliation are
  the separate, larger project, and they are what would make this offline-first rather than
  cache-backed.

**The read path is done.** What is left on this deviation is offline writes and reconciliation,
which is a larger project and deliberately out of scope. Two properties of the read path that are
easy to break and worth stating:

- `getNotesStream(limit)` carries **no cursor**. `getNotes(cursor)` owns the remote pagination and
  writes each page into the mirror, so a subscriber sees the result rather than driving it — that is
  the `RemoteMediator` shape without the Paging 3 dependency, and it works only because the
  collection is one user's own notes (`user/{uid}/note`) rather than a shared feed. The `limit` is
  the subscriber's half of that bargain, not a second cursor.
- **Every remote read writes what it fetched into the table before returning.** `getNotes`,
  `searchNotes`, `getNote` and `addNote` all do. A new remote read that skips it is a screen that
  renders nothing, because the screens no longer look at return values. `addNote` is the one that
  reads its own result *back* — the createNote Cloud Function returns only `{documentPath}`, and the
  id and server-stamped `created` exist only server-side, so the client cannot build that row
  itself. One extra document read, and deliberately not fatal: the note was created, so failing
  there would report a successful write as an error.

The behaviour to get right when moving a screen over is the empty state, and Home is the worked
example. Room answers before the first fetch does, so a fresh install renders an empty table while
the notes are still in flight; `HomeViewModel`'s `FetchStatus.Pending` branch is what stops that
from showing "create your first note" to a user who has notes. Two tests pin it —
`init stays Loading while the first fetch is in flight over an empty mirror` and
`init renders the mirror before any fetch completes`. The mirror image is a *failed* fetch: an
error screen is right on an empty cache and wrong on a populated one, so with rows on screen the
failure goes to `actionError` as a snackbar and the cached feed stays up. That replaced Home's old
"refresh failure blanks the list for a full-screen error", which was a safeguard only while there
was nothing cached to lose.

### Cross-feature communication

**There is no cross-feature communication mechanism any more, and that is the point.** Screens that
must reflect a mutation made on another screen observe the Room mirror
([Source of truth](#source-of-truth)). `NoteHandler` is deleted — all four of its events, its
`@Provides` in `AppModule`, `observeNoteMutations`, `NoteHandlerTest`, and every list transform the
ViewModels applied on receiving an event.

**Do not reintroduce one.** The official answer to "screen A must reflect a change made on screen B"
is a single source of truth in the data layer exposing a reactive stream both screens observe, and
that is now what this app does. If a list *could* be backed by a live query, back it with the query.

The removal is worth reading as a sequence, because each step only became visible after the one
before it:

1. Three events (deleted / favorited / updated) died when the four screens moved onto the mirror —
   those mutations already wrote through to the table.
2. `noteAddedEvent` looked structural, because a created note has a server-stamped id and `created`
   that the client cannot invent. It was not: `createNote` already returned `{documentPath}` and the
   repository was throwing it away. `addNote` reads the note back through `getNote`, which mirrors
   it. **No Cloud Function change was needed — the payload had been there all along.**
3. With the last subscriber gone the class deleted outright.

**The lesson is the shape of the mistake**, since this page asserted twice that step 2 required a
backend change. A missing capability and an unused return value look identical from the call site.
Check what the remote actually hands back before planning around what it doesn't.

One rule that outlived the pattern, for whatever event type comes next: a default
`MutableSharedFlow` has no replay, so a subscriber that is not collecting yet silently misses the
event. For an event a screen must never miss, use `note/IncomingShareHandler.kt` — a `StateFlow`
holding the pending value plus an explicit `consume()`, which survives having no subscriber yet.
That one is still in use, and it is the pattern to copy rather than a `SharedFlow`.

**The delegates are dealt with; the five packages are next.** `NoteActionsDelegate` is gone —
without `noteHandler` it was `repository.call().onFailure { report }.isSuccess`, so it inlined into
its four callers rather than being relocated. `NoteShareDelegate` and `NoteErrorReporter` went down
to `:core:common`, which is why that module now carries Hilt. The rule those two decisions came from
is worth keeping: **move what cannot be cheaply duplicated, inline what can.** Both could have moved
— once `:core:common` was taking one of them the marginal cost of the other was zero — and inlining
the trivial one was chosen to keep that module to one new tenant-family rather than two.

What is left in `:app` is five packages and one edge:

| Package | Lines | Reaches up for |
| --- | --- | --- |
| `home` | 470 | `:app`'s `R` |
| `search` | 368 | `:app`'s `R` |
| `favorite` | 315 | `:app`'s `R` |
| `preview` | 1,092 | `:app`'s `R` |
| `note` | 1,458 | `:app`'s `R` |

`:app`'s `R` is the problem solved four times over already — decide each string one at a time, and
expect the `explore`/`cd_back` split at every extraction (exclusive strings travel, shared ones go
*down* to `:core:ui` or `:core:common`, and a string that looks shared is sometimes two strings).
There are no structural edges left at all: `NoteHandler` is deleted and the delegates are down in
`:core:common`. **A `:feature:note` is fine**: what NiA's rule forbids is the other four *depending*
on it, and nothing does.

Two corrections to what this paragraph used to say, both learned by doing the work. It told you to
sequence the Room migration before extracting `auth` and `profile` — they are extracted, so that
advice is spent. And it said the delegates would "shrink to per-feature code" once the handler went:
half right, and the wrong half was the expensive one to assume. `NoteActionsDelegate` did.
`NoteShareDelegate` did not — thirty lines of parallel media download that four packages genuinely
share. **Predicting that a shared helper will stop being shared is the same error as predicting
which convention-plugin lines generalize**, and it wants the same fix: count the consumers.

### Error handling

Route thrown errors through `util/ErrorHandler` rather than reading `Throwable.localizedMessage`
directly. Its two methods belong to different layers, and that split is the rule:

- **`logError(throwable, tag)` — any layer, repositories included.** It only touches `Log` (debug)
  and Crashlytics (release), so a repository logging a failure it swallows is correct;
  `DefaultNoteRepository`, `DefaultMediaFileRepository`, `DefaultAuthRepository` and
  `FirebaseRemoteConfigRepository` all do.
- **`getErrorMessage(throwable)` — ViewModel layer only.** It resolves a string resource through
  `ResourceProvider`, which makes its result user-facing presentation, not data. Repositories must
  not call it: they log and then either propagate the exception or fold it into a `Result`/null
  return (`DefaultNoteRepository` does the latter), and the ViewModel converts that into an
  `*UiState` error field. Every current call site is a ViewModel or a `@ViewModelScoped` helper
  (`note/NoteErrorReporter.kt`) — keep it that way.

The feature-specific mapper in `util/ErrorMessageMappers.kt` (`noteErrorMessageResId`) sits at
that same ViewModel layer, tried ahead of `getErrorMessage` and falling back to it when it returns
null.

There were two. `usernameErrorMessageResId` read an `ALREADY_EXISTS`/`INVALID_ARGUMENT` code off a
`FirebaseFunctionsException` and was tried by `AuthViewModel` and `ProfileViewModel`;
`DefaultUserRepository` now raises `AppError.UsernameTaken`/`UsernameReserved` instead and
`getErrorMessage` renders them, so both call sites shrank to a plain `getErrorMessage`. **The
reason to prefer that shape is not tidiness — reading a Firebase error code is data-layer
knowledge, and leaving it up here would have put `firebase-functions` on `:feature:auth`'s
classpath for the sake of two lines.** `noteErrorMessageResId` is the same shape and should go the
same way when `note/` moves; it is left as the worked example of the pattern being replaced. Where
the two genuinely differ is what they read: the username one switched on the error *code*, which
folds cleanly, while the note one reads a structured `details` payload because all of createNote's
validation errors share one code — so its fold needs a `reason` per `AppError` case, not a code
per case.

The three pieces live in three files, by layer rather than by topic: `util/ErrorHandler.kt` holds
the interface and `ErrorTag` and imports nothing; `util/DefaultErrorHandler.kt` holds the
Android/Firebase-bound implementation; `util/ErrorMessageMappers.kt` holds the `R`-resolving
mappers. Keep a new mapper in the third file rather than reuniting them. That split is now also a
module boundary — the first file is in `:core:domain` and the other two are in `:app`, which is
exactly the division the KMP note below predicted.

**A repository that needs to raise its own failure throws a `domain/AppError`, never a message.**
Resolving a string resource is presentation, so a repository building one —
`IllegalStateException(context.getString(...))` — puts ViewModel-layer work in the data layer and
forces an Android `Context` into a class that otherwise needs none. `AppError` is a sealed type carrying an
identity (`NotAuthenticated`, `NoteUnavailable`, `NoMediaAvailable`, `UsernameTaken`,
`UsernameReserved`); `appErrorMessageResId` maps each to its string. That mapper is the one applied *inside* `getErrorMessage` rather than at the
call site, because an `AppError` is the app's own cross-cutting vocabulary and every caller wants
the same string for it — so a ViewModel already routing failures through `ErrorHandler` renders it
with no extra code. Add a case to the sealed type and to the mapper together; don't reach for
`Context` in a repository to build the message instead.

The last two cases show the second thing this buys, beyond keeping `Context` out of the data layer:
`UsernameTaken`/`UsernameReserved` replaced a ViewModel-layer mapper that inspected a
`FirebaseFunctionsException`, and that is what let `:feature:auth` be extracted without
`firebase-functions` on its classpath. **A Firebase type read above the repository boundary is a
module boundary waiting to be violated** — fold it into an `AppError` before it is, not after.

This split is also what makes the [KMP readiness](#kotlin-multiplatform-readiness) rule satisfiable
rather than self-contradictory. The `ErrorHandler` *interface* is Android-free in its signatures
(`Throwable` in, `String` out), so injecting it into a repository imports no `android.*` type — only
`DefaultErrorHandler` is Android-bound. If repository interfaces are ever extracted to a shared
module, `logError` travels with them and `getErrorMessage` is the half that stays on the Android
side.

### Kotlin Multiplatform readiness

We may migrate parts of this codebase (`domain/`, repository interfaces, other business logic) to
Kotlin Multiplatform down the line. This isn't a mandate to add KMP tooling now, but when choosing
between otherwise-equivalent approaches, prefer the one that keeps that migration cheap:

- Keep `domain/` models and repository *interfaces* free of Android/Firebase/Room types — required
  by the Separation of concerns rules above, and since the extraction, enforced by the compiler
  rather than by review: they live in `:core:domain`, which has no Android Gradle plugin.
  Extracting that module was the actual first step and it is done; the type purity built up
  beforehand is what kept it to a `git mv`. `:core:data` is now extracted too — but be honest about
  what that bought: it is an Android library full of Firebase and Play Core, so **nothing in it
  became shareable by moving**. It was modularization, not KMP progress, and the two should not be
  conflated. Keep the reading of this honest in both directions: a pure package inside `:app` was
  never progress on its own, because KMP moves Gradle *modules*, not Kotlin packages; and a module
  that compiles without the Android plugin is real progress but still not a `commonMain` source set.
  `:core:ui` reads the same way as `:core:data`: it is an Android library full of Jetpack Compose,
  so extracting it bought modularization, not shareability. So do `:feature:explore`,
  `:feature:settings`, `:feature:auth` and `:feature:profile` — feature modules are Compose end to
  end and are the
  *least* shareable code in the build. Extracting more of them is worth doing for the boundaries it
  enforces, but it moves the KMP needle by exactly zero, and the two should not be reported as one
  number. `:core:common` is the sharpest example yet, and it cuts the other way from what its name
  suggests: it holds two pure functions whose only Android dependency is that they return string
  resource ids. Nothing about `validateUsername` needs Android — but a resource id does, so the
  module needs the Android plugin and the functions stay unshareable. **If `:core:domain` goes
  multiplatform, that is the seam to revisit**: validators returning an identity (the `AppError`
  shape) rather than a resource id would move to `commonMain` and leave only the mapping behind. (Compose Multiplatform would change
  that calculus, but nothing here targets it, and `android.content.ClipData`, `android.os.Build`
  and the `R` class in `common/` would all have to go first.)

  **The next actual KMP step is converting `:core:domain` from `kotlin.jvm` to
  `kotlin.multiplatform`** (`commonMain` + `androidTarget()`). Every import in its `main` sources is
  already multiplatform — `kotlin.time.Instant`, `kotlinx.coroutines`, `kotlinx.datetime`,
  `kotlinx.serialization` — with exactly one exception: `javax.inject.Qualifier` in
  `di/Qualifiers.kt`, which belongs in an `androidMain` source set, since Hilt is Android-only.
  `SafeCallTest.kt` needs three small edits to reach `commonTest` (JUnit4 → `kotlin.test`,
  `java.io.IOException` → any exception, and its deliberate `runBlocking` case has no common
  equivalent, so that one test stays platform-side). Note that `androidTarget()` requires an Android
  library plugin on `:core:domain`, which sounds like it gives up the no-Android-plugin property
  above — it doesn't: `commonMain` resolves only against the common stdlib, so it blocks `java.*` as
  well as `android.*`. The enforcement gets stricter, and `androidMain` becomes the visible place
  for the exceptions.

  **The ceiling to know about before planning further:** Hilt has no KMP support, and it is
  load-bearing below `:app` — every `Default*` is `@Inject constructor` and `DataModule` is
  `@InstallIn(SingletonComponent::class)`. A shared data layer would need Koin or hand-written
  constructor wiring with Hilt confined to the Android edge. That decision, not module splitting,
  is what sets how far KMP can go here.
- Prefer `kotlinx` libraries (`kotlinx.coroutines`, `kotlinx.datetime`, `kotlinx.serialization`)
  over equivalents that have no `commonMain` implementation (e.g. `java.time`, Gson) in
  shared-leaning code, when a choice exists. `java.time` is not unavailable on Android — core
  library desugaring provides it — the objection is only that it cannot cross into a shared source
  set.
- Keep `android.*` out of the *contracts*: domain models, repository interfaces, and the data
  classes those interfaces carry (`NoteMediaDetail`, say). `Default*` implementations are
  Android-bound by definition and are not what this rule targets — `DefaultErrorHandler` uses `Log`,
  `ResourceProviderImpl` needs a `Context` — the interface itself is in `:core:domain`, moved there
  when `:core:testing` was extracted, because `FakeResourceProvider` implements it and a fixtures
  module cannot depend on `:app`. It is the same interface-down/implementation-up split as
  `ErrorHandler`, and since the package was already `com.jiahan.smartcamera.util` it was a pure
  `git mv`. Note what it carries: `getString(resId: Int)` takes a resource id as a bare `Int`, which
  compiles in a pure-JVM module but is an Android concept in everything but its type — so it is not
  a KMP asset, just a testable seam. One interface is deliberately exempt:
  `MediaFileRepository` exists precisely to wrap `ContentResolver`/`FileProvider` work behind a
  seam, so it keeps its `Uri`/`Bitmap` parameters and is not a KMP candidate. Don't wrap those
  parameters to satisfy the rule, and don't cite it as precedent for a new contract. It lives in
  `:core:common` rather than `:core:domain` for exactly that reason, which makes the module split
  the visible record of which contracts are shareable and which are not.
- The module *names* deviate from Google's, deliberately. The
  [modularization patterns](https://developer.android.com/topic/modularization/patterns) guide keeps
  a repository, its data sources and its models together in a single data module; Now in Android
  splits that into `core:model` (model classes), `core:common` (`NiaDispatchers`, `Result`) and
  `core:data` (repository interfaces *and* implementations). Our `:core:domain` is those three minus
  the implementations: domain models, the pure repository interfaces, `safeCall`, the
  `ErrorHandler` interface and the DI qualifiers. It is split that way because `:core:data` is
  an Android library — Firebase and Room — so the contracts need a pure-JVM home to be worth
  anything; NiA is not KMP and has no such pressure. The cost is the name: `core:domain` means *use
  cases* in Google's usage, not models. Keep the name and this note together rather than renaming to
  `:core:model` (which would be inaccurate — it holds more than models) or splitting into three
  modules at this size, and don't read `:core:domain` here as a use-case layer.

  The `:feature:*` names, by contrast, match NiA exactly, and so does their shape: depend on
  `:core:*` only, never on another feature, never on `:app`. That last constraint is the one that
  decides how far this can go — see [Cross-feature communication](#cross-feature-communication).

  `:core:ui` deviates the other way — it is *one* module where NiA has two. NiA splits
  `core:designsystem` (theme, atoms, icons) from `core:ui` (composites that know domain types).
  `NoteItem` is squarely the second kind: it takes a `HomeNote`. At 1,294 lines nothing here
  consumes one half without the other, and a second module earns its keep only when something does
  — the same test that argued against breaking `:core:database` out of `:core:data`. Revisit if a
  second app, or a Wear/TV surface, ever appears.

## Follow official Android guidance

When implementing or reviewing changes, prefer solutions aligned with Google's official Android
guidance over ad-hoc approaches:

- [Android app architecture guide](https://developer.android.com/topic/architecture) — unidirectional
  data flow, `StateFlow`/`UiState` exposed from ViewModels (not events polled by the UI), repositories
  as the single source of truth — with the one documented exception that the notes feed reads
  Firestore directly ([Source of truth](#source-of-truth)).
- [Jetpack Compose guidance](https://developer.android.com/develop/ui/compose/documentation) —
  state hoisting, `remember` for recomposition efficiency, avoiding side effects outside
  `LaunchedEffect`/`DisposableEffect`. Use `derivedStateOf` only where a frequently-changing state
  feeds a rarely-changing derived value (a scroll offset driving an `isScrolled` boolean); applied
  more broadly it adds overhead instead of removing it.
- [Kotlin coroutines & Flow best practices](https://developer.android.com/kotlin/coroutines/coroutines-best-practices) —
  scope coroutines to `viewModelScope`, inject `CoroutineDispatcher`s rather than hardcoding
  `Dispatchers.IO`, avoid `GlobalScope`. The UI here is Compose-only, so there are no
  Fragment/Activity scopes to scope to: the composition-side equivalents are `LaunchedEffect` and
  `rememberCoroutineScope`, and `lifecycleScope` appearing in a new file is a smell, not a
  convention.
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
- There are three tiers, not two. `remember`/`mutableStateOf` for state that is genuinely local
  and ephemeral to composition (IME visibility, an animation trigger). `rememberSaveable` for local
  state that must also survive configuration change and process death (a half-typed field, an
  expanded section). The ViewModel's `UiState` for anything another screen needs or that outlives
  the composition — with `SavedStateHandle` for the parts of it that must survive process death.
- "Put it in the ViewModel" does not by itself mean "it survives". A ViewModel is scoped to its
  `NavBackStackEntry`, so it is cleared when that entry is popped, and no ViewModel survives process
  death. Today `rememberSaveable` appears nowhere in `app/src/main`, and `SavedStateHandle` is used
  in four ViewModels only to read navigation arguments (`savedStateHandle.toRoute<Screen.X>()`), so
  no UI state in this app currently survives process death. That is a gap, not a convention to
  copy — reach for the right tier when adding state a user would be annoyed to lose.

### One-off UI events

Snackbars and other fire-and-forget signals travel from ViewModel to screen on a
`MutableSharedFlow(extraBufferCapacity = 1)` exposed as a read-only `SharedFlow`, collected in the
screen's `LaunchedEffect` and shown through `SnackbarHostState`. `actionError` (Home, Search,
Favorite and the preview screens, all via `:core:common`'s `NoteErrorReporter`) and `ProfileViewModel.events`
are the existing instances — follow their shape rather than inventing a third.

**This deviates from the official guidance**, which says a ViewModel event should update `UiState`
with the UI signalling consumption back (see
[UI events](https://developer.android.com/topic/architecture/ui-layer/events)). It stands because
these signals have no state to restore — a snackbar already shown must not reappear, and modelling
that as state means threading a consumed-flag round trip through every `UiState`. Two limits that
come with it:

- Anything that must survive configuration change or process death is not one of these. Error text
  a screen keeps displaying belongs in `UiState` (as `HomeContent.Error` and its siblings already
  do); only the transient, toast-like signal goes on the flow.
- `tryEmit` into a one-slot buffer drops silently when events land back-to-back with no collector
  ready, and a `LaunchedEffect` collector only exists while the composition does. Don't put
  anything the user must not miss on this flow — the same replay caveat as
  [Cross-feature communication](#cross-feature-communication) above.

### Navigation

Navigation Compose's type-safe routes: each destination is a top-level `@Serializable`
`data object`/`data class`, registered with `composable<HomeRoute> { ... }` and reached via
`navController.navigate(NotePreviewRoute(id))`. Add destinations that way — no hand-built path
strings, no `navArgument` lists, no manual URL escaping of arguments.

- **The route type lives in the feature package, beside the screen it names** — `home/HomeRoute.kt`,
  `search/SearchRoute.kt`, `note/NoteRoutes.kt`, `preview/PreviewRoutes.kt` and so on. What stays in
  `navigation/` is the wiring: `SmartPhotosNavGraph.kt`, `BottomNavItem.kt` and `NavTransitions.kt`,
  the three things that legitimately need to see every route at once. Put a new route with its
  screen, not in `navigation/`. This is what lets a feature package become its own module later, and
  what lets a feature's ViewModel read its own arguments back with `toRoute<…>()` without importing
  upward — `EditNoteViewModel` and the three in `preview/` were the last four to do so. They were
  the last upward imports *into `navigation/`*, not the last upward imports full stop: every feature
  package still in `:app` reaches `:app` for its `R`. Those are what the next feature module hits.
  (`util/ResourceProvider.kt` was on that list until `:core:testing` moved it to `:core:domain`,
  `ValidationResult` until `:feature:settings` moved it there, and `util/ValidationUtils.kt` plus
  `usernameErrorMessageResId` until `:feature:auth` — the first went down to `:core:common`, the
  second was deleted rather than moved. `MediaFileRepository` and `util/MediaUriExt.kt` came off
  it with `:feature:profile`, and those two were the interesting ones, because they were not
  resources — they were `:core:data` types, which a feature module must not reach.) What is left on
  the list for the five remaining packages is `:app`'s `R`, and nothing else. The delegates came
  off it when `NoteShareDelegate` and `NoteErrorReporter` went down to `:core:common` and
  `NoteActionsDelegate` inlined; `NoteHandler` came off it by being deleted.
- **The routes share no supertype, deliberately.** They were nested in a `sealed interface Screen`
  until that hierarchy became unrepresentable: a route in a feature module cannot implement an
  interface declared in `:app`. So `startDestination` and `BottomNavItem.route` are typed `Any`,
  which is what Navigation Compose itself uses for a destination. Don't reintroduce a marker
  interface to get the exhaustiveness back.
- Route types stay plain data. UI-only metadata (bottom-bar icon and title) lives in
  `navigation/BottomNavItem.kt`, because only some destinations appear in the bottom bar.
- A route's property names are its argument names — Navigation serializes by property name, and the
  ViewModel tests build a `SavedStateHandle` from the same keys (`mapOf("noteId" to …)`). Renaming
  one changes the generated route pattern and breaks its test; renaming the route *class* changes
  the route pattern too, which invalidates a back stack saved by an older build.
- An enum used as a route argument needs `@Keep` (see `MediaSourceType` in `preview/PreviewRoutes.kt`).
  Navigation resolves enum arguments through `Class.forName()`, so R8 renaming one breaks navigation
  in release builds only — a failure that shows up in neither debug runs nor unit tests.

## Conventions

- DI: Hilt, all *modules* `@InstallIn(SingletonComponent::class)`. Constructor-injected classes may
  still be narrower — `:core:common`'s `NoteErrorReporter` is `@ViewModelScoped` so a ViewModel and
  the `NoteShareDelegate` it injects report onto one flow — so "all modules are singleton" is not "everything is a singleton". App-wide bindings live in
  `di/AppModule.kt` / `di/FirebaseModule.kt` (both in `:app`); other cross-cutting layers have
  their own module — `util/di/UtilModule.kt` in `:app`, `data/di/DataModule.kt` and
  `database/di/DatabaseModule.kt` in `:core:data`. `FirebaseModule` deliberately stayed in `:app`
  even though only `:core:data` consumes what it provides: `:app` is where Firebase is initialised
  (the `google-services` plugin, and App Check in `MyApp.kt`), so the module handing out the
  initialised SDK singletons sits beside that initialisation, and `:core:data` receives them
  through constructor injection. Moving it down is a reasonable follow-up, not a correction — there is no
  per-feature `di/` package yet (e.g. `note/`, `search/` have none); follow this layer-scoped
  pattern rather than introducing one.
- `:core:data` dependency configurations: **anything whose type appears in an `@Inject
  constructor` parameter there must be `api`, not `implementation`.** Hilt aggregates every
  `@InstallIn(SingletonComponent::class)` binding into a single component generated in `:app`, so
  `:app`'s annotation processor has to resolve those parameter types itself; hiding one behind
  `implementation` fails with `InjectProcessingStep was unable to process 'x' because 'Y' could not
  be resolved`. Under Hilt a library's constructor parameters are effectively part of its API. A
  dependency used only inside function bodies stays `implementation` — `firebase-storage` and
  `play-app-update-ktx` are the current examples. The failure surfaces in
  `compileDebugAndroidTestKotlin` well before `connectedDebugAndroidTest`, and can hide from
  `assembleDebug` entirely.
- `:core:ui` dependency configurations: the same rule reached from the ordinary direction —
  **a Compose artifact whose type appears in a public signature is `api`.** `Modifier` is a
  parameter of 25 public composables there; `SnackbarHostState`, `Typography`, `Color`, `Shape`,
  `ImageVector` and `LazyListState` each appear in at least one. A consumer cannot call
  `NoteItem(modifier = …)` without resolving `Modifier`, so `compose-bom`, `ui`, `ui-graphics`,
  `material3` and `foundation` are all `api`. What makes this easy to get wrong is that it *builds*
  either way today, because `:app` declares the same artifacts for its own screens — the same
  accident that hid `:core:data`'s DataStore and Room bindings until androidTest walked the graph.
  It would surface at the first `:feature:*` module that depends on `:core:ui` without redeclaring
  Compose. `coil-compose`, `kotlinx-coroutines-android`, `ui-tooling-preview` and the icon packs
  stay `implementation`: used in function bodies, never handed out.
  - The check that does not require a second consumer: `./gradlew :core:ui:dependencies
    --configuration api` lists exactly what the module exports. If something in a public signature
    is missing from that list, it is declared wrong.
- Dispatchers and scopes: don't reference `Dispatchers.IO` directly in new code. Inject
  `@param:IoDispatcher private val ioDispatcher: CoroutineDispatcher` — the qualifier lives in
  `di/Qualifiers.kt` in `:core:domain` and its provider in `di/AppModule.kt` in `:app` — so tests
  can substitute a `TestDispatcher`, and keep the `@param:` use-site target the existing
  repositories use. All three qualifiers sit apart from their providers deliberately: the
  annotations are plain JSR-330 and have moved to the shared module, the `@Provides` methods
  cannot follow. `@ApplicationScope` provides the app-lifetime `CoroutineScope` for work that must
  outlive a ViewModel. The single deliberate exception is `data/datastore/DataStoreModule.kt`,
  where DataStore's own scope is built at module level.
- Build type: in code that lives below `:app`, or is headed there, don't read `BuildConfig.DEBUG` —
  inject `@param:DebugBuild private val isDebugBuild: Boolean` (qualifier in `di/Qualifiers.kt` in
  `:core:domain`, provider in `di/AppModule.kt` in `:app`). `com.jiahan.smartcamera.BuildConfig`
  belongs to the application module's namespace, so now that `data/` lives in `:core:data` that
  import no longer resolves there — a compile error, not a silent wrong value. `:core:data` declares
  its own namespace (`com.jiahan.smartcamera.core.data`), so it has neither `:app`'s `R` nor its
  `BuildConfig`. (AGP 8 libraries don't generate
  `BuildConfig` at all without `buildFeatures.buildConfig`, and if one does, its `DEBUG` tracks
  the *library's* variant, not the app's — which is where a silent wrong value would actually come
  from, if someone "fixed" the compile error by importing the local `BuildConfig`.) The concrete
  payoff today is testability: the static read made the release branch unreachable from a unit
  test, and `FirebaseRemoteConfigRepositoryTest` now pins both fetch intervals.

  **Don't generalize this to application-module code.** `BuildConfig.DEBUG` is a `static final
  boolean`, so R8 constant-folds it and strips the dead branch from the release binary; an injected
  flag is a runtime value and ships both. That is why `MyApp.kt` and `util/DefaultErrorHandler.kt`
  keep reading it directly — converting `DefaultErrorHandler` would ship its `Log.e` calls and make
  the Crashlytics-suppressing branch reachable in release. `di/AppModule.kt` reads it too, in the
  provider itself. (`SettingsScreen` used to read `BuildConfig.VERSION_NAME`; when it moved
  to `:feature:settings` that became a `versionName` parameter passed from `SmartPhotosNavGraph`.
  Note what was *not* done: a `@VersionName` qualifier beside `@DebugBuild`. The R8 argument that
  justifies `@DebugBuild`'s counterpart does not transfer, because a version string is display data
  rather than a branch condition — there is no dead branch to fold away, so hoisting it as a
  parameter is both simpler and, per Compose state hoisting, more correct. Reach for a qualifier
  when the value picks a code path; reach for a parameter when it is rendered.)
- Dependencies: every version lives in `gradle/libs.versions.toml` and is referenced through the
  generated `libs.*` accessors — no module build file holds a hardcoded version string. Add a
  library as a `[versions]` entry plus a `[libraries]` entry, never as an inline
  `implementation("group:artifact:1.2.3")`.
- Resources belong to the module whose code resolves them, and **`android.nonTransitiveRClass=true`
  means each module's `R` holds only its own.** A string a `:core:ui` composable passes to
  `stringResource` lives in `core/ui/src/main/res/`, translations included; `:app` then reaches it
  as `com.jiahan.smartcamera.core.ui.R`, imported as **`import com.jiahan.smartcamera.core.ui.R as
  UiR`** and used as `UiR.string.x`. The alias is needed because those files also use `:app`'s own
  `R`, and the two collide on the simple name. Ten `:app` files and six test files do this today,
  for the seventeen strings in `:core:ui` — sixteen that moved with `common/`, plus `cd_back`,
  which went there when `:feature:explore` was extracted rather than travelling with it.
  `:core:common` is reached the same way, as **`import com.jiahan.smartcamera.core.common.R as
  CommonR`** — five files across `:app`, `:feature:auth` and `:feature:profile` do that for its ten
  strings. A file needing both aliases is normal and not a smell: `AuthScreen` uses its own `R` for
  `password` and `login`, and `CommonR` for `email`, `name` and `username`, which `ProfileScreen`
  reads too.
  - **`:app` may read a *feature* module's `R`, and that is not a leak.** `:app` depends on every
    `:feature:*`, so `import com.jiahan.smartcamera.feature.profile.R as ProfileR` is a downward
    read like `UiR` — `navigation/TopLevelDestination.kt` does it for the Profile tab's label,
    beside the `UiR.string.search` it already used for Search. Reach for it when one string is the
    same copy for the same thing in both places; reach for a second string, as Home did with
    `cd_open_explore`, when the two usages only *look* alike. Now in Android's `TopLevelDestination`
    resolves its titles the same way.
  - **A string moves to the module that owns it, and "owns" means the only consumer.** Extracting
    `:feature:explore` needed six `:app` strings decided one at a time: four were exclusive and
    went with the code, `cd_back` had seven consumers across five packages and went *down* to
    `:core:ui` instead, and `explore` turned out to be two strings sharing one piece of copy — a
    screen title and Home's `contentDescription` for the button that opens it. The title travelled
    and Home got its own `cd_open_explore`. Expect this split at every feature extraction, and note
    that a consumer *count* cannot tell the last case from the second: only the call sites can.
  - **The same rule applies to drawables, and to functions.** `:feature:settings` took `dark_mode`
    and `translate` because nothing else drew them; `visibility`/`visibility_off` had consumers in
    settings and auth and went *down* to `:core:ui` with `PasswordField`. `validateNewPassword` went
    with the module for exactly the same reason a string does — settings was its only caller —
    while `validateUsername`/`validateDisplayName` went *down* to `:core:common` when
    `:feature:auth` was extracted, because auth and profile both call them, and the
    `ValidationResult` all three return went down to `:core:domain`. A shared *return type* has to
    land where every caller can see it, which is the same shape as `cd_back`. Note the sequencing
    lesson in that pair: the validators sat in `:app` carrying a comment saying they would move
    "when both of those packages become modules", and that wait was unnecessary. A shared function
    goes down as soon as the *first* caller leaves; it does not have to wait for the second.
  - **A vector drawable that moves out of `:app` may stop resolving `?attr/colorControlNormal`.**
    AppCompat reaches `:app` only transitively (via Firebase/Play), so a library module that
    receives such a drawable fails resource linking with `resource attr/colorControlNormal not
    found` — in `processDebugUnitTestResources`, well after the Kotlin compiles fine. When the
    drawable is only ever used through Compose's `Icon(painter = …)`, the fix is to delete the
    `android:tint` line rather than to add AppCompat: `Icon` applies its own `ColorFilter` over the
    painter, so the attribute was already being overridden. Verified pixel-identical against
    `settingsScreen_default.png`.
  - Don't "solve" a cross-module string by declaring it in both modules. Two resources with one
    name is legal — the application's value wins the merge — but it silently duplicates
    user-visible text and its `values-ja/` translation, and the two drift.
  - Don't turn `nonTransitiveRClass` off to avoid the aliasing. It is the reason a library's `R`
    stays small and its resources stay attributable; the import churn is the price, and it is
    one-off.
  - The alternative worth considering for a genuinely reusable component is hoisting the string out
    as a parameter, so the component takes text rather than resolving product copy itself. Not done
    for `NoteItem` — it already carries nine lambdas — but it is the right answer if `:core:ui`
    ever grows a component meant for reuse outside this app.
- Pagination: repositories hold no position state — the caller owns its place in the list, so two
  callers paginating at once can't corrupt each other. The key depends on the data source: notes
  page by an opaque `NoteCursor` (`getNotes(cursor)` returns a `NotePage` carrying the next one,
  null = first page), while Explore holds a page index because Unsplash pages by number. Either
  way the ViewModel keeps that key plus `pageSize` and `hasMoreData`, with `isRefreshing` and
  `isLoadingMore` as separate fields on the `*UiState` (not separate `StateFlow`s). Two rules that
  are easy to get wrong:
  - Derive "is there another page" from the rows the data source returned, never from the mapped
    domain list's size — mapping can drop rows (a failed author lookup, say), and a short list
    would then be read as "end of feed" and stop pagination for the rest of the session.
  - Route *every* path that rebuilds the list from the first page through one `reload()` (pull-to-
    refresh, a cross-feature add event, the initial load), and have it cancel any in-flight
    load-more before resetting the position, with load-more no-opping while a reload is active. A
    page fetched against the old position that lands after the reset splices a stale window into
    the new list — duplicating or skipping items depending on which finishes first. Guarding only the
    pull-to-refresh path is not enough; `HomeViewModel` had three such paths. A ViewModel running
    two independent paginated lists needs one pair of jobs per list — `ExploreViewModel` keeps a
    reload/load-more pair for the browse feed and another for search results.
- Paging 3 is deliberately not a dependency. It could be made to work — `getRefreshKey` may return
  null to restart from the first page, and a Firestore `PagingSource` exists in `firebase-ui` — but
  a `DocumentSnapshot` cursor is an awkward `PagingSource` key, Explore pages an Unsplash-backed
  Cloud Function by number rather than by cursor, and the hand-rolled pagination above already
  covers what the two feeds need. The reason is cost/benefit, not impossibility. Revisit if the
  notes feed moves into Room, where `PagingSource`/`RemoteMediator` is the natural fit.
- Firestore security rules (`firestore.rules`) deny all access by default; per-collection rules are
  additive (OR'd). Keep new collections behind an explicit `request.auth != null` (or stricter) rule.
- Cloud Functions code style is enforced by `eslint-config-google` — run the functions lint command
  above before committing changes under `functions/`.

### Kotlin coding conventions

Follows the [official Kotlin style guide](https://kotlinlang.org/docs/coding-conventions.html)
(`kotlin.code.style=official`). Project-specific points worth calling out beyond the official guide:

- Prefer the read-only collection types (`List`, `Map`) over `MutableList`/`MutableMap` across
  public API surfaces — but note that read-only is not immutable. Upcasting a `MutableList` to
  `List` hands the caller a live view the owner can still mutate underneath them, so when a property
  or return value is backed by a mutable field, copy at the boundary (`.toList()`) instead of
  relying on the declared type. "Mutate locally, expose read-only" only holds with that copy.
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
