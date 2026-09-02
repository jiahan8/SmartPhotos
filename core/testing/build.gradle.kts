/*
 * Test fixtures shared across modules: the nine repository/handler fakes and MainDispatcherRule.
 *
 * This is the module the plan deliberately kept until last, on the rule that every `:core:` module
 * needs a forcing function -- something that will not compile without it. Three arrived on their
 * own: BaseScreenshotTest was needed by :app and :core:ui and lendable by neither (phase 9),
 * MainDispatcherRule was duplicated into :feature:explore (phase 11), and every feature module
 * after the first needs the fakes.
 *
 * BaseScreenshotTest has since moved to :core:screenshot-testing. It was the only Compose-shaped
 * thing here, and because everything below is `api`, it put Roborazzi, Robolectric and the compose
 * ui-test stack on the unit-test compile classpath of every module that takes this one -- nine
 * features that mostly capture nothing. Same shape as the :core:data edge removed below, one layer
 * over. What is left is the fakes and the dispatcher rule, which is what all fourteen consumers
 * actually take this module for.
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
    // No smartphotos.android.compose: BaseScreenshotTest was the only source here that declared a
    // @Composable, and it now lives in :core:screenshot-testing. The fakes and MainDispatcherRule
    // are plain Kotlin over :core:domain / :core:common interfaces.
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
    // and so lives in :core:common rather than :core:domain. Its own API surface: the fake *is*
    // that interface.
    api(project(":core:common"))

    /*
     * No :core:data edge, and its absence is load-bearing rather than an omission.
     *
     * Nothing here names a type from that module: every fake implements an interface, and those
     * interfaces are all in :core:domain (or :core:common, above) precisely so that a test never
     * has to resolve a `Default*` to stand in for one. The edge existed anyway, and `api` meant it
     * put firebase-firestore, room-ktx, datastore-preferences and play:app-update on the unit-test
     * compile classpath of every module that takes this one -- which is all nine features. "No
     * feature module depends on :core:data" then held for main sources and quietly failed for
     * tests, in the one place the fakes exist to make the dependency unnecessary.
     *
     * It also cost :core:data the use of this module: with the edge in place the reverse direction
     * was a cycle, so that module's own tests declare junit, mockk and Robolectric directly rather
     * than inheriting them here. That is now a free choice rather than a constraint -- see the note
     * in core/data/build.gradle.kts.
     *
     * So: a fixtures module is a supplier to the data layer or a consumer of it, never both. Adding
     * this edge back means a fake started naming an implementation, which is the thing to fix
     * instead.
     */

    api(libs.junit)
    api(libs.kotlinx.coroutines.test)

    /*
     * The compose-bom, ui-test-junit4, robolectric, roborazzi and roborazzi-compose lines that used
     * to close this block went with BaseScreenshotTest to :core:screenshot-testing, which the
     * `smartphotos.android.screenshot` convention adds to the four modules that capture goldens.
     *
     * Nothing here names any of them, and `api` meant every consumer resolved them regardless. The
     * modules that legitimately want Robolectric for a non-screenshot suite -- :app, :core:data,
     * :feature:auth, :feature:note, :feature:preview -- were already declaring it themselves, which
     * is how the leak stayed invisible: it was never the edge anyone was relying on.
     */
}