import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink

/*
 * Kotlin Multiplatform module: the Firebase-backed repository implementations, on GitLive's
 * multiplatform Firebase SDK (`dev.gitlive:firebase-*`) instead of the Android one.
 * `DefaultPhotoRepository` came first; the rest of :core:data's Firebase `Default*`s follow one at a
 * time. Why GitLive, and what the spike that chose it measured, is in ARCHITECTURE.md.
 *
 * An Android target, like :core:database, and for a similar reason: GitLive's `invoke` and `data`
 * are inline, so their bodies are compiled into this module per target, and Android has to get the
 * build that inlined GitLive's Android implementation rather than its JVM one.
 *
 * What stays in :core:data is the Hilt wiring: `FirebaseModule` provides GitLive's instances, and
 * `DataModule` constructs these classes, which carry no annotations.
 */
plugins {
    id("smartphotos.kmp.android.library")
    // The payload DTOs are @Serializable: GitLive returns a callable's result only through
    // kotlinx.serialization.
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.jiahan.smartcamera.core.firebase"
        // JVM 17, the one module in the build that is not on 11. GitLive 2.7.0 compiles its inline
        // functions to JVM 17 bytecode, and Kotlin refuses to inline that into a JVM 11
        // compilation ("Cannot inline bytecode built with JVM target 17 into bytecode that is being
        // built with JVM target 11"). It stays contained: consumers call this module's own,
        // non-inline API, and :core:data and :app compile, shrink and pass their device suites at 11
        // against it. Raise this only in a module that calls GitLive itself.
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        commonMain.dependencies {
            // api: DefaultPhotoRepository implements a :core:domain interface and returns its models.
            api(project(":core:domain"))
            // api: DefaultNoteRepository's public constructor takes the NoteDao it writes the Room
            // mirror through. The DAO only: opening the database needs a Context, and stays in
            // :core:data's DatabaseModule.
            api(project(":core:database"))
            // api: the public constructors take GitLive's FirebaseFunctions, FirebaseRemoteConfig,
            // FirebaseAnalytics, FirebaseAuth, FirebaseFirestore and FirebaseMessaging.
            api(libs.gitlive.firebase.functions)
            api(libs.gitlive.firebase.config)
            api(libs.gitlive.firebase.analytics)
            api(libs.gitlive.firebase.auth)
            api(libs.gitlive.firebase.firestore)
            api(libs.gitlive.firebase.messaging)
            // implementation: DefaultUserRepository builds its Storage instance itself, from the
            // bucket Remote Config names, so no public signature carries the type.
            implementation(libs.gitlive.firebase.storage)
        }

        androidMain.dependencies {
            // Remote Config's real-time update listener, which GitLive 2.7.0 does not wrap in common
            // code. `configUpdates`' Android actual reaches the SDK instance through GitLive's own
            // `android` accessor, so it names the SDK's listener types itself.
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.config)
            // Topic subscriptions that finish before they return, which GitLive 2.7.0's common calls
            // do not: the Android actuals await the SDK's own Task through GitLive's `android`
            // accessor.
            implementation(libs.firebase.messaging)
            implementation(libs.kotlinx.coroutines.play.services)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            // The multiplatform fakes for :core:domain's contracts -- UserRepository and
            // LocalUserDataCleaner for DefaultAuthRepositoryTest, AuthRepository and ErrorHandler for
            // DefaultNoteRepositoryTest, RemoteConfigRepository for DefaultUserRepositoryTest.
            implementation(project(":core:domain-testing"))
            // `decode`, the function HttpsCallableResult.data and DocumentSnapshot.data run on the raw
            // value, and `encode`, which a callable's arguments go through, so the suites exercise
            // GitLive's real serialization. Not exposed to consumers by the GitLive SDKs.
            implementation(libs.gitlive.firebase.common.internal)
        }
    }
}

// Matches the JVM 17 above for the `jvm` target's published variant.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// iOS tests compile but do not link or run. On iOS, GitLive expects the app to link the native
// Firebase SDK itself (SwiftPM or CocoaPods), and without it the test binary fails at link time with
// "ld: framework 'FirebaseCore' not found". Linking it is iOS-client work nobody has started; until
// then this keeps `./gradlew iosSimulatorArm64Test` green on a Mac, while `compileTestKotlinIos*`
// still proves the suite compiles for those targets. Delete both blocks when the SDK is linked.
tasks.withType<KotlinNativeLink>().configureEach {
    if (name.startsWith("linkDebugTest") || name.startsWith("linkReleaseTest")) enabled = false
}
tasks.withType<KotlinNativeTest>().configureEach {
    enabled = false
}