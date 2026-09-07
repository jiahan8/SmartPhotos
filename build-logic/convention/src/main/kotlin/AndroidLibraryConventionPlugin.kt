import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.jiahan.smartcamera.buildlogic.configureKotlinAndroid
import com.jiahan.smartcamera.buildlogic.configureManagedDevices
import com.jiahan.smartcamera.buildlogic.configureTestJvm
import com.jiahan.smartcamera.buildlogic.disableAndroidTestWithoutSources
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType

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
            configureManagedDevices(this)
        }
        // Five of these libraries have no instrumented tests, and a device-test task in one of
        // those does not skip itself. The variant API rather than the DSL, hence the components
        // extension rather than a line inside the block above.
        disableAndroidTestWithoutSources(extensions.getByType<LibraryAndroidComponentsExtension>())
        configureTestJvm()
    }
}
