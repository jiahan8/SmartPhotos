import com.jiahan.smartcamera.buildlogic.ProjectConfig
import com.jiahan.smartcamera.buildlogic.configureTestJvm
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * `smartphotos.kmp.library` -- applied by `:core:domain`, the one module with no Android plugin.
 *
 * It replaces `smartphotos.jvm.library`, and the module's charter survives the swap intact: the
 * point was never the Kotlin JVM plugin specifically, it was that `import android.*` must not
 * resolve. Under `kotlin.multiplatform` the compiler holds that line for `commonMain` -- which
 * compiles against the *intersection* of the targets below, and `android.*` is in none of them.
 * `java.*` is now excluded too, which the JVM plugin could never have enforced. Keep this plugin
 * free of anything Android.
 *
 * The three Apple targets are what make the guarantee mean something. Without them `commonMain`
 * would be common in name only, since a single-JVM-target metadata compilation accepts the whole
 * JDK. They are declared here rather than per-module because a second shared module would want the
 * same three, and a set that drifts between modules is a set that stops being an intersection.
 *
 * `configureTestJvm()` matches `tasks.withType<Test>()`, so it reaches `jvmTest` and skips the
 * Kotlin/Native test tasks. That is correct rather than an oversight: the UTC/en-US pin exists for
 * the Roborazzi goldens in the Android modules, and nothing here renders a timestamp.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")

        extensions.configure<KotlinMultiplatformExtension> {
            jvm {
                compilerOptions {
                    jvmTarget.set(ProjectConfig.JVM_TARGET)
                }
            }
            iosArm64()
            iosSimulatorArm64()
            iosX64()
        }

        // Guarded because the Java plugin arrives with the `jvm()` target rather than from here,
        // and that is an arrangement Kotlin has changed before (`withJava()` used to be opt-in).
        // It has to be set either way: `org.gradle.jvm.version` is published from Java's target
        // compatibility, not from Kotlin's `jvmTarget`, and an Android consumer compiling against
        // 11 refuses a variant claiming the daemon's 21. That failure reads as an unresolvable
        // dependency, which is the opaque variant-resolution error the old plugin warned about.
        plugins.withId("java") {
            extensions.configure<JavaPluginExtension> {
                sourceCompatibility = ProjectConfig.JAVA_VERSION
                targetCompatibility = ProjectConfig.JAVA_VERSION
            }
        }

        configureTestJvm()
    }
}
