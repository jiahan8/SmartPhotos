import com.jiahan.smartcamera.buildlogic.ProjectConfig
import com.jiahan.smartcamera.buildlogic.configureTestJvm
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/**
 * `smartphotos.jvm.library` -- applied by `:core:domain`, the one module with no Android plugin.
 *
 * The absence of `com.android.library` here is the module's whole point: `import android.*` does
 * not resolve in a plain Kotlin JVM project, so the purity rule AGENTS.md states in prose is held
 * by the compiler. Keep this plugin free of anything Android.
 *
 * The Java/JVM target is the same 11 the Android modules use, so a domain type compiled here is
 * loadable by them; a mismatch surfaces as an opaque Gradle variant-resolution failure rather than
 * an obvious version error.
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")

        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = ProjectConfig.JAVA_VERSION
            targetCompatibility = ProjectConfig.JAVA_VERSION
        }
        extensions.configure<KotlinJvmProjectExtension> {
            compilerOptions {
                jvmTarget.set(ProjectConfig.JVM_TARGET)
            }
        }
        configureTestJvm()
    }
}
