/*
 * Android library: every implementation that satisfies a contract in :core:domain.
 *
 * This is the Firebase/Room/DataStore/Play-Core half of the data layer -- the part that is
 * Android-bound by definition. The split with :core:domain is the dependency inversion the
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
    // No @Serializable is declared in this module -- the annotated models are in :core:domain --
    // and removing this plugin still compiles and still passes DatabaseConvertersTest (verified).
    // It stays because DatabaseConverters calls Json.encodeToString/decodeFromString at reified
    // call sites here: with the plugin those resolve to the generated serializer at compile time,
    // without it they fall back to runtime reflection over Kotlin metadata -- the kind of thing
    // release R8 (minify + shrink + strictFullModeForKeepRules) can break while every unit test
    // stays green. Don't drop it as an unused plugin.
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    // Distinct from :app's `com.jiahan.smartcamera` so the two R classes and BuildConfigs cannot
    // collide. Kotlin packages are unchanged -- files here still live in com.jiahan.smartcamera.data
    // and .database, which is what kept the extraction a pure `git mv` with no import churn.
    namespace = "com.jiahan.smartcamera.core.data"

    defaultConfig {
        // The Room DAO and DataStore tests here use neither Hilt nor Compose, so the plain
        // AndroidX runner is enough -- :app's HiltTestRunner stays in :app with the tests that
        // need a Hilt component. Orchestrator + clearPackageData for the same reason as :app:
        // Room and DataStore both write to disk, so tests must not inherit each other's state.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
    }

    testOptions {
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }
}

ksp {
    // Room schema JSON per version, moved here with the database it describes. Used as the source
    // of truth for writing and testing future migrations.
    //
    // This block, not defaultConfig.javaCompileOptions.annotationProcessorOptions -- those feed
    // javac/kapt, and there is no kapt in this project. :app carried a vestigial
    // `arguments += "room.incremental" to "true"` there until the extraction; it had reached
    // nothing since Room moved to KSP (and `room.incremental` has been Room's default since 2.3.0
    // regardless). Verified by passing room.schemaLocation that way instead: Room answers
    // "Schema export directory was not provided". Note it answers with a *warning*, so getting
    // this wrong stops schema export without failing the build -- if you change it, confirm
    // schemas/ still regenerates after deleting it.
    arg("room.schemaLocation", "$projectDir/schemas")
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

    implementation(libs.androidx.core.ktx)
    // ActivityResultLauncher / IntentSenderRequest, for the in-app update flow.
    implementation(libs.androidx.activity)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    implementation(libs.kotlinx.coroutines.android)
    // `kotlinx.coroutines.tasks.await`, called on every Firebase Task in this module.
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)
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
    api(libs.firebase.analytics)
    api(libs.firebase.auth)
    api(libs.firebase.config)
    api(libs.firebase.firestore)
    api(libs.firebase.functions)
    api(libs.firebase.messaging)
    api(libs.play.app.update)
    api(libs.room.ktx)
    api(libs.datastore.preferences)
    api(libs.datastore.preferences.core)

    // implementation, deliberately: no constructor takes these. DefaultNoteRepository builds its
    // FirebaseStorage itself from a Remote Config URL, and the Play Core ktx wrappers are used
    // only inside DefaultAppUpdateRepository's own function bodies.
    implementation(libs.firebase.storage)
    implementation(libs.play.app.update.ktx)

    ksp(libs.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestUtil(libs.androidx.test.orchestrator)
}