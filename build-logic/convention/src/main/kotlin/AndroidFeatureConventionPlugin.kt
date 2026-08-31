import com.jiahan.smartcamera.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * `smartphotos.android.feature` -- applied by every `:feature:*` module.
 *
 * This plugin did not exist while `:feature:explore` was the only feature, on build-logic's own
 * rule: put a setting here when more than one module wants it *for the same reason*. One module is
 * a sample size of one, and explore's build file said in as many words that the shape of the second
 * feature was what it existed to discover. `:feature:settings` is that second module, and it wanted
 * all of the following verbatim.
 *
 * What is deliberately *not* here:
 *
 * - `namespace`, for the same reason [AndroidLibraryConventionPlugin] leaves it out: every module
 *   needs its own, and a convention default would be wrong everywhere.
 * - `coil-compose` and `activity-compose`. Explore loads remote images and handles a system back
 *   press in its search mode; settings does neither. A dependency one feature happens to need is
 *   not a convention.
 * - `kotlin-serialization`. Both features declare it for their `@Serializable` route, which makes
 *   it a genuine candidate, but a route is not required to be serializable-by-plugin (a feature
 *   with no destination of its own would not need it) and applying a compiler plugin no source
 *   needs is the sort of thing that is hard to notice later. Left to the modules.
 *
 * The icon packs *are* here, and were not in the first draft of this plugin -- settings was
 * expected not to want them and immediately failed to compile on `Icons.Rounded.Check` and
 * `Icons.AutoMirrored.Filled.ArrowBack`. Two features drawing Material icons for the same reason
 * is the rule this file is built on, so they moved in rather than being restated twice.
 *
 * The `:core:domain` edge is `api`, not `implementation`, and that is load-bearing rather than
 * stylistic. Hilt aggregates every `@InstallIn(SingletonComponent::class)` binding into one
 * component generated in `:app`, so `:app`'s annotation processor has to resolve a feature
 * ViewModel's `@Inject constructor` parameter types itself -- and those are `:core:domain`
 * repository interfaces in both features. Hiding them behind `implementation` fails with
 * `InjectProcessingStep was unable to process ... could not be resolved`, and it fails in
 * `compileDebugAndroidTestKotlin` rather than in `assembleDebug`.
 *
 * `:core:ui` is the mirror image: `implementation`, because a feature *consumes* Compose without
 * handing any of it back out. Neither `ExploreScreen` nor `SettingsScreen` has a Compose type in
 * its signature that a caller must resolve -- they take lambdas, a `SnackbarHostState` from
 * `:app`'s own Compose dependency, and their own ViewModel.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("smartphotos.android.library")
        pluginManager.apply("smartphotos.android.compose")
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("com.google.dagger.hilt.android")

        dependencies {
            add("api", project(":core:domain"))
            add("implementation", project(":core:ui"))

            add("implementation", platform(libs.findLibrary("androidx-compose-bom").get()))
            add("implementation", libs.findLibrary("androidx-ui").get())
            add("implementation", libs.findLibrary("androidx-ui-graphics").get())
            add("implementation", libs.findLibrary("androidx-material3").get())
            add("implementation", libs.findLibrary("androidx-material-icons-core").get())
            // AutoMirrored.Filled.ArrowBack is not in material-icons-core, which carries only a
            // small default set. Both features reach past it.
            add("implementation", libs.findLibrary("androidx-material-icons-extended").get())
            add("implementation", libs.findLibrary("androidx-foundation").get())
            add("implementation", libs.findLibrary("androidx-lifecycle-runtime-ktx").get())
            add("implementation", libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
            add("implementation", libs.findLibrary("androidx-hilt-lifecycle-viewmodel-compose").get())
            add("implementation", libs.findLibrary("hilt-android").get())
            add("ksp", libs.findLibrary("hilt-android-compiler").get())
            add("implementation", libs.findLibrary("kotlinx-coroutines-android").get())

            add("debugImplementation", libs.findLibrary("androidx-ui-tooling").get())

            add("testImplementation", project(":core:testing"))
            add("testImplementation", libs.findLibrary("junit").get())
            add("testImplementation", libs.findLibrary("mockk").get())
            add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
        }
    }
}