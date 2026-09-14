/*
 * Android library: every implementation that satisfies a contract in :core:domain but one --
 * `DefaultUserPreferencesRepository`, which needed nothing Android and is in :core:datastore's
 * commonMain. It is still bound here, by `DataModule`, from the DataStore `DataStoreModule` builds.
 *
 * This is the Firebase/Play-Core half of the data layer, plus the Context-bound wiring that opens
 * the Room database (declared in :core:database) and the DataStore -- the part that is Android-bound
 * by definition. The split with :core:domain is the dependency inversion the
 * Separation of concerns section of AGENTS.md describes, now expressed as a module boundary:
 * the interfaces sit above in a module with no Android plugin and the `Default*` classes sit here.
 *
 * Note what the boundary does and does not buy. It stops this module reaching UP -- a repository
 * cannot touch a ViewModel, an R string or :app's BuildConfig, because nothing depends on :app.
 * It does NOT stop :app referencing `DefaultNoteRepository` directly: these classes are public in
 * a module :app depends on. Making them `internal` would enforce that too; it is not done here
 * because Hilt still has to instantiate them from the component it generates in :app.
 *
 * Unlike :core:domain this is NOT a step toward Kotlin Multiplatform. Firebase and Play Core have
 * no common source set, so nothing here becomes shareable by having moved. See the KMP readiness
 * section of AGENTS.md for what would be.
 */
plugins {
    // Applies AGP's library plugin and the Kotlin Android plugin, and sets compileSdk/minSdk,
    // the Java 11 pair and the Kotlin JVM target. It deliberately does not set `namespace` --
    // every library needs its own, so the convention leaves it to be declared below.
    id("smartphotos.android.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    // Distinct from :app's `com.jiahan.smartcamera` so the two R classes and BuildConfigs cannot
    // collide. Kotlin packages are unchanged -- files here still live in com.jiahan.smartcamera.data
    // and .database, which is what kept the extraction a pure `git mv` with no import churn.
    namespace = "com.jiahan.smartcamera.core.data"

    defaultConfig {
        // The Room DAO and migration tests here use neither Hilt nor Compose, so the plain
        // AndroidX runner is enough -- :app's HiltTestRunner stays in :app with the tests that
        // need a Hilt component. Orchestrator + clearPackageData for the same reason as :app:
        // Room writes to disk, so tests must not inherit each other's state.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
    }

    sourceSets {
        // The :feature:auth arrangement, applied to the suites that need a real SQLite and a real
        // file: compiled into both source sets, run on the JVM under Robolectric for CI and
        // on-device under the instrumentation runner.
        getByName("test").java.srcDir("src/sharedTest/kotlin")
        getByName("androidTest").java.srcDir("src/sharedTest/kotlin")

        // MigrationTestHelper reads the exported schema JSON at runtime, so `schemas/` has to ship
        // as a test asset. Both source sets, because the migration suite is in `sharedTest`. The
        // directory is :core:database's, which exports it; the migration suite stays here because
        // what it pins is the upgrade Android's own SQLite performs -- see its class doc.
        getByName("test").assets.srcDir("$rootDir/core/database/schemas")
        getByName("androidTest").assets.srcDir("$rootDir/core/database/schemas")
    }

    testOptions {
        execution = "ANDROIDX_TEST_ORCHESTRATOR"

        // MigrationTestHelper resolves the exported schema JSON through the instrumentation
        // context's assets. On the JVM half of `sharedTest` that context is Robolectric's, and it
        // sees no assets at all unless they are merged in -- the migration suite fails with
        // "Cannot find the schema file in the assets folder" without this.
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {

    // api, not implementation: the repository interfaces these classes implement, and the domain
    // models they return, are in :core:domain and appear throughout this module's public
    // signatures, so :app compiles against them through this dependency as well as its own.
    api(project(":core:domain"))

    // Same reason, one module along: `MediaFileRepository` is the interface
    // DefaultMediaFileRepository implements and a constructor parameter of DefaultNoteRepository,
    // and `toPlatformUri()` is called in three files here. Both came down to :core:common when
    // :feature:profile was extracted, because a feature module must not depend on this one.
    api(project(":core:common"))

    // implementation, not api: DataModule constructs DefaultUserPreferencesRepository in a provider
    // that returns the :core:domain interface and takes a DataStore<Preferences>, so no type from
    // that module reaches a signature :app's annotation processor has to resolve.
    implementation(project(":core:datastore"))

    // api: NoteDao is an Inject-constructor parameter of DefaultLocalUserDataCleaner and a parameter
    // of DataModule's NoteRepository provider, and AppDatabase the return type of a DatabaseModule
    // provider, so :app's annotation processor resolves both -- the rule in the block below, for a
    // project edge.
    api(project(":core:database"))

    // implementation: DataModule constructs :core:firebase's repositories in providers whose
    // signatures name only :core:domain interfaces, NoteDao and GitLive's types (all declared api).
    implementation(project(":core:firebase"))

    implementation(libs.androidx.core.ktx)
    // ActivityResultLauncher / IntentSenderRequest, for the in-app update flow.
    implementation(libs.androidx.activity)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    implementation(libs.kotlinx.coroutines.android)
    // `kotlinx.coroutines.tasks.await`, called on every Firebase Task in this module.
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.datetime)

    /*
     * api, not implementation, for everything below whose type appears in an `@Inject constructor`
     * parameter of a class in this module.
     *
     * Hilt does not build a component per module: every @InstallIn(SingletonComponent::class)
     * binding is aggregated and the component is generated in :app, which means :app's annotation
     * processor has to *resolve* the constructor parameters of every Default* class it instantiates
     * -- DataStore<Preferences>, FirebaseFirestore, AppUpdateManager and the rest. Declaring them
     * `implementation` hides them from :app's compile classpath and the build fails with
     * "InjectProcessingStep was unable to process 'x' because 'Y' could not be resolved".
     *
     * So under Hilt a library's @Inject constructor parameters are effectively part of its API,
     * and `api` states that rather than leaving it to whatever :app happens to declare for its own
     * reasons. (It did declare most of these, which is why only DataStore and Room -- the two :app
     * genuinely stopped using -- broke, and only in androidTest, where the smoke test's member
     * injection walks the graph furthest.) This is the same Hilt-shaped constraint the KMP
     * readiness section of AGENTS.md calls the ceiling on sharing this layer.
     */
    api(platform(libs.firebase.bom))
    // GitLive's Firebase types are providers' return types and parameters. The Android SDKs they wrap
    // are gone from this list: no source here names Auth, Firestore, Functions, Messaging, Remote
    // Config or Analytics since their repositories moved to :core:firebase, which brings them in.
    api(libs.gitlive.firebase.functions)
    api(libs.gitlive.firebase.config)
    api(libs.gitlive.firebase.analytics)
    api(libs.gitlive.firebase.auth)
    api(libs.gitlive.firebase.firestore)
    api(libs.gitlive.firebase.messaging)
    api(libs.play.app.update)
    api(libs.datastore.preferences)
    api(libs.datastore.preferences.core)

    // implementation, deliberately: no constructor takes these. DefaultMediaUploadRepository builds
    // its FirebaseStorage from a Remote Config URL, and the Play Core ktx wrappers are used only
    // inside DefaultAppUpdateRepository's own function bodies.
    implementation(libs.firebase.storage)
    implementation(libs.play.app.update.ktx)
    // `Room.databaseBuilder`, in DatabaseModule's body only -- the database it builds, and the
    // Room compiler that generates it, are :core:database's.
    implementation(libs.room.runtime)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)

    /*
     * The repository suites here run under Robolectric, for a reason of this module's own: every
     * Firebase call here is stubbed with `Tasks.forResult`/`forException`, which needs a real
     * Android runtime rather than the JVM stub jar.
     *
     * Declared directly rather than taken from :core:testing, which is where the rest of the build
     * gets Robolectric. That used to be forced: :core:testing carried an `api` edge on this module,
     * so the reverse direction was a cycle. The edge turned out to be unused -- no fake names a
     * type from here -- and removing it leaves this module free to take :core:testing on
     * `testImplementation` whenever a suite here wants the fakes. It has not been done yet only
     * because these four suites stub Firebase directly rather than through a fake; do it when one
     * of them would rather have a FakeNoteRepository than a mockk.
     *
     * The rule the removal restored: a fixtures module is a supplier to this layer or a consumer
     * of it, never both.
     */
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestUtil(libs.androidx.test.orchestrator)

    // MigrationTestHelper, for the v1 -> v2 auto-migration. Nothing else opens an *existing*
    // database file: every other suite here builds one fresh with `inMemoryDatabaseBuilder`, which
    // never migrates, so an upgrade crash would reach users with the whole suite green.
    testImplementation(libs.room.testing)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}