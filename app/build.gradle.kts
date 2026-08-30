import com.google.firebase.appdistribution.gradle.firebaseAppDistribution

plugins {
    // Convention plugins from `build-logic`. Between them they apply AGP, the Kotlin Android
    // plugin and the Compose compiler, and set compileSdk/minSdk, the Java 11 pair, the Kotlin
    // JVM target and the unit-test JVM pin -- everything this file used to state and :core:data
    // and :core:ui state identically.
    id("smartphotos.android.application")
    id("smartphotos.android.compose")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
    alias(libs.plugins.crashlytics)
    alias(libs.plugins.firebase.perf)
    alias(libs.plugins.firebase.appdistribution)
    alias(libs.plugins.roborazzi)
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
        // Robolectric-backed Compose screenshot tests need access to Android resources on the JVM.
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

    sourceSets {
        // Tests placed in sharedTest run on both the JVM (Robolectric) and on-device, so Compose
        // behavior tests and the fakes are written once and executed in both environments.
        getByName("test").java.srcDir("src/sharedTest/java")
        getByName("androidTest").java.srcDir("src/sharedTest/java")
    }
}

roborazzi {
    // Store reference screenshots in a VCS-tracked directory (default is the transient build/ dir),
    // so they are committed and used as the baseline by verifyRoborazziDebug.
    outputDir.set(layout.projectDirectory.dir("src/test/screenshots"))
}

dependencies {

    // Domain models, repository contracts, safeCall and the DI qualifiers. A pure-JVM
    // module with no Android plugin, so it is the compiler's copy of the purity rule.
    implementation(project(":core:domain"))

    // Every Default* repository, the Room database and the DataStore wiring. Room and DataStore
    // left :app with them; the Firebase and Play Core artifacts below stay because :app still
    // compiles against those itself. Several arrive from :core:data as `api` too (Hilt needs
    // them resolvable here) -- declaring them anyway states what :app's own code uses.
    implementation(project(":core:data"))

    // The first feature module. :app supplies its navigation lambdas and hosts its route in
    // the graph; the Kotlin package is unchanged, so SmartPhotosNavGraph's imports did not move.
    implementation(project(":feature:explore"))

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
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    // The fakes, MainDispatcherRule and BaseScreenshotTest, shared with :core:ui and
    // :feature:explore. androidTest too: sharedTest/ runs in both source sets.
    testImplementation(project(":core:testing"))
    androidTestImplementation(project(":core:testing"))

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    // Roborazzi + Robolectric: JVM Compose screenshot tests (no emulator required).
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.androidx.ui.test.manifest)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.android.compiler)
    androidTestUtil(libs.androidx.test.orchestrator)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Coil for image loading
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.network.okhttp)

    // ML Kit Text Recognition
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.text.recognition.japanese)
    implementation(libs.mlkit.text.recognition.chinese)
    implementation(libs.mlkit.image.labeling)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Hilt
    implementation(libs.androidx.hilt.navigation.compose)
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

    // GenAI
    implementation(libs.genai.image.description)
    implementation(libs.kotlinx.coroutines.guava)

    // ExoPlayer
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.ui)
    implementation(libs.media3.ui.compose)

    // Splash Screen
    implementation(libs.core.splashscreen)

    // Explore screen icon (Icons.Outlined.Explore isn't in material-icons-core)
    implementation(libs.androidx.material.icons.extended)
}