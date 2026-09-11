import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * `smartphotos.android.compose` -- applied on top of the application or library convention by the
 * three modules that hold composables: `:app`, `:core:ui` and `:core:screenshot-testing`, whose
 * `BaseScreenshotTest.capture()` takes a `@Composable` lambda.
 *
 * Split from those two conventions rather than folded into them because `:core:data` holds no
 * Compose at all, and turning the build feature on there would cost it the Compose compiler for
 * nothing.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        extensions.configure<CommonExtension> {
            buildFeatures.compose = true
        }
    }
}
