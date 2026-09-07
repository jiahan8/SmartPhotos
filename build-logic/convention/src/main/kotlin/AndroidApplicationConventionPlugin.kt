import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.jiahan.smartcamera.buildlogic.configureKotlinAndroid
import com.jiahan.smartcamera.buildlogic.configureManagedDevices
import com.jiahan.smartcamera.buildlogic.configureTestJvm
import com.jiahan.smartcamera.buildlogic.disableAndroidTestWithoutSources
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType

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
            configureManagedDevices(this)
        }
        // A no-op for :app, which has androidTest sources -- here because a second application
        // module without them would hit exactly what :core:common hit, and this plugin carries what
        // that module would want. The variant API rather than the DSL, hence the components
        // extension rather than a line inside the block above.
        disableAndroidTestWithoutSources(
            extensions.getByType<ApplicationAndroidComponentsExtension>(),
        )
        configureTestJvm()
    }
}
