import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.jiahan.smartcamera.buildlogic.ProjectConfig
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * `smartphotos.kmp.android.library` -- applied by `:core:database` and `:core:firebase`: multiplatform
 * modules that Android must consume as an Android build rather than through their `jvm` variant.
 *
 * `smartphotos.kmp.library` plus AGP's `com.android.kotlin.multiplatform.library` target, with the
 * same `compileSdk`, `minSdk` and JVM target every other Android module takes from [ProjectConfig].
 * That plugin stays free of anything Android, as its KDoc asks; this is the one that is not.
 *
 * It was written with a single user, against build-logic's usual wait for a second, because what it
 * sets is not new: those three values already have a dozen consumers, and a module that restated
 * them as literals would be the fork this included build was written to stop. `:core:firebase` has
 * since become the second. Namespace stays in the module, as it does for
 * `smartphotos.android.library`.
 *
 * Code generated or inlined per target is why a module needs this. Room's compiler generates
 * `AppDatabase_Impl` for each target; GitLive's `invoke`/`data` are inline, so their bodies land in
 * the calling module per target. Either way, the Android build is a different artifact from the JVM
 * one, so the `jvm` variant that serves every other shared module is the wrong build to hand Android.
 * `:core:firebase` also overrides the JVM target set here to 17 -- its build file says why.
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