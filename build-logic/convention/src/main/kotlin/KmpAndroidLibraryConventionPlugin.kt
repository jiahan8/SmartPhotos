import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.jiahan.smartcamera.buildlogic.ProjectConfig
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * `smartphotos.kmp.android.library` -- applied by `:core:database`: a multiplatform module that
 * Android must consume as an Android build rather than through its `jvm` variant.
 *
 * `smartphotos.kmp.library` plus AGP's `com.android.kotlin.multiplatform.library` target, with the
 * same `compileSdk`, `minSdk` and JVM target every other Android module takes from [ProjectConfig].
 * That plugin stays free of anything Android, as its KDoc asks; this is the one that is not.
 *
 * It exists with a single user, against build-logic's usual wait for a second, because what it sets
 * is not new: those three values already have a dozen consumers, and a module that restated them
 * as literals would be the fork this included build was written to stop. Namespace stays in the
 * module, as it does for `smartphotos.android.library`.
 *
 * Room is why a module needs this. Its compiler generates `AppDatabase_Impl` for each target, and
 * the Android runtime that code meets on a device is a different artifact from the JVM one, so the
 * `jvm` variant that serves every other shared module is the wrong build to hand Android here.
 */
class KmpAndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("smartphotos.kmp.library")
        pluginManager.apply("com.android.kotlin.multiplatform.library")

        extensions.configure<KotlinMultiplatformExtension> {
            // AGP registers the target as the `android` extension on `kotlin`, which is what the
            // `kotlin { android { } }` accessor in a module build file reaches.
            (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") {
                compileSdk = ProjectConfig.COMPILE_SDK
                minSdk = ProjectConfig.MIN_SDK
                compilerOptions {
                    jvmTarget.set(ProjectConfig.JVM_TARGET)
                }
            }
        }
    }
}