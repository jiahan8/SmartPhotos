import com.jiahan.smartcamera.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * `smartphotos.kmp.viewmodel` -- applied by each `:feature:<name>-viewmodel` module, the
 * multiplatform half of a feature that holds its ViewModel and that ViewModel's suite.
 *
 * `:feature:explore-viewmodel` declared all of this by hand while it was the only one, on
 * build-logic's rule that one module is a sample size of one. `:feature:auth-viewmodel` and
 * `:feature:settings-viewmodel` arrived together wanting the same lines for the same reason, which
 * is the threshold -- so they live here, and a new ViewModel module's build file is the plugin id.
 *
 * The module is named `<feature>-viewmodel` for a reason outside this file:
 * `smartphotos.android.feature`'s layering check allows a feature exactly one kind of `:feature:`
 * edge, to its own `<feature>-` module.
 */
class KmpViewModelConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("smartphotos.kmp.library")

        extensions.configure<KotlinMultiplatformExtension> {
            sourceSets.getByName("commonMain").dependencies {
                // api: a ViewModel's constructor parameters are :core:domain interfaces, and its
                // Hilt subclass in the feature module restates them.
                api(project(":core:domain"))
                // api: the class extends ViewModel, so a consumer cannot subclass or call it
                // without resolving this. It is also the dependency that set the Apple targets --
                // see smartphotos.kmp.library on iosX64.
                api(libs.findLibrary("androidx-lifecycle-viewmodel").get())
                // api: `uiState` is a StateFlow in every one of them.
                api(libs.findLibrary("kotlinx-coroutines-core").get())
            }

            sourceSets.getByName("commonTest").dependencies {
                // kotlin-test rather than junit, and no mockk: these suites compile for the Apple
                // targets too, where neither exists. :core:domain-testing's fakes stand in for
                // mocks, and Turbine is multiplatform, so a suite asserting on an emitted sequence
                // keeps it.
                implementation(libs.findLibrary("kotlin-test").get())
                implementation(libs.findLibrary("kotlinx-coroutines-test").get())
                implementation(libs.findLibrary("turbine").get())
                implementation(project(":core:domain-testing"))
            }
        }
    }
}