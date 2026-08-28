import com.google.firebase.appdistribution.gradle.firebaseAppDistribution
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
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
    compileSdk = 37

    defaultConfig {
        applicationId = "com.jiahan.smartcamera"
        minSdk = 28
        targetSdk = 36
        versionCode = 6
        versionName = "3.1.0"

        // Custom runner installs HiltTestApplication so instrumented tests can inject the Hilt graph.
        testInstrumentationRunner = "com.jiahan.smartcamera.HiltTestRunner"
        // Wipe app data (DataStore/Room/prefs) between tests for full isolation. Requires orchestrator.
        testInstrumentationRunnerArguments["clearPackageData"] = "true"

        javaCompileOptions {
            annotationProcessorOptions {
                arguments += "room.incremental" to "true"
            }
        }
    }

    buildFeatures {
        compose = true
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
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

// Pin the unit-test JVM's timezone and locale. `Long.toFormattedDateTime()` resolves
// ZoneId.systemDefault() and Locale.getDefault() at render time, so a note's timestamp renders
// differently depending on the machine running the tests. That made the Roborazzi goldens
// machine-dependent: every screenshot containing a note row passed on a UTC+8 laptop and failed
// on the UTC CI runner, 8 hours out. UTC/en-US is chosen to match the CI runner, so goldens
// recorded anywhere verify everywhere. Re-record goldens if you change these.
tasks.withType<Test>().configureEach {
    systemProperty("user.timezone", "UTC")
    systemProperty("user.language", "en")
    systemProperty("user.country", "US")

    // Report a failure as its assertion message and nothing else. Both settings are needed and
    // do opposite jobs: the default SHORT format collapses the exception to
    // "AssertionError at Foo.kt:59", hiding the one line that makes a Roborazzi failure
    // actionable (which golden changed, and where its comparison image was written), while FULL
    // on its own appends ~20 lines of Roborazzi-internal frames per failure that say nothing
    // about this codebase. FULL restores the message; showStackTraces = false drops the frames.
    testLogging {
        events("failed")
        exceptionFormat = TestExceptionFormat.FULL
        showStackTraces = false
    }
}

ksp {
    // Export Room schema JSON per version to app/schemas, used as the source of truth
    // for writing and testing future Room migrations.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
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

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.config)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.inappmessaging.display)
    implementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.firebase.appcheck.debug)
    implementation(libs.firebase.perf)

    // Play Core (in-app updates)
    implementation(libs.play.app.update)
    implementation(libs.play.app.update.ktx)

    // Room
    ksp(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.room.ktx)

    // GenAI
    implementation(libs.genai.image.description)
    implementation(libs.kotlinx.coroutines.guava)

    // DataStore
    implementation(libs.datastore.preferences)
    implementation(libs.datastore.preferences.core)

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