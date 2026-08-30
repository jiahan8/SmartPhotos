import com.android.build.api.dsl.LibraryExtension
import com.jiahan.smartcamera.buildlogic.configureKotlinAndroid
import com.jiahan.smartcamera.buildlogic.configureTestJvm
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * `smartphotos.android.library` -- applied by `:core:data` and `:core:ui`.
 *
 * Note what it does not set: `namespace`. Every library needs its own, so leaving it out of the
 * convention forces each module to declare one rather than inherit a wrong default.
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("org.jetbrains.kotlin.android")

        extensions.configure<LibraryExtension> {
            configureKotlinAndroid(this)
        }
        configureTestJvm()
    }
}
