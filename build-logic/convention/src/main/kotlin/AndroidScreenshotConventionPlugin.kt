import com.android.build.api.dsl.CommonExtension
import io.github.takahirom.roborazzi.RoborazziExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * `smartphotos.android.screenshot` -- applied by the four modules that capture Roborazzi goldens:
 * `:core:ui`, `:feature:home`, `:feature:search` and `:feature:settings`.
 *
 * It exists on this build's own rule, which `:core:ui` wrote down as "a third would earn a
 * convention plugin; two do not". The third and fourth arrived together when `:app`'s
 * `ScreenScreenshotTest` was split between `:feature:home` and `:feature:search`, and at that point
 * four modules were restating the same `outputDir` line for the same reason.
 *
 * What it sets:
 *
 * - The Roborazzi plugin itself, which is what defines `recordRoborazziDebug` /
 *   `verifyRoborazziDebug`. The tasks are per-module, so a module that captures goldens without
 *   this plugin runs its screenshot tests under `testDebugUnitTest` and never diffs them -- a
 *   suite that passes by never comparing anything.
 * - `outputDir`, to a VCS-tracked `src/test/screenshots/` rather than the transient `build/` dir
 *   Roborazzi defaults to. This is the whole reason the plugin is worth having: a golden under
 *   `build/` is rewritten by the run that was supposed to be checked against it, so the default
 *   turns `verifyRoborazziDebug` into a no-op that always passes.
 * - `unitTests.isIncludeAndroidResources`, because Robolectric renders the real composable and a
 *   screenshot of a screen whose strings all resolved to nothing is not a useful golden.
 *
 * What it deliberately does not set:
 *
 * - `androidx-ui-test-manifest`. `createComposeRule()` does need it -- without the ComponentActivity
 *   that artifact merges into the debug manifest, every capture fails to resolve an activity -- but
 *   that is a requirement of *any* Compose test, not of screenshots specifically, and eight feature
 *   modules want it for behaviour suites that take no pictures at all. It lives in
 *   [AndroidFeatureConventionPlugin] for the features, and in `:core:ui`'s own build file, which is
 *   the only screenshot module that is not a feature.
 * - Any dependency on `:core:testing`, where `BaseScreenshotTest` and the Roborazzi artifacts live.
 *   The feature convention already adds it, `:core:ui` declares it for its non-screenshot tests
 *   too, and a convention plugin that pulled in a fixtures module would be the one place a *cycle*
 *   could be introduced by accident.
 *
 * Note that `roborazzi-junit-rule` is not here either, and is no longer declared anywhere: it
 * supplies `RoborazziRule`, and [com.jiahan.smartcamera.screenshot.BaseScreenshotTest] calls
 * `captureRoboImage()` directly instead. Three modules carried it because the first one to capture
 * a screenshot did.
 */
class AndroidScreenshotConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("io.github.takahirom.roborazzi")

        extensions.configure<RoborazziExtension> {
            outputDir.set(layout.projectDirectory.dir("src/test/screenshots"))
        }

        extensions.configure<CommonExtension> {
            testOptions.unitTests.isIncludeAndroidResources = true
        }
    }
}