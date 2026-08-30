import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * `smartphotos.android.compose` -- applied on top of the application or library convention by the
 * two modules that hold composables, `:app` and `:core:ui`.
 *
 * Split from those two rather than folded into them because `:core:data` holds no Compose at all,
 * and turning the build feature on there would cost it the Compose compiler for nothing.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        extensions.configure<CommonExtension> {
            buildFeatures.compose = true
        }
    }
}
