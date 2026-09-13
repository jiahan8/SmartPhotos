/*
 * Kotlin Multiplatform module: `DefaultUserPreferencesRepository`, the DataStore-backed
 * implementation of :core:domain's `UserPreferencesRepository`, and the first `Default*` repository
 * outside :core:data.
 *
 * It could move because nothing in it was Android. Preferences DataStore publishes common sources
 * (`datastore-preferences-core`, which :core:data already declared for this class), and the only
 * JVM names in the file were `java.io.IOException` and `javax.inject.Inject`. The first became
 * DataStore's own common `IOException`; the second went, because no Hilt annotation resolves in
 * `commonMain`.
 *
 * What stays in :core:data is what a platform binds. `DataStoreModule` builds the DataStore from an
 * Android `Context`'s files directory, and `DataModule` constructs this class from it in a provider
 * where it used to bind an injected constructor -- the ViewModels' Hilt-at-the-edge shape, minus the
 * subclass, since a provider can call a constructor that carries no annotation.
 *
 * Neither of the two ceilings ARCHITECTURE.md names is raised by this: there is no Firebase here,
 * and Hilt stays where it was. Local persistence is the half of the data layer those two do not
 * bound, and Room is the rest of it.
 */
plugins {
    // Nothing Android, for the reason :core:domain has none: `commonMain` compiles against the
    // intersection of the JVM and Apple targets, so an `android.*` or `java.*` import is rejected
    // rather than merely discouraged. :core:data consumes the `jvm` variant.
    id("smartphotos.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // api: the class implements a :core:domain interface and returns its models, and
            // :core:data's provider compiles against both.
            api(project(":core:domain"))
            // api: `DataStore<Preferences>` is the constructor's one parameter.
            api(libs.datastore.preferences.core)
        }

        commonTest.dependencies {
            // kotlin-test and runTest rather than junit and runBlocking: commonTest compiles for
            // every target above, and neither of those exists on the Apple ones.
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            // An in-memory okio file system under the suite's DataStore. It is hermetic per test with
            // no directory to clean up, and it exists on every target -- the JUnit TemporaryFolder
            // the suite used under Robolectric does not.
            implementation(libs.okio.fakefilesystem)
        }
    }
}