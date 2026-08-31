import com.google.firebase.appdistribution.gradle.firebaseAppDistribution

plugins {
    // Convention plugins from `build-logic`. Between them they apply AGP, the Kotlin Android
    // plugin and the Compose compiler, and set compileSdk/minSdk, the Java 11 pair, the Kotlin
    // JVM target and the unit-test JVM pin -- everything this file used to state and :core:data
    // and :core:ui state identically.
    id("smartphotos.android.application")
    id("smartphotos.android.compose")
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
    alias(libs.plugins.crashlytics)
    alias(libs.plugins.firebase.perf)
    alias(libs.plugins.firebase.appdistribution)
    // No roborazzi here any more. ScreenScreenshotTest captured HomeScreen and SearchScreen, and
    // both screens left for :feature:home and :feature:search -- so the goldens followed and the
    // plugin has nothing left to record. :app renders no composable of its own worth capturing:
    // what is left up here is the NavHost, the bottom bar and the Scaffold around them.
}

android {
    namespace = "com.jiahan.smartcamera"

    defaultConfig {
        applicationId = "com.jiahan.smartcamera"
        // targetSdk, unlike compileSdk/minSdk, is an application-only setting -- a library has no
        // targetSdk -- so it stays here rather than moving into the convention plugin.
        targetSdk = 36
        versionCode = 6
        versionName = "3.1.0"

        // Custom runner installs HiltTestApplication so instrumented tests can inject the Hilt graph.
        testInstrumentationRunner = "com.jiahan.smartcamera.HiltTestRunner"
        // Wipe app data (DataStore/Room/prefs) between tests for full isolation. Requires orchestrator.
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
    }

    buildFeatures {
        // compose is turned on by smartphotos.android.compose. buildConfig is :app's alone: it
        // generates com.jiahan.smartcamera.BuildConfig, which MyApp, DefaultErrorHandler and
        // AppModule read. No module below :app enables it -- see the Build type rule in AGENTS.md.
        buildConfig = true
    }

    testOptions {
        // Each test runs in its own instrumentation process, so a crash or leaked state in one
        // test cannot affect another. Combined with clearPackageData above for hermetic runs.
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
        // DefaultErrorHandlerTest resolves real strings under Robolectric. This used to be here
        // for the Compose screenshot tests as well; they have gone to the feature modules, but the
        // remaining reason is enough on its own.
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("boolean", "DEBUG_MODE", "false")
        }
        debug {
            isMinifyEnabled = false
            buildConfigField("boolean", "DEBUG_MODE", "true")

            // Manual distribution to testers: `firebase login` once, then
            // `./gradlew assembleDebug appDistributionUploadDebug`.
            // The "testers" group must exist under Firebase console > App
            // Distribution > Testers & Groups (or override `groups`/`testers`
            // here) before the first upload.
            firebaseAppDistribution {
                groups = "testers"
                releaseNotes = "Manual test build."
            }
        }
    }

    androidResources {
        generateLocaleConfig = true
    }

    // No `sharedTest` source set here any more either. It held HomeScreenTest, which went to
    // :feature:home and took the arrangement with it -- see the source-sets note there, and
    // :feature:auth's. The two srcDir lines outlived the directory's last file by one commit,
    // which is the failure mode worth naming: a source set that points at nothing is invisible.
}

dependencies {

    // Domain models, repository contracts, safeCall and the DI qualifiers. A pure-JVM
    // module with no Android plugin, so it is the compiler's copy of the purity rule.
    implementation(project(":core:domain"))

    // The Android-bound half of the shared vocabulary: validateUsername/validateDisplayName,
    // which profile still calls here and :feature:auth calls there, and the ten username/name/email
    // strings the two screens and appErrorMessageResId all resolve. ProfileScreen, ProfileViewModel
    // and ErrorMessageMappers reach its R as `CommonR`.
    implementation(project(":core:common"))

    // Every Default* repository, the Room database and the DataStore wiring. Room and DataStore
    // left :app with them; the Firebase and Play Core artifacts below stay because :app still
    // compiles against those itself. Several arrive from :core:data as `api` too (Hilt needs
    // them resolvable here) -- declaring them anyway states what :app's own code uses.
    implementation(project(":core:data"))

    // The first feature module. :app supplies its navigation lambdas and hosts its route in
    // the graph; the Kotlin package is unchanged, so SmartPhotosNavGraph's imports did not move.
    implementation(project(":feature:explore"))

    // The second. Same shape as explore -- :app hosts the route and supplies the lambdas -- plus
    // one value the module cannot read for itself: SettingsScreen takes `versionName`, because a
    // library has no application BuildConfig.
    implementation(project(":feature:settings"))

    // The third. The start destination, so MainViewModel, SmartPhotosApp and NavTransitions all
    // name AuthRoute -- all of them here in :app, pointing down. AuthScreen takes `logoRes`,
    // because mipmap/ic_launcher is this module's resource and a library cannot reach it.
    implementation(project(":feature:auth"))

    // The fourth. It was the one blocked on :core:data, and the block dissolved when
    // MediaFileRepository and MediaUriExt came down to :core:common. TopLevelDestination reads its
    // R for the bottom-bar label, as `ProfileR`.
    implementation(project(":feature:profile"))

    // The fifth, and the first of the five packages note/'s delegates used to hold here. Nothing
    // was left to decouple by the time it moved -- the work was three strings.
    implementation(project(":feature:favorite"))

    // The sixth. SmartPhotosNavGraph reads SEARCH_DEEP_LINK_URI_PATTERN off its route -- a downward
    // read, and why the pattern lives on the destination rather than in navigation/.
    implementation(project(":feature:search"))

    // The seventh. HomeScreen takes `title`, because app_name is the application's manifest label
    // and a library cannot own it -- the same hoist as settings' versionName and auth's logoRes.
    implementation(project(":feature:home"))

    // The eighth, and the largest. SmartPhotosNavGraph constructs MediaSourceType.REMOTE off its
    // routes -- a downward read, like the search deep link above.
    implementation(project(":feature:preview"))

    // The ninth and last. AppModule provides IncomingShareHandler from here and MainViewModel
    // consumes it -- a downward read, since :app is what receives the share intent.
    implementation(project(":feature:note"))

    // The shared Compose vocabulary -- common/, ui/theme and the two util helpers that follow
    // them. Every feature screen in this module draws with it, and nine of them also resolve
    // strings from its R, imported there as `UiR`.
    implementation(project(":core:ui"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    /*
     * What :app's own tests still need, which is much less than it was. Three suites left with
     * their subjects -- the two repository tests and FirebaseRemoteConfigRepositoryTest to
     * :core:data, ScreenScreenshotTest split between :feature:home and :feature:search -- and what
     * remains is MainViewModelTest, AppModuleTest, DefaultErrorHandlerTest and
     * ErrorMessageMappersTest. None of them renders a composable, so the Compose test artifacts and
     * all three Roborazzi artifacts went with the suites that did.
     *
     * :core:testing stays, for one type: MainViewModelTest's `MainDispatcherRule`. It uses none of
     * the nine fakes, which is the trap in reading this edge from the imports -- the rule is in
     * package `com.jiahan.smartcamera`, the same package as the test, so it is used without an
     * import line to find it.
     *
     * Robolectric stays too: DefaultErrorHandlerTest resolves real strings, and androidx-junit is
     * what supplies the AndroidJUnit4 runner both it and MainViewModelTest name.
     */
    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)

    /*
     * androidTest is HiltGraphSmokeTest and ExampleInstrumentedTest -- member injection over the
     * generated component, and no Compose. The orchestrator and HiltTestRunner are what
     * defaultConfig above names; hilt-android-testing and the ksp compiler are what generate the
     * test component for them.
     */
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.android.compiler)
    androidTestUtil(libs.androidx.test.orchestrator)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.datetime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    /*
     * Coil, and only the part :app uses. MyApp is the `SingletonImageLoader.Factory` and the
     * messaging service resolves a notification icon through it; neither draws a composable, so
     * this is coil-core rather than the coil-compose every module that renders an image declares.
     * The okhttp fetcher has no source reference and is required anyway: the singleton this module
     * builds is the one every feature's AsyncImage resolves through, and without a network
     * component on the classpath every remote load fails at runtime.
     */
    implementation(libs.coil.core)
    implementation(libs.coil.network.okhttp)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Hilt. hilt-navigation-compose is deliberately absent: `hiltViewModel()` comes from
    // hilt-lifecycle-viewmodel-compose, which is what every feature screen actually imports.
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    // Firebase: what :app's own code compiles against -- FirebaseModule's providers, MyApp's
    // App Check setup, the messaging service, DefaultErrorHandler and ErrorMessageMappers.
    // firebase-storage is deliberately absent: nothing in :app has referenced it since
    // DefaultNoteRepository/DefaultUserRepository moved to :core:data.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.config)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.inappmessaging.display)
    implementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.firebase.appcheck.debug)
    // No source reference: the firebase-perf Gradle plugin above instruments the build and needs
    // the SDK present, and the SDK auto-initialises. Don't remove it as an unused dependency.
    implementation(libs.firebase.perf)

    // Play Core: AppModule builds the AppUpdateManager. The ktx wrapper the update flow
    // itself uses is in :core:data, with DefaultAppUpdateRepository.
    implementation(libs.play.app.update)

    // Splash Screen
    implementation(libs.core.splashscreen)

    /*
     * What used to be here, and why none of it is:
     *
     * - ExoPlayer (media3-exoplayer/-ui). Playback is :feature:preview's, and that module declares
     *   the two artifacts it names. media3-exoplayer-dash and media3-ui-compose were declared only
     *   here and imported nowhere: every video is a `MediaItem.fromUri` of a progressive Firebase
     *   Storage URL, so the DASH source is never reflectively loaded, and PlayerView is the
     *   views-based one.
     * - ML Kit text recognition x3 and image labeling, and genai-image-description with its
     *   kotlinx-coroutines-guava bridge. No module imports `com.google.mlkit` at all -- the ML in
     *   this app is the Cloud Vision call in functions/index.js.
     * - material-icons-extended. It was here for `Icons.Outlined.Explore`, and the Explore
     *   destination left the bottom bar; the five TopLevelDestination icons are all in
     *   material-icons-core. The feature convention still adds the extended pack, for the screens
     *   that reach past that set.
     * - kotlinx-serialization-json and the serialization plugin. Every `@Serializable` route is
     *   declared in the feature module that owns it, which applies the plugin itself, and nothing
     *   here touches `Json`. Compare :core:data, which keeps the plugin with no `@Serializable` of
     *   its own for a reason that does not apply here -- see the note in its build file.
     *
     * All of it arrived with code that has since moved into a module of its own. **A dependency
     * does not fail a build by being unused, so it outlives the code that wanted it unless
     * something goes looking** -- which is the argument for pruning at the end of a split rather
     * than trusting each extraction to have taken its own libraries with it.
     */
}