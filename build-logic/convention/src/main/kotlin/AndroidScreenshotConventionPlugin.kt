import com.android.build.api.dsl.CommonExtension
import io.github.takahirom.roborazzi.RoborazziExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

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
 * - `testImplementation(project(":core:screenshot-testing"))`, the harness itself: BaseScreenshotTest
 *   and the Roborazzi/Robolectric/compose-ui-test artifacts it names. Applying this plugin is what
 *   makes a module a screenshot module, so the plugin and the harness edge are the same fact and
 *   there is no arrangement in which one is wanted without the other.
 *
 * What it deliberately does not set:
 *
 * - `androidx-ui-test-manifest`. `createComposeRule()` does need it -- without the ComponentActivity
 *   that artifact merges into the debug manifest, every capture fails to resolve an activity -- but
 *   that is a requirement of *any* Compose test, not of screenshots specifically, and eight feature
 *   modules want it for behaviour suites that take no pictures at all. It lives in
 *   [AndroidFeatureConventionPlugin] for the features, and in `:core:ui`'s own build file, which is
 *   the only screenshot module that is not a feature.
 * - Any dependency on `:core:testing`. That is the *fixtures* module -- the nine fakes and
 *   MainDispatcherRule -- which the feature convention already adds and `:core:ui` declares for its
 *   non-screenshot tests. It is also, being a module every feature takes, the one place a cycle
 *   could be introduced by accident.
 *
 *   An earlier draft of this file used that second point to argue against adding *any* module
 *   here, and that is why `BaseScreenshotTest` sat in `:core:testing` alongside the fakes. The
 *   objection was sound about a fixtures module and wrong about a harness: because everything in
 *   `:core:testing` is `api`, the four modules that capture goldens were paying for Roborazzi
 *   together with the nine features that do not. `:core:screenshot-testing` exists to be depended
 *   on from exactly here, is nobody's transitive dependency, and the cycle it could form is a
 *   single edge -- so [verifyNotTheHarnessItself] checks it rather than a comment asking nobody to
 *   write it.
 *
 * Note that `roborazzi-junit-rule` is not here either, and is no longer declared anywhere: it
 * supplies `RoborazziRule`, and [com.jiahan.smartcamera.screenshot.BaseScreenshotTest] calls
 * `captureRoboImage()` directly instead. Three modules carried it because the first one to capture
 * a screenshot did.
 */
class AndroidScreenshotConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        verifyNotTheHarnessItself()

        pluginManager.apply("io.github.takahirom.roborazzi")

        extensions.configure<RoborazziExtension> {
            outputDir.set(layout.projectDirectory.dir("src/test/screenshots"))
        }

        extensions.configure<CommonExtension> {
            testOptions.unitTests.isIncludeAndroidResources = true
        }

        dependencies {
            // testImplementation, not api or implementation: a golden is a unit test, and nothing
            // in this harness may reach a production classpath. The on-device counterpart is
            // deliberately absent -- Roborazzi renders under Robolectric on the JVM, so there is no
            // androidTest story for it to have.
            add("testImplementation", project(HARNESS_MODULE))
        }
    }
}

private const val HARNESS_MODULE = ":core:screenshot-testing"

/**
 * Fails configuration if the harness module applies this plugin to itself.
 *
 * The single edge this plugin adds points at [HARNESS_MODULE], so that module applying it would be
 * a project depending on itself -- which Gradle reports as a circular dependency at *resolution*
 * time, from a task in a module nobody was editing. Catching it here names the actual mistake, and
 * costs one string comparison per applying project.
 *
 * There is no legitimate reason for the harness to capture goldens of its own: it contains one
 * abstract class and no composable worth a picture.
 */
private fun Project.verifyNotTheHarnessItself() {
    if (path == HARNESS_MODULE) {
        throw GradleException(
            """
            |$path must not apply `smartphotos.android.screenshot`.
            |
            |That plugin adds `testImplementation(project("$HARNESS_MODULE"))`, so applying it here
            |makes this module depend on itself. $path *is* the screenshot harness -- it supplies
            |BaseScreenshotTest and the Roborazzi artifacts to the modules that capture goldens, and
            |captures none of its own.
            """.trimMargin(),
        )
    }
}