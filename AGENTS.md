# SmartPhotos

Android app (Kotlin) for organizing photos/notes with ML-based tagging. Firebase backend + a
Node.js Cloud Functions project live in `functions/`.

Fifteen Gradle modules, plus an included build for the shared Gradle config:

- `:app` — UI, ViewModels, navigation, and app-level plumbing: `MyApp`, the messaging service,
  `DefaultErrorHandler`/`ResourceProviderImpl`, `FirebaseModule`, `AppModule`. Hosts the NavHost,
  supplies each screen's navigation lambdas, and installs the Hilt bindings — no feature screen
  renders here. Sources under `app/src/main/java/com/jiahan/smartcamera/`.
- `:core:domain` — pure Kotlin JVM (no Android Gradle plugin, no Hilt/KSP): domain models, the
  repository *interfaces*, `safeCall`, the `ErrorHandler` interface, DI qualifiers. Sources under
  `core/domain/src/main/kotlin/com/jiahan/smartcamera/`.
- `:core:common` — an Android library, deliberately not Compose: `validateUsername`/
  `validateDisplayName` and their strings, the `MediaFileRepository` contract, `util/MediaUriExt.kt`,
  and the two `@ViewModelScoped` classes every feature module shares — `note/NoteShareDelegate.kt`
  and `note/NoteErrorReporter.kt` (why this module has Hilt/KSP). Rule: an Android-typed *contract*
  belongs here; its implementation stays in `:core:data`. This is the module closest to needing a
  split — if an unrelated fifth tenant lands, split it before it needs a name like `:core:misc`.
  Sources under `core/common/src/main/kotlin/com/jiahan/smartcamera/`.
- `:core:data` — an Android library holding every implementation of a `:core:domain`/`:core:common`
  contract: the `Default*`/`Firebase*` repositories, the Room database, DataStore wiring,
  `DataModule`. Sources under `core/data/src/main/kotlin/com/jiahan/smartcamera/`.
- `:core:ui` — an Android library, shared Compose vocabulary: `common/` (14 composables),
  `ui/theme/`, and `util/DateTimeUtils.kt`/`FlowUtils.kt`. Sources under
  `core/ui/src/main/kotlin/com/jiahan/smartcamera/`.
- `:feature:explore` / `:favorite` / `:settings` / `:auth` / `:profile` / `:search` / `:home` /
  `:preview` / `:note` — one Android library per screen: its Compose screen(s), ViewModel(s), route,
  and tests. Each depends on `:core:*` only — never on another feature, never on `:core:data`
  directly (the repositories a ViewModel injects are interfaces, bound in `:app`), never on `:app`.
  Notable exceptions worth knowing: `:feature:preview` is the only module carrying ExoPlayer/Coil
  (the only screens that play video or load a full-screen image); `:feature:note` owns
  `IncomingShareHandler`, read downward by `:app`'s `AppModule`/`MainViewModel`; `:feature:search`
  owns `SEARCH_DEEP_LINK_URI_PATTERN`, read downward by the nav graph. Sources under
  `feature/<name>/src/main/kotlin/com/jiahan/smartcamera/`.
- `:core:testing` — an Android library of shared test fixtures: nine `fake/` repository doubles,
  `MainDispatcherRule`, `BaseScreenshotTest`. Taken as `testImplementation`
  (`androidTestImplementation` too wherever a `sharedTest/` runs in both), so nothing in it
  reaches a production classpath. Note it takes an `api` edge on `:core:data`, so `:core:data`
  cannot depend on it back — its own tests declare Robolectric directly. A **regular library module, not AGP's `testFixtures`** — that was
  tried and doesn't work, since the Kotlin Android plugin generates no Kotlin compilation for the
  testFixtures variant. Its dependencies are `api` throughout: a fixtures module's API surface is
  *other* modules' types (`FakeNoteRepository` **is** a `NoteRepository`).
- `build-logic/` — not a module but an included build, holding the six convention plugins
  (`smartphotos.android.application`, `.android.library`, `.android.compose`, `.android.feature`,
  `.android.screenshot`, `.jvm.library`); see [Convention plugins](#convention-plugins).

Kotlin package names are deliberately identical across modules (`com.jiahan.smartcamera.util`,
`.di`, `.common`, `.data.repository`, `.data.datastore`, …), so a type moving between modules is
usually a pure `git mv` with no import churn. Feature module *namespaces* don't follow that rule,
though: they're `com.jiahan.smartcamera.feature.<name>` even though the Kotlin packages stayed bare
(`.explore`, `.auth`, …), so each feature's `R` is reached as `import
com.jiahan.smartcamera.feature.<name>.R` — including from a file inside that module, since its own
`R` is not in its own Kotlin package. `:core:common` and `:core:ui` are the same shape:
`com.jiahan.smartcamera.core.common.R` / `com.jiahan.smartcamera.core.ui.R`.

Dependency arrows run one way: `:app` → `:core:data` → `:core:domain`; `:app` → `:core:ui` →
`:core:domain`; `:app` → `:core:data` → `:core:common` → `:core:domain`; `:app` → each
`:feature:*` → the `:core` libraries it needs. `:core:testing` hangs off test classpaths only. No
feature module depends on another, and none reaches `:core:data` — check with `./gradlew
:feature:profile:dependencies --configuration debugCompileClasspath`. Nothing depends on `:app`, so
a repository implementation can never reach a ViewModel, an `:app` `R` string, or `BuildConfig`.
`:core:ui` and `:core:data` are siblings — neither depends on the other.

## Build, test, lint

Run from the repo root (Gradle wrapper):

- Build debug APK: `./gradlew assembleDebug`
- Unit tests (JVM, Robolectric-backed): `./gradlew testDebugUnitTest :core:domain:test`
  - Both tasks: `:core:domain` is a plain Kotlin JVM module, so its tests run under `test`, not the
    Android-variant `testDebugUnitTest`, which every other (Android-library) module already runs
    under, as do `lintDebug` and `connectedDebugAndroidTest`. 410 tests total across 14 modules
    (`:core:testing` has no unit tests of its own — it *is* the fixtures).
  - Single class: `--tests "com.jiahan.smartcamera.home.HomeViewModelTest"`
  - Single method: `--tests "com.jiahan.smartcamera.home.HomeViewModelTest.methodName"`
  - Assert on a settled `StateFlow` by reading `.value`. Reach for
    [Turbine](https://github.com/cashapp/turbine) (`.test { ... }`) when the *sequence* matters, or
    for a `SharedFlow` event (no `.value` to read at all). Never hand-roll a collector into a list.
  - ViewModel tests replace `Dispatchers.Main` with `MainDispatcherRule` (`:core:testing`) —
    `@get:Rule val mainDispatcherRule = MainDispatcherRule()`, since `viewModelScope` dispatches to
    Main. Defaults to `UnconfinedTestDispatcher`; pass `StandardTestDispatcher` when a test needs
    virtual-time control (e.g. a debounce).
- Screenshot tests (Roborazzi) live beside the composable they capture, in four modules today:
  `:core:ui`, `:feature:home`, `:feature:search`, `:feature:settings` — and in none of them by
  accident, since a golden is only meaningful next to the composable it pins. Each keeps its own
  goldens under its own `src/test/screenshots/`; the shared harness (`BaseScreenshotTest`) is in
  `:core:testing`. A module capturing them applies `smartphotos.android.screenshot` and nothing
  else — that plugin brings the Roborazzi tasks, the VCS-tracked `outputDir` and
  `unitTests.isIncludeAndroidResources`. It still needs
  `debugImplementation(libs.androidx.ui.test.manifest)` on top, which the feature convention
  already supplies to a `:feature:*` module, and `:core:ui` declares for itself — without it
  `createComposeRule()` fails to resolve an activity. `testDebugUnitTest` *runs* these tests but does
  **not** diff them against goldens — only `./gradlew verifyRoborazziDebug` does (what CI runs before
  pushing UI changes). Re-record with `./gradlew recordRoborazziDebug` when a diff reflects an
  intended change, and inspect the new PNGs before committing.
- **Moving a type between modules can leave KSP's incremental state stale**, producing a wall of
  `InjectProcessingStep was unable to process 'X(…,Foo,…)' because 'Foo' could not be resolved` from
  `:app:kspDebugKotlin` for a type that's actually on the classpath and compiles fine on its own.
  `./gradlew clean` fixes it — check this before assuming an `api`/`implementation`
  misconfiguration (see [Conventions](#conventions)); the tell is that the unresolved name is
  unqualified while its neighbours from the same module are fully qualified.
- **`./gradlew compileDebugAndroidTestKotlin` catches Hilt graph errors nothing else does** —
  `assembleDebug`, unit tests, Roborazzi and lint never compile androidTest sources, and
  `di/HiltGraphSmokeTest.kt`'s member injection walks the binding graph furthest. Run it after any
  change to a module's dependencies or an `@Inject` constructor, especially without a device handy.
- **`./gradlew assembleRelease` is the only thing that compiles the release variant.** Every other
  check — `assembleDebug`, the unit tests, Roborazzi, `lintDebug` — compiles debug, so a dependency
  that reaches a module only through `debugImplementation` compiles clean everywhere else and fails
  here. That is not hypothetical: it is exactly how `:feature:auth`'s `@Preview` broke
  `compileReleaseKotlin` (see the note in its build file), unnoticed because CI built no release
  either. **CI now runs it**, so the gap is closed for anything that reaches `main` — but run it
  locally after changing any module's dependency block, alongside `compileDebugAndroidTestKotlin`
  above. Between them they cover the two variants nothing else does.
- Instrumented tests (`./gradlew connectedDebugAndroidTest`) need a device/emulator; live in ten
  modules — `app/`, `core/data/`, and every `:feature:*` except `:feature:explore`. `:app` uses a
  custom `HiltTestRunner`, the AndroidX Test Orchestrator, and `clearPackageData=true` for hermetic
  runs — don't remove these without understanding why. The library modules declare no
  `testInstrumentationRunner` of their own; their suites build a ViewModel from `:core:testing`'s
  fakes and inject nothing.
  - **`sharedTest/` is the arrangement to copy for a new screen test.** A Compose behaviour suite
    placed there compiles into *both* the unit-test and androidTest source sets, so it runs on the
    JVM under Robolectric and on-device, written once — `:feature:home`'s `HomeScreenTest` and
    `:feature:auth`'s `AuthScreenTest` do this (two `sourceSets` lines plus
    `unitTests.isIncludeAndroidResources = true`). But check an existing androidTest-only suite's own
    note before promoting it: `ProfileScreenTest` is device-only because its bottom-anchored save
    button and inline validation text depend on real viewport/scroll behaviour, and moving it to
    `sharedTest` confirmed exactly that (three of five tests passed under Robolectric, two failed).
  - The same suite also runs on Firebase Test Lab's device farm via `./scripts/run-test-lab.sh`
    (needs the `gcloud` CLI, auth, and the Blaze plan).
- Distribute a debug build via Firebase App Distribution: `firebase login` once, then
  `./gradlew assembleDebug appDistributionUploadDebug` (the `testers` group must exist under
  Firebase console > App Distribution first).
- Cloud Functions (`functions/`, Node 24): `npm --prefix functions run lint` (eslint, google
  config). `npm --prefix functions run serve` / `deploy` for emulate/deploy (needs Firebase CLI auth).

### CI

`.github/workflows/ci.yml` runs on every push to `main`, every pull request, and on demand: it
builds the debug APK and the release APK, then runs the unit tests, the screenshot comparison, and
`lintDebug` as separate steps on JDK 21 (each runs even if an earlier one failed, so one run reports
every problem), plus a parallel job linting `functions/`.

The release step is `assembleRelease -x uploadCrashlyticsMappingFileRelease`, and both halves are
deliberate: it is the only step that compiles the release variant or runs R8, and the exclusion
keeps CI from uploading a mapping file to Crashlytics for a build it will never ship. Everything
else in the job builds debug, which is how a `debugImplementation`-only dependency stayed green here
while `compileReleaseKotlin` was broken.

It needs one repository secret, `GOOGLE_SERVICES_JSON` — `app/google-services.json` is gitignored
and the Google Services plugin fails the build without it. Generate the value with `base64 -i
app/google-services.json` and paste it into Settings > Secrets and variables > Actions as a
*repository* secret (the job declares no environment).

Each run uploads two artifacts: `unit-test-report` and `screenshot-and-lint-reports` (download the
latter on a screenshot failure — its `*_compare.png` files show reference/diff/actual side by side).
**Both artifact lists are literal per-module paths, not a glob — a new module that captures
screenshots or produces a lint report must be added to both by hand**, or a failure in it reports as
a red X with nothing to open.

Two things to know about the goldens:

- The unit-test JVM is pinned to UTC/en-US (`configureTestJvm()` in `build-logic`, applied by every
  convention plugin), because any screenshot rendering a note's timestamp goes through
  `Long.toFormattedDateTime()`, whose `zone`/`locale` default to the system's — without the pin,
  goldens are machine-dependent (they'd pass on a UTC+8 laptop and fail on the UTC CI runner). If you
  change the pin, re-record. More generally: **a golden diff that appears only on CI is far more
  likely to be non-determinism in the test than a rendering difference between platforms** — check
  for a clock, locale, or random value in the fixture before assuming the environment is at fault.
- **A golden that renders a build-varying value is a hoisting problem, not a re-recording chore.**
  `SettingsScreen` takes `versionName` as a parameter (it has to — `:feature:settings` is a library
  with no application `BuildConfig`) and the test pins `"1.0.0"`, instead of the screenshot going
  stale on every version bump.

### Convention plugins

`build-logic/` is an included build (`includeBuild("build-logic")` from `pluginManagement` in
`settings.gradle.kts`), holding six plugins that every module applies by id instead of restating
the same settings:

| Plugin | Applied by | Applies | Sets |
| --- | --- | --- | --- |
| `smartphotos.android.application` | `:app` | AGP application, Kotlin Android | compileSdk 37, minSdk 28, Java 11, JVM target 11, test-JVM pin |
| `smartphotos.android.library` | `:core:common`, `:core:data`, `:core:ui`, `:core:testing` | AGP library, Kotlin Android | the same |
| `smartphotos.android.compose` | `:app`, `:core:ui`, `:core:testing` | Compose compiler | `buildFeatures.compose = true` |
| `smartphotos.android.feature` | all nine `:feature:*` modules | the library + compose conventions, KSP, Hilt | the `:core:domain`/`:core:ui` edges, the Compose set, icons, Hilt, lifecycle, `ui-test-manifest`, `:core:testing` |
| `smartphotos.android.screenshot` | `:core:ui`, `:feature:home`, `:feature:search`, `:feature:settings` | Roborazzi | `outputDir` → `src/test/screenshots`, `unitTests.isIncludeAndroidResources` |
| `smartphotos.jvm.library` | `:core:domain` | Kotlin JVM — **nothing Android** | Java 11, JVM target 11, test-JVM pin |

`.android.screenshot` is also the one that needs a plugin *artifact* in `build-logic`'s own
dependencies (`roborazzi-gradlePlugin`, `compileOnly`), because it configures Roborazzi's own
extension rather than just applying it by id — the same distinction the Compose note there draws
from the other side.

A feature module's build file should contain only what that feature alone needs beyond the
convention — e.g. explore keeps `coil-compose`/`activity-compose`; settings keeps
`androidx-core-ktx`/Roborazzi. `build-logic` looks the version catalog up by string name
(`Project.libs` in `buildlogic/VersionCatalog.kt`), since the generated `libs.androidx.material3`-
style accessors are a build-script-only feature — **an alias typo there fails at configuration time
in the consuming module, not at compile time in `build-logic`.**

Rules worth knowing before editing these plugins:

- **Put a setting here only when more than one module wants it, for the same reason.** `targetSdk`
  stays per-app-module, `namespace` stays per-library, `buildConfig = true` stays in `:app` only
  (see the Build type rule under [Conventions](#conventions)).
- **One module is a sample size of one — wait for the second before writing a shared convention.**
  `smartphotos.android.feature` didn't exist while `:feature:explore` was the only feature module,
  because there was no way to tell which lines were *the shape of a feature* versus *the shape of
  Explore*. A second consumer (`:feature:settings`) is what answered it — and answered it partly
  against expectation (the icon packs, assumed Explore-specific, turned out shared).
- **`build-logic` targets Java 17, the modules target 11.** Not drift — the convention plugins run
  in the Gradle daemon (needs 17+), while 11 is what the app compiles against. Don't "fix" one to
  match the other.
- **The plugin artifacts (`android-gradlePlugin`, `kotlin-gradlePlugin`, `compose-gradlePlugin`) are
  `compileOnly`**, used only by `build-logic/convention`. The modules'
  `pluginManager.apply("com.android.application")` calls resolve against the build classpath the
  root `build.gradle.kts` establishes with its `apply false` block — that block must keep listing
  them.
- **AGP 9's `CommonExtension` is not generic and exposes only property accessors** —
  `defaultConfig.minSdk = …`, not the `defaultConfig { }` block form shown in AGP-8-era guides.

## Architecture

MVVM with a layered structure, one Gradle module per feature. **All nine feature modules are
extracted**, and `:app` is down to ~1,350 lines: `MainActivity`, `MyApp`, `MainViewModel`,
`SmartPhotosApp`, `navigation/`, the messaging service, the two DI modules, and four `util/`
implementations. It hosts the NavHost, supplies each screen's navigation lambdas, and installs the
Hilt bindings — nothing that renders a feature screen lives there. Cross-cutting layers:

- **UI** — Jetpack Compose screens (`*Screen.kt`) + the Navigation Compose graph in
  `navigation/SmartPhotosNavGraph.kt`, with each destination's route type in the feature package
  that owns it (`home/HomeRoute.kt`, `preview/PreviewRoutes.kt`, …).
- **ViewModel** — `@HiltViewModel` classes exposing a `*UiState` data class via `StateFlow`. The
  loading/loaded/error branch is a nested sealed sub-type (e.g. `HomeContent` in
  `home/HomeViewModel.kt`), kept separate from flat fields on the outer `*UiState` for orthogonal UI
  state (`isRefreshing`, dialogs, pagination) that shouldn't force a full state-machine branch.
- **Repository** (`data/repository/`) — one interface + one `Default*` implementation per
  repository (e.g. `NoteRepository`/`DefaultNoteRepository`), bound in `data/di/DataModule.kt`.
  Coordinates Firebase Firestore/Storage (remote) and Room/DataStore (local). Interfaces live in
  `:core:domain`; implementations and `DataModule` in `:core:data`. Two interfaces can't live in
  `:core:domain` because their signatures carry Android types: `AppUpdateRepository`
  (`ActivityResultLauncher`/`IntentSenderRequest`) stays in `:core:data` beside its `Default*` since
  only `:app`'s `MainViewModel` injects it; `MediaFileRepository` (`Bitmap`/`Uri`) moved to
  `:core:common` because a feature module needed to inject it and must not depend on `:core:data`.
  Move the next Android-typed interface down only when a feature needs it.
- **Domain** (`domain/`, in `:core:domain`) — plain data classes shared across features (`HomeNote`,
  `MediaDetail`, `User`, …). No Android plugin on the module, so a stray `import android.*` fails
  the build — purity is enforced by the compiler, not by review.
- **Local** — Room database in `database/` (schemas exported to `core/data/schemas/`), DataStore
  preferences in `data/datastore/` (contract + model, `UserPreferencesRepository`/
  `UserPreferences`, in `:core:domain`; wiring in `:core:data`). A note's media list persists into
  the `notes.media_list` column as `kotlinx.serialization` JSON keyed by `MediaDetail`'s property
  names — an on-disk format, so renaming one needs `@SerialName` to keep old rows decodable.
- **Remote** — Firebase (Auth, Firestore, Storage, Remote Config, Analytics, Crashlytics, FCM) plus
  Cloud Functions in `functions/index.js` calling Google Cloud Vision API for text/label/object
  detection on uploaded photos.

### Separation of concerns

- Composables render state and forward user intents; they never call Firebase/Room/DataStore
  directly or hold business logic beyond simple derived/UI-only state (scroll position, sheet
  visibility).
- ViewModels own presentation logic and state transformation; they depend on repository
  *interfaces* (dependency inversion), never `Default*` implementations or Firebase/Room types
  directly, so they stay unit-testable without a real backend.
- Repositories own data-source coordination and expose domain models — never Firestore
  `DocumentSnapshot`/`QuerySnapshot` or Room entities — across the interface boundary. Every
  fallible operation returns `Result<T>` rather than throwing, so callers never wrap a call in
  try/catch; wrap the body in `util/safeCall`. The exemptions are the ones that can't carry a
  `Result`: a `Flow`-returning stream, and fire-and-forget work like `quickUploadMediaToFirebase`,
  which logs its failures internally. Firestore is the source of truth and writes go there first,
  then Room — see [Source of truth](#source-of-truth).
- Domain models are plain data with no Android/Firebase/Room dependencies. A local media location
  crosses this boundary as `domain/MediaUri.kt`, never `android.net.Uri` — convert with
  `toMediaUri()`/`toPlatformUri()` (`util/MediaUriExt.kt`, `:core:common`) at the ViewModel boundary
  on the way down or inside a `Default*` implementation on the way out.

When adding a feature, prefer extending this layering over reaching across it (a screen calling
`FirebaseFirestore` directly, or a repository returning a Room `@Entity` to a ViewModel).

### Source of truth

**A deliberate deviation from the offline-first architecture guide.** That guidance makes the local
database the source of truth: the UI observes Room, writes land locally first, a sync layer
reconciles with the backend. This app does the opposite — Firestore is the source of truth and Room
is written only after the Firestore write returns. The four note-rendering screens (Home, Search,
NotePreview, Favorite) *read* the Room mirror, which is the read half of offline-first, not the
write half: there are still no offline writes and no reconciliation.

Each of those screens exposes a `content: StateFlow` built with `combine(<a Room query>, <a fetch
status>)` and shared `WhileSubscribed`. The remote call still happens, but none of them hands its
result straight to the UI — each write-through fills the `notes` table and the screen re-reads it:

| Screen | Query | Fetch that fills it |
| --- | --- | --- |
| Home | `getNotesStream(limit)` | `getNotes(cursor)` |
| Search | `searchNotesStream(query)` | `searchNotes(query)` |
| NotePreview | `getNoteStream(noteId)` | `getNote(noteId)` |
| Favorite | `getFavoriteNotesStream(query)` | `syncFavoriteNotes()` |

**Copy this `combine` shape for a new mirrored list.** Fetch status decides only what an *empty*
result means (nothing fetched yet = loading, fetch failed = error) — any cached rows beat both, so a
failed refresh keeps the list on screen and reports itself through `actionError` rather than
blanking the feed.

Rules that keep this correct:

- `getNotesStream` takes a `limit`, widened one page at a time as the cursor advances (and reset
  wherever the cursor resets). Without it Home would render the whole table, not just what it has
  paged — Search and NotePreview write into `notes` too.
- A mirror-write failure must **fail the fetch**, not log-and-continue — with screens rendering the
  table, a swallowed write is a note the user can't see, with the cursor already advanced past it.
- **Every remote read writes what it fetched into the table before returning.** A new remote read
  that skips this renders nothing, since screens never look at return values. `addNote` reads its
  own note back via `getNote` after creating it, because the createNote Cloud Function returns only
  `{documentPath}` — the id and server-stamped `created` exist only server-side.
- `syncFavoriteNotes` reconciles only the favorited rows (clears + reinserts what the server says is
  favorited); nothing reconciles the rest of the table.

Known gaps, so nobody rediscovers them as bugs: **no offline writes** (a mutation with no network
fails at the Firestore call and never reaches Room), and **no reconciliation** (a row deleted
server-side, or on another device, lingers locally until something rewrites it). Both are a larger,
deliberately out-of-scope project.

Get the empty state right when mirroring a new screen: Room answers before the first fetch does, so
a fresh install must not render "create your first note" while the first fetch is still in flight —
see `HomeViewModel`'s `FetchStatus.Pending` branch and its two pinning tests. The mirror image is a
*failed* fetch: right to full-screen-error on an empty cache, wrong on a populated one — with rows
on screen, the failure goes to `actionError` as a snackbar instead.

### Cross-feature communication

**There is no cross-feature communication mechanism, and that is deliberate.** A screen that must
reflect a mutation made on another screen observes the Room mirror
([Source of truth](#source-of-truth)) instead of listening for an event. **Do not reintroduce one.**
If a list *could* be backed by a live query, back it with the query.

For an event a screen must never miss — the one still in use is `note/IncomingShareHandler.kt` —
use a `StateFlow` holding the pending value plus an explicit `consume()`, not a `SharedFlow`: a
default `MutableSharedFlow` has no replay, so a subscriber that isn't collecting yet silently misses
the event.

### Error handling

Route thrown errors through `util/ErrorHandler` rather than reading `Throwable.localizedMessage`
directly. Its two methods belong to different layers:

- **`logError(throwable, tag)` — any layer, repositories included.** Only touches `Log` (debug) and
  Crashlytics (release), so a repository logging a failure it swallows is correct.
- **`getErrorMessage(throwable)` — ViewModel layer only.** Resolves a string resource through
  `ResourceProvider`, making its result user-facing presentation, not data. Repositories must not
  call it: they log and then either propagate the exception or fold it into a `Result`/null return,
  and the ViewModel converts that into a `*UiState` error field.

**A repository that needs to raise its own failure throws a `domain/AppError`, never a message.**
Resolving a string resource is presentation, so building one in a repository
(`IllegalStateException(context.getString(...))`) puts ViewModel-layer work in the data layer and
forces a `Context` into a class that otherwise needs none. `AppError` is a sealed type carrying an
identity (`NotAuthenticated`, `NoteUnavailable`, `NoMediaAvailable`, `UsernameTaken`,
`UsernameReserved`, …); `appErrorMessageResId` maps each to a string and is applied *inside*
`getErrorMessage`, so a ViewModel already routing failures through `ErrorHandler` renders it with no
extra code. Add a case to the sealed type and the mapper together.

**This splits the test as well as the code.** A repository test asserts the `AppError` the
repository raised; the string that error resolves to is `ErrorMessageMappersTest`'s, in the module
that owns the mapping. A data-layer test that asserts user-facing English is reaching a layer up for
its assertion, and the tell is mechanical: it cannot move into `:core:data` with its subject,
because `DefaultErrorHandler`, `ResourceProviderImpl` and `:app`'s `R` do not exist down there.

**A Firebase type read above the repository boundary is a module boundary waiting to be
violated** — fold it into an `AppError` before it is, not after. A Firebase-specific fold (e.g.
reading a `FirebaseFunctionsException`'s code or structured payload) belongs *below* the repository
boundary, inside the `Default*` implementation (see `DefaultNoteRepository.foldNoteValidationError`
/ the equivalent in `DefaultUserRepository`) — never in a ViewModel-layer mapper, which would put
`firebase-functions` on a feature module's classpath.

The three pieces live in three files, by layer: `util/ErrorHandler.kt` (the interface + `ErrorTag`,
`:core:domain`, no imports), `util/DefaultErrorHandler.kt` (Android/Firebase-bound implementation,
`:app`), `util/ErrorMessageMappers.kt` (the `R`-resolving `appErrorMessageResId` mapper, `:app`,
several cases resolving `:core:common`'s `R` since those strings have a second reader down there).
Keep a new mapper in the third file rather than reuniting them.

### Kotlin Multiplatform readiness

We may migrate parts of this codebase (`domain/`, repository interfaces, other business logic) to
Kotlin Multiplatform down the line. This isn't a mandate to add KMP tooling now, but when choosing
between otherwise-equivalent approaches, prefer the one that keeps that migration cheap:

- Keep `domain/` models and repository *interfaces* free of Android/Firebase/Room types — required
  by [Separation of concerns](#separation-of-concerns) above, and enforced by the compiler rather
  than review, since they live in `:core:domain`, which has no Android Gradle plugin.
  **Be honest about what module extraction alone buys**, though: `:core:data` and `:core:ui` are
  Android libraries full of Firebase/Room/Compose, so nothing in them became shareable by moving —
  that's modularization, not KMP progress. Same for the `:feature:*` modules — Compose end to end,
  and the *least* shareable code in the build. Don't report "modules extracted" and "KMP progress"
  as one number.
- Prefer `kotlinx` libraries (`kotlinx.coroutines`, `kotlinx.datetime`, `kotlinx.serialization`)
  over equivalents with no `commonMain` implementation (`java.time`, Gson) in shared-leaning code,
  when a choice exists.
- Keep `android.*` out of the *contracts*: domain models, repository interfaces, and the data
  classes those interfaces carry. `Default*` implementations are Android-bound by definition and
  aren't what this rule targets. One interface is deliberately exempt: `MediaFileRepository` keeps
  its `Uri`/`Bitmap` parameters because it exists precisely to wrap `ContentResolver`/`FileProvider`
  work behind a seam — don't wrap those parameters to satisfy this rule, and don't cite it as
  precedent for a new contract.
- **The next actual KMP step is converting `:core:domain` from `kotlin.jvm` to
  `kotlin.multiplatform`** (`commonMain` + `androidTarget()`). Nearly every import in its `main`
  sources is already multiplatform (`kotlin.time.Instant`, `kotlinx.coroutines`, `kotlinx.datetime`,
  `kotlinx.serialization`) with one exception — `javax.inject.Qualifier` in `di/Qualifiers.kt`,
  which would move to an `androidMain` source set since Hilt is Android-only.
- **The ceiling to know about before planning further:** Hilt has no KMP support, and it's
  load-bearing below `:app` — every `Default*` is `@Inject constructor` and `DataModule` is
  `@InstallIn(SingletonComponent::class)`. A shared data layer would need Koin or hand-written
  constructor wiring with Hilt confined to the Android edge. That decision, not module splitting, is
  what sets how far KMP can go here.
- The module *names* deviate from Google's Now in Android deliberately: `:core:domain` here holds
  domain models, pure repository interfaces, `safeCall`, and DI qualifiers — NiA's equivalent split
  keeps repository implementations alongside the interfaces, which isn't possible here since
  `:core:data` needs the Android plugin. Keep this note beside the name rather than renaming to
  `:core:model` (inaccurate — it holds more than models).

## Follow official Android guidance

When implementing or reviewing changes, prefer solutions aligned with Google's official Android
guidance over ad-hoc approaches:

- [Android app architecture guide](https://developer.android.com/topic/architecture) —
  unidirectional data flow, `StateFlow`/`UiState` exposed from ViewModels (not events polled by the
  UI), repositories as the single source of truth — with the one documented exception that the
  notes feed reads Firestore directly ([Source of truth](#source-of-truth)).
- [Jetpack Compose guidance](https://developer.android.com/develop/ui/compose/documentation) —
  state hoisting, `remember` for recomposition efficiency, avoiding side effects outside
  `LaunchedEffect`/`DisposableEffect`. Use `derivedStateOf` only where a frequently-changing state
  feeds a rarely-changing derived value (a scroll offset driving an `isScrolled` boolean); applied
  more broadly it adds overhead instead of removing it.
- [Kotlin coroutines & Flow best practices](https://developer.android.com/kotlin/coroutines/coroutines-best-practices)
  — scope coroutines to `viewModelScope`, inject `CoroutineDispatcher`s rather than hardcoding
  `Dispatchers.IO`, avoid `GlobalScope`. The UI here is Compose-only, so `LaunchedEffect` and
  `rememberCoroutineScope` are the composition-side equivalents; `lifecycleScope` appearing in a new
  file is a smell, not a convention.
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
- There are three tiers, not two. `remember`/`mutableStateOf` for state that is genuinely local and
  ephemeral to composition (IME visibility, an animation trigger). `rememberSaveable` for local
  state that must also survive configuration change and process death (a half-typed field, an
  expanded section). The ViewModel's `UiState` for anything another screen needs or that outlives
  the composition — with `SavedStateHandle` for the parts of it that must survive process death.
- "Put it in the ViewModel" does not by itself mean "it survives" — a ViewModel is cleared when its
  `NavBackStackEntry` is popped, and no ViewModel survives process death. `rememberSaveable` appears
  nowhere in `app/src/main` today, so no UI state currently survives process death — that's a gap,
  not a convention to copy; reach for the right tier when adding state a user would be annoyed to
  lose.

### One-off UI events

Snackbars and other fire-and-forget signals travel from ViewModel to screen on a
`MutableSharedFlow(extraBufferCapacity = 1)` exposed as a read-only `SharedFlow`, collected in the
screen's `LaunchedEffect` and shown through `SnackbarHostState`. `actionError` (Home, Search,
Favorite and the preview screens, all via `:core:common`'s `NoteErrorReporter`) and
`ProfileViewModel.events` are the existing instances — follow their shape rather than inventing a
third.

**This deviates from the [official guidance](https://developer.android.com/topic/architecture/ui-layer/events)**,
which says a ViewModel event should update `UiState` with the UI signalling consumption back. It
stands because these signals have no state to restore — a snackbar already shown must not reappear.
Two limits that come with it:

- Anything that must survive configuration change or process death is not one of these — error text
  a screen keeps displaying belongs in `UiState` (e.g. `HomeContent.Error`); only the transient,
  toast-like signal goes on the flow.
- `tryEmit` into a one-slot buffer drops silently when events land back-to-back with no collector
  ready, and a `LaunchedEffect` collector only exists while the composition does. Don't put anything
  the user must not miss on this flow.

### Navigation

Navigation Compose's type-safe routes: each destination is a top-level `@Serializable`
`data object`/`data class`, registered with `composable<HomeRoute> { ... }` and reached via
`navController.navigate(NotePreviewRoute(id))`. Add destinations that way — no hand-built path
strings, no `navArgument` lists, no manual URL escaping of arguments.

- **The route type lives in the feature package, beside the screen it names** — `home/HomeRoute.kt`,
  `search/SearchRoute.kt`, `note/NoteRoutes.kt`, `preview/PreviewRoutes.kt`, and so on. What stays
  in `navigation/` is the wiring that legitimately needs to see every route at once:
  `SmartPhotosNavGraph.kt`, `BottomNavItem.kt`, `NavTransitions.kt`. Put a new route with its
  screen, not in `navigation/` — that's what lets a feature's ViewModel read its own arguments back
  with `toRoute<…>()` without importing upward.
- **The routes share no supertype, deliberately** — a route in a feature module can't implement an
  interface declared in `:app`, so `startDestination` and `BottomNavItem.route` are typed `Any`,
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

- **DI**: Hilt, all *modules* `@InstallIn(SingletonComponent::class)`. Constructor-injected classes
  may still be narrower — `:core:common`'s `NoteErrorReporter` is `@ViewModelScoped` — so "all
  modules are singleton" isn't "everything is a singleton". App-wide bindings live in
  `di/AppModule.kt`/`di/FirebaseModule.kt` (both in `:app`); other cross-cutting layers get their
  own module — `util/di/UtilModule.kt` in `:app`, `data/di/DataModule.kt` and
  `database/di/DatabaseModule.kt` in `:core:data`. There's no per-feature `di/` package yet — follow
  this layer-scoped pattern rather than introducing one.
- **`:core:data` dependency configurations**: anything whose type appears in an `@Inject
  constructor` parameter there must be `api`, not `implementation`. Hilt aggregates every
  `@InstallIn(SingletonComponent::class)` binding into a single component generated in `:app`, so
  `:app`'s annotation processor has to resolve those parameter types itself; hiding one behind
  `implementation` fails with `InjectProcessingStep was unable to process 'x' because 'Y' could not
  be resolved`. Under Hilt a library's constructor parameters are effectively part of its API. A
  dependency used only inside function bodies stays `implementation` (`firebase-storage`,
  `play-app-update-ktx`). The failure surfaces in `compileDebugAndroidTestKotlin` well before
  `connectedDebugAndroidTest`, and can hide from `assembleDebug` entirely.
- **`:core:ui` dependency configurations**: the same rule from the other direction — **a Compose
  artifact whose type appears in a public signature is `api`.** `Modifier`, `SnackbarHostState`,
  `Typography`, `Color`, `Shape`, `ImageVector`, `LazyListState`, and so on each appear in a public
  composable's signature, so `compose-bom`, `ui`, `ui-graphics`, `material3`, `foundation` are all
  `api`. `coil-compose`, `kotlinx-coroutines-android`, `ui-tooling-preview` and the icon packs stay
  `implementation`: used in function bodies, never handed out. Check with `./gradlew
  :core:ui:dependencies --configuration api` — if something in a public signature is missing from
  that list, it's declared wrong.
- **Dispatchers and scopes**: don't reference `Dispatchers.IO` directly in new code. Inject
  `@param:IoDispatcher private val ioDispatcher: CoroutineDispatcher` — the qualifier lives in
  `di/Qualifiers.kt` in `:core:domain`, its provider in `di/AppModule.kt` in `:app` — so tests can
  substitute a `TestDispatcher`. `@ApplicationScope` provides the app-lifetime `CoroutineScope` for
  work that must outlive a ViewModel. The one deliberate exception is
  `data/datastore/DataStoreModule.kt`, where DataStore's own scope is built at module level.
- **Build type**: in code that lives below `:app`, or is headed there, don't read
  `BuildConfig.DEBUG` — inject `@param:DebugBuild private val isDebugBuild: Boolean` (qualifier in
  `di/Qualifiers.kt`, provider in `di/AppModule.kt`). `com.jiahan.smartcamera.BuildConfig` belongs
  to the application module's namespace, so it's a compile error below `:app`, not a silent wrong
  value. **Don't generalize this to application-module code**: `BuildConfig.DEBUG` is a `static
  final boolean`, so R8 constant-folds it and strips the dead branch from the release binary; an
  injected flag is a runtime value and ships both. `MyApp.kt` and `util/DefaultErrorHandler.kt` keep
  reading it directly for that reason. For a value that's *rendered* rather than branched on (e.g.
  `versionName`, `logoRes`), hoist it as a parameter instead of adding a qualifier — there's no dead
  branch to fold away.
- **Dependencies**: every version lives in `gradle/libs.versions.toml` and is referenced through the
  generated `libs.*` accessors — never an inline `implementation("group:artifact:1.2.3")`.
  **A module declares what its own sources name, and a dependency it stops naming is deleted, not
  left.** Unused ones cost nothing at compile time and so survive every refactor that should have
  removed them: `:app` carried ML Kit ×4, GenAI, media3 ×4 and `material-icons-extended` for the
  length of the whole feature split, long after the code that wanted them had moved into a module of
  its own. Two exceptions, both flagged in place where they are declared: an artifact that is loaded
  reflectively or auto-initialises (`firebase-perf`, `coil-network-okhttp`), and a compiler plugin
  whose absence changes codegen rather than resolution (`kotlin-serialization` in `:core:data`).
  Check with `./gradlew :<module>:dependencies --configuration debugCompileClasspath` and, for what
  the sources actually reference, a grep for the import root.
- **A test lives in the module that owns its subject.** Tests do not move themselves when a class
  does, and nothing fails when they stay: `:app` kept both `Default*Repository` suites and a
  screenshot test of two feature screens because its test classpath can still see all of them.
  When a test cannot follow its subject down, that is a finding, not a reason to leave it — it means
  the test is asserting something from a layer above. The two repository suites asserted the
  rendered *message* through `:app`'s `DefaultErrorHandler`; splitting the assertion at the
  `AppError` identity (the message half was already pinned by `ErrorMessageMappersTest`, where it
  belongs) is what let them move. See [Error handling](#error-handling).
- **Resources**: they belong to the module whose code resolves them, and
  `android.nonTransitiveRClass=true` means each module's `R` holds only its own. A `:core:ui` string
  is reached from elsewhere as `import com.jiahan.smartcamera.core.ui.R as UiR`; `:core:common`'s
  the same way as `CommonR`. `:app` may also read a *feature* module's `R` downward (e.g.
  `import com.jiahan.smartcamera.feature.profile.R as ProfileR`), when one string is genuinely the
  same copy for the same thing in both places (a destination name doubling as a bottom-bar label).
  Rules:
  - **A resource/function moves to the module that owns it, and "owns" means the only consumer.**
    It moves down (to `:core:ui`/`:core:common`/`:core:domain`) as soon as the *first* second caller
    appears — don't wait for a hypothetical second feature. A consumer *count* can't tell "shared
    vocabulary" (`cd_back`, `no_results_found`) from "two strings that happen to share a word" (a
    screen title vs. a `contentDescription`, a tab label vs. a preview title) — only the call sites
    can. A shared *return type* (e.g. `ValidationResult`) has to land where every caller can see it,
    same shape as a shared string.
  - A vector drawable moved out of `:app` may stop resolving `?attr/colorControlNormal` (AppCompat
    reaches `:app` only transitively). If it's drawn only through Compose's `Icon(painter = …)`,
    delete the `android:tint` line rather than adding AppCompat — `Icon` already overrides it with
    its own `ColorFilter`.
  - Don't "solve" a cross-module string by declaring it in both modules — the application's value
    wins the merge, but it silently duplicates user-visible text and its translations, and the two
    drift.
  - Don't turn `nonTransitiveRClass` off to avoid the aliasing — it's why a library's `R` stays
    small and its resources stay attributable.
  - For a genuinely reusable component, consider hoisting the string out as a parameter instead of
    having the component resolve product copy itself.
- **Pagination**: repositories hold no position state — the caller owns its place in the list, so
  two callers paginating at once can't corrupt each other. The key depends on the data source: notes
  page by an opaque `NoteCursor` (`getNotes(cursor)` returns a `NotePage` carrying the next one,
  null = first page), while Explore holds a page index (Unsplash pages by number). The ViewModel
  keeps that key plus `pageSize` and `hasMoreData`, with `isRefreshing`/`isLoadingMore` as separate
  fields on the `*UiState` (not separate `StateFlow`s). Two rules easy to get wrong:
  - Derive "is there another page" from the rows the data source returned, never from the mapped
    domain list's size — mapping can drop rows (a failed author lookup), and a short list would then
    be read as "end of feed."
  - Route *every* path that rebuilds the list from the first page (pull-to-refresh, a cross-feature
    add, the initial load) through one `reload()`, cancelling any in-flight load-more before
    resetting position, with load-more no-opping while a reload is active. A page fetched against the
    old position that lands after the reset splices a stale window into the new list. A ViewModel
    running two independent paginated lists needs one reload/load-more job pair per list.
- **Paging 3 is deliberately not a dependency** — a cost/benefit call, not impossibility. A
  `DocumentSnapshot` cursor is an awkward `PagingSource` key, and Explore pages an Unsplash-backed
  Cloud Function by number rather than by cursor; the hand-rolled pagination above already covers
  what the two feeds need. Revisit if the notes feed needs `PagingSource`/`RemoteMediator`.
- **Firestore security rules** (`firestore.rules`) deny all access by default; per-collection rules
  are additive (OR'd). Keep new collections behind an explicit `request.auth != null` (or stricter).
- **Cloud Functions** code style is enforced by `eslint-config-google` — run the functions lint
  command above before committing changes under `functions/`.

### Kotlin coding conventions

Follows the [official Kotlin style guide](https://kotlinlang.org/docs/coding-conventions.html)
(`kotlin.code.style=official`). Project-specific points worth calling out beyond the official guide:

- Prefer the read-only collection types (`List`, `Map`) over `MutableList`/`MutableMap` across
  public API surfaces — but note that read-only is not immutable. Upcasting a `MutableList` to
  `List` hands the caller a live view the owner can still mutate underneath them, so when a property
  or return value is backed by a mutable field, copy at the boundary (`.toList()`) instead of
  relying on the declared type.
- Prefer a sealed type over a nullable field where both express the same state, matching the nested
  loading/loaded/error content types each ViewModel's `*UiState` wraps (see Architecture above).
- Name booleans and boolean-returning functions as predicates (`isRefreshing`, `hasMoreData`,
  `canRetry`), matching the pagination/state fields already used in ViewModels.

### Formatting

There is no `ktlint`/`spotless`/`detekt` Gradle plugin configured in this project — formatting is
enforced by convention, not a CLI check. After making Kotlin changes, reformat touched files with
Android Studio's formatter (**Code → Reformat Code**, or `Cmd+Opt+L` / `Ctrl+Alt+L`) using the
project's default (official Kotlin style) settings before committing, and avoid introducing
unrelated whitespace/import-order diffs in files you didn't otherwise change.
