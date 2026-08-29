# SmartPhotos

Android app (Kotlin, single `:app` module) for organizing photos/notes with ML-based tagging.
Firebase backend + a Node.js Cloud Functions project live in `functions/`.

## Build, test, lint

Run from the repo root (Gradle wrapper):

- Build debug APK: `./gradlew assembleDebug`
- Unit tests (JVM, Robolectric-backed): `./gradlew testDebugUnitTest`
  - Single class: `./gradlew testDebugUnitTest --tests "com.jiahan.smartcamera.home.HomeViewModelTest"`
  - Single method: `./gradlew testDebugUnitTest --tests "com.jiahan.smartcamera.home.HomeViewModelTest.methodName"`
  - Assert on a settled `StateFlow` by reading `.value`; that is what most of the suite does, and
    it is the right tool, since a `StateFlow` always has a current value. Reach for
    [Turbine](https://github.com/cashapp/turbine) (`.test { ... }`) when the *sequence* matters —
    intermediate states, ordering — or for a `SharedFlow` event, which has no `.value` to read at
    all. Never hand-roll a collector into a list; that is what Turbine replaces.
  - ViewModel tests replace `Dispatchers.Main` with the `MainDispatcherRule` in
    `app/src/test/java/com/jiahan/smartcamera/MainDispatcherRule.kt`
    (`@get:Rule val mainDispatcherRule = MainDispatcherRule()`), since `viewModelScope` dispatches
    to Main. It defaults to `UnconfinedTestDispatcher` so coroutines run eagerly; pass
    `StandardTestDispatcher` when a test needs virtual-time control, such as a debounce.
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
  Note that the pin is a containment measure, not the fix: `toFormattedDateTime()` reads global
  state, so it can't be tested at a chosen instant or locale. Giving it `zone` and `locale`
  parameters (defaulted to the current lookups) would make it directly testable and drop the
  dependency on a JVM-wide setting — worth doing if that formatter ever needs its own tests.
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
  `toPlatformUri()` from `util/MediaUriExt.kt`, at the ViewModel boundary on the way down or inside
  a `Default*` implementation on the way out. It mirrors how `MediaDetail` already carries remote
  locations as plain `String` URLs.

When adding a feature, prefer extending this layering over reaching across it (e.g. a screen
calling `FirebaseFirestore` directly, or a repository returning a Room `@Entity` to a ViewModel).

### Source of truth

**This is a deliberate deviation from the architecture guide**, and it is the root of the
[Cross-feature communication](#cross-feature-communication) deviation below. The offline-first
guidance makes the local database the source of truth: the UI observes Room, writes land locally
first, and a sync layer reconciles with the backend. This app does the opposite — Firestore is the
source of truth, Home and Search render point-in-time `QuerySnapshot`s, and Room is written only
after the Firestore write returns.

Room is also not a cache of the feed, so don't call it a write-through one. Every `noteDao` write
in `DefaultNoteRepository` is gated on favorite status, so the table only ever holds favorited
notes, and Favorite is the only screen that reads from it. It is a local mirror for one feature,
not a read path for the notes list.

What that costs, listed so nobody rediscovers it as a bug:

- No offline writes. A mutation with no network fails at the Firestore call and never reaches Room.
- No reconciliation. If the Firestore write succeeds and the Room write then fails, the two diverge
  silently until something rewrites that row.
- No live query for Home or Search, which is the entire reason `NoteHandler` exists.

**Trigger to revisit:** mirroring the notes feed into Room — a `Flow`-returning stream the feed
observes, with Firestore writes syncing into it — removes all three costs at once and retires both
this deviation and the handler. That is the direction to move when the feed's offline behavior next
causes a problem. Until then keep new repositories on the same Firestore-first shape rather than
introducing a third pattern.

### Cross-feature communication

Screens that must reflect a mutation made on another screen use a per-domain `*Handler` singleton
injected via Hilt (`note/NoteHandler.kt`), exposing read-only `SharedFlow`s (`noteAddedEvent`,
`noteDeletedEvent`, `noteFavoritedEvent`, `noteUpdatedEvent`) over private `MutableSharedFlow`s.
ViewModels emit into the handler on mutation and subscribe in `init {}` — Home, Search and
NotePreview do. Follow this rather than adding direct cross-ViewModel references.

Subscribe through `NoteHandler.observeNoteMutations(scope) { transform -> ... }`, which collects the
deleted/favorited/updated events and hands back a list transform to apply; Home and Search each use
it as a single line. Don't hand-roll those three collectors. `noteAddedEvent` is the one collected
directly, because it triggers a full `reload()` rather than a transform of the list in hand.

**This is a deliberate deviation from the architecture guide.** The official answer to "screen A
must reflect a change made on screen B" is a single source of truth in the data layer exposing a
reactive stream both screens observe — not peer-to-peer events between ViewModels. It stands here
only because Home's feed and Search's results have no local mirror to observe — and that absence is
itself a choice this project made ([Source of truth](#source-of-truth) above), not a constraint
Firestore imposes. Treat "there is no live query" as a decision that can be reversed, not as a fact
about the backend.

Favorite is the counter-example, and the pattern to copy whenever you can: it never injects
`NoteHandler`, because `getFavoriteNotesStream` is a Room-backed `Flow` that re-emits on its own.

**Trigger to retire the handler:** if the notes feed is ever mirrored into Room (the offline-first
direction), Home and Search should observe that query and the handler should be deleted, not
extended. Until then, don't add a `*Handler` for a list that *could* be backed by a live query —
back it with the query instead.

Two rules while it exists:

- Emit the mutation once and let the collector apply it. Don't also patch the list locally at the
  call site — `HomeViewModel.deleteNote` currently does both, which is harmless only because
  filtering happens to be idempotent.
- A default `MutableSharedFlow` has no replay, so a subscriber that isn't collecting yet (its
  ViewModel not yet constructed, or still before its `init {}`) silently misses the event. That is
  tolerable for list patches, where every subscriber re-fetches on construction anyway. For an
  event a screen must never miss, use `note/IncomingShareHandler.kt` instead: a `StateFlow` holding
  the pending value plus an explicit `consume()`, which survives having no subscriber yet.

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

The feature-specific mappers in the same file (`usernameErrorMessageResId`,
`noteErrorMessageResId`) sit at that same ViewModel layer, tried ahead of `getErrorMessage` and
falling back to it when they return null.

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

- Keep `domain/` models and repository *interfaces* free of Android/Firebase/Room types — already
  required by the Separation of concerns rules above. Treat this as a prerequisite, not as the
  migration itself: KMP moves Gradle *modules*, not Kotlin packages, so a perfectly pure `domain/`
  package inside the single `:app` module still compiles against the Android plugin's classpath and
  is no closer to a `commonMain` source set than an impure one. The actual first step is extracting
  `:core:domain` / `:core:data` modules; type purity is what keeps that extraction from becoming a
  rewrite. Don't read a clean `domain/` package as "we are nearly KMP-ready".
- Prefer `kotlinx` libraries (`kotlinx.coroutines`, `kotlinx.datetime`, `kotlinx.serialization`)
  over equivalents that have no `commonMain` implementation (e.g. `java.time`, Gson) in
  shared-leaning code, when a choice exists. `java.time` is not unavailable on Android — core
  library desugaring provides it — the objection is only that it cannot cross into a shared source
  set.
- Keep `android.*` out of the *contracts*: domain models, repository interfaces, and the data
  classes those interfaces carry (`NoteMediaDetail`, say). `Default*` implementations are
  Android-bound by definition and are not what this rule targets — `DefaultErrorHandler` uses `Log`,
  `ResourceProviderImpl` needs a `Context`. One interface is deliberately exempt:
  `MediaFileRepository` exists precisely to wrap `ContentResolver`/`FileProvider` work behind a
  seam, so it keeps its `Uri`/`Bitmap` parameters and is not a KMP candidate. Don't wrap those
  parameters to satisfy the rule, and don't cite it as precedent for a new contract.

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
Favorite and the preview screens, all via `note/NoteErrorReporter.kt`) and `ProfileViewModel.events`
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

`navigation/Screen.kt` uses Navigation Compose's type-safe routes: each destination is a
`@Serializable` `data object`/`data class` under the `Screen` sealed interface, registered with
`composable<Screen.Home> { ... }` and reached via `navController.navigate(Screen.NotePreview(id))`.
Add destinations that way — no hand-built path strings, no `navArgument` lists, no manual URL
escaping of arguments.

- Route types stay plain data. UI-only metadata (bottom-bar icon and title) lives in
  `navigation/BottomNavItem.kt`, because only some destinations appear in the bottom bar.
- An enum used as a route argument needs `@Keep` (see `MediaSourceType`). Navigation resolves enum
  arguments through `Class.forName()`, so R8 renaming one breaks navigation in release builds only —
  a failure that shows up in neither debug runs nor unit tests.

## Conventions

- DI: Hilt, all *modules* `@InstallIn(SingletonComponent::class)`. Constructor-injected classes may
  still be narrower — `note/NoteErrorReporter.kt` is `@ViewModelScoped` so the delegates sharing it
  report onto one flow — so "all modules are singleton" is not "everything is a singleton". App-wide bindings live in
  `di/AppModule.kt` / `di/FirebaseModule.kt`; other cross-cutting layers have their own module
  (`data/di/DataModule.kt`, `util/di/UtilModule.kt`, `database/di/DatabaseModule.kt`) — there is no
  per-feature `di/` package yet (e.g. `note/`, `search/` have none); follow this layer-scoped
  pattern rather than introducing one.
- Dispatchers and scopes: don't reference `Dispatchers.IO` directly in new code. Inject
  `@param:IoDispatcher private val ioDispatcher: CoroutineDispatcher` — the qualifier and its
  provider both live in `di/AppModule.kt` — so tests can substitute a `TestDispatcher`, and keep the
  `@param:` use-site target the existing repositories use. `@ApplicationScope` provides the
  app-lifetime `CoroutineScope` for work that must outlive a ViewModel. The single deliberate
  exception is `data/datastore/DataStoreModule.kt`, where DataStore's own scope is built at module
  level.
- Dependencies: every version lives in `gradle/libs.versions.toml` and is referenced through the
  generated `libs.*` accessors — `app/build.gradle.kts` holds no hardcoded version strings. Add a
  library as a `[versions]` entry plus a `[libraries]` entry, never as an inline
  `implementation("group:artifact:1.2.3")`.
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
