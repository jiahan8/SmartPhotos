/*
 * Test fixtures shared across modules: the nine repository/handler fakes, MainDispatcherRule and
 * the Roborazzi screenshot harness.
 *
 * This is the module the plan deliberately kept until last, on the rule that every `:core:` module
 * needs a forcing function -- something that will not compile without it. It now has three, all
 * arrived on their own: BaseScreenshotTest was needed by :app and :core:ui and lendable by neither
 * (phase 9), MainDispatcherRule was duplicated into :feature:explore (phase 11), and every feature
 * module after the first needs the fakes.
 *
 * A regular library module, not AGP's `testFixtures`. That was tried at phase 9 and does not work:
 * the Kotlin Android plugin creates no Kotlin compilation for the testFixtures variant -- only
 * compileDebugTestFixturesJavaWithJavac, which is NO-SOURCE against a .kt file. Verified against
 * Kotlin 2.4.10 / AGP 9.3.1. Consumers take this with `testImplementation` (and
 * `androidTestImplementation` where they run on device), so nothing here reaches a production
 * classpath.
 */
plugins {
    id("smartphotos.android.library")
    id("smartphotos.android.compose")
}

android {
    namespace = "com.jiahan.smartcamera.core.testing"
}

dependencies {

    /*
     * api, not implementation, throughout. Everything this module exposes is a type a consumer
     * names directly: FakeNoteRepository *is* a NoteRepository, and a test assigning one to a
     * ViewModel parameter has to resolve that interface. The usual "prefer implementation" advice
     * inverts for a fixtures module -- its whole API surface is other modules' types.
     */
    api(project(":core:domain"))
    // FakeMediaFileRepository implements MediaFileRepository, which is Android-bound (Uri, Bitmap)
    // and so lives in :core:common rather than :core:domain. Declared rather than left to arrive
    // through :core:data's `api` edge, because it is this module's own API surface: the fake *is*
    // that interface.
    api(project(":core:common"))
    api(project(":core:data"))

    api(libs.junit)
    api(libs.kotlinx.coroutines.test)

    // BaseScreenshotTest only. Robolectric renders Compose on the JVM and Roborazzi captures it.
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.ui.test.junit4)
    api(libs.robolectric)
    api(libs.roborazzi)
    api(libs.roborazzi.compose)
}