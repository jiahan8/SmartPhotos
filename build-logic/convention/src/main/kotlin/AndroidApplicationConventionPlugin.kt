import com.android.build.api.dsl.ApplicationExtension
import com.jiahan.smartcamera.buildlogic.configureKotlinAndroid
import com.jiahan.smartcamera.buildlogic.configureTestJvm
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * `smartphotos.android.application` -- applied by `:app`, the only application module.
 *
 * It carries only what a second application module would also want. Everything specific to this
 * app -- applicationId, versionCode/Name, the Firebase plugins, the signing and buildType blocks,
 * the Hilt test runner -- stays in `app/build.gradle.kts`, where it is read.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.android")

        extensions.configure<ApplicationExtension> {
            configureKotlinAndroid(this)
        }
        configureTestJvm()
    }
}
