/*
 * The Roborazzi screenshot harness: `BaseScreenshotTest` and the four artifacts it takes to render
 * a composable on the JVM and capture it.
 *
 * Split out of :core:testing, which is the module every feature takes for the fakes and
 * MainDispatcherRule. Everything there is `api` -- correctly, since a fixtures module's API surface
 * is other modules' types -- so the Roborazzi and Robolectric artifacts that only this one class
 * needs were landing on the unit-test compile classpath of all nine features plus :app and
 * :core:ui. :feature:explore, which has no screenshot test and no androidTest source set at all,
 * resolved roborazzi 1.70.0, robolectric 4.16.1 and the whole compose ui-test stack.
 *
 * That is the same shape as the :core:testing -> :core:data edge removed earlier: an unused `api`
 * edge out of the fixtures module, invisible in the imports, putting a heavy toolchain everywhere.
 * The tell that nobody was relying on the leak is that :feature:auth, :feature:note and
 * :feature:preview each declare `testImplementation(libs.robolectric)` for themselves anyway --
 * they name it, so they declare it. Splitting the harness out lets :core:testing hold only what its
 * own sources name (junit and kotlinx-coroutines-test), and leaves the four modules that capture
 * goldens as the only ones resolving Roborazzi.
 *
 * Consumers do not declare this module: `smartphotos.android.screenshot` adds it, and applying that
 * plugin is exactly what makes a module a screenshot module. The four are :core:ui, :feature:home,
 * :feature:search and :feature:settings.
 *
 * A regular library module rather than AGP's `testFixtures`, for the reason :core:testing's build
 * file records: the Kotlin Android plugin creates no Kotlin compilation for that variant.
 */
plugins {
    id("smartphotos.android.library")
    // BaseScreenshotTest.capture() takes a `@Composable () -> Unit` and calls setContent, so this
    // module's own main source set needs the Compose compiler -- not just the Compose artifacts.
    id("smartphotos.android.compose")
    // Deliberately NOT smartphotos.android.screenshot. That plugin adds this module to
    // testImplementation, so applying it here would be a dependency cycle; the plugin fails
    // configuration with a named error rather than leaving that to be discovered.
}

android {
    namespace = "com.jiahan.smartcamera.core.screenshottesting"
}

dependencies {

    /*
     * api throughout, on the same rule :core:testing states: everything this module exposes is a
     * type a consumer names directly. A subclass of BaseScreenshotTest writes @Test, calls
     * `capture { }` with its own composable, and its goldens are produced by Roborazzi's own
     * captureRoboImage() -- so junit, compose and roborazzi are all part of this module's API
     * surface rather than implementation detail.
     */
    api(libs.junit)

    // `composeRule` is a public ComposeContentTestRule and `capture` takes a @Composable lambda, so
    // both the test-rule artifact and the one carrying androidx.compose.runtime.Composable are api.
    // The BOM is api for the same reason it is platform: a consumer resolving Compose through this
    // edge lands on the versions this module compiled against.
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.ui)
    api(libs.androidx.ui.test.junit4)

    // Robolectric renders Compose on the JVM; Roborazzi captures it. Both are named by
    // BaseScreenshotTest itself -- @RunWith(RobolectricTestRunner::class), @GraphicsMode, @Config
    // and RobolectricDeviceQualifiers from the first, captureRoboImage() from the second.
    api(libs.robolectric)
    api(libs.roborazzi)
    api(libs.roborazzi.compose)
}