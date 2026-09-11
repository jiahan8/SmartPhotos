import com.android.build.api.dsl.LibraryExtension
import com.jiahan.smartcamera.buildlogic.libs
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.kotlin.dsl.configure
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
 * repository interfaces in every feature. Hiding them behind `implementation` fails with
 * `InjectProcessingStep was unable to process ... could not be resolved`, and it fails in
 * `compileDebugAndroidTestKotlin` rather than in `assembleDebug`.
 *
 * `:core:ui` is the mirror image: `implementation`, because a feature *consumes* Compose without
 * handing any of it back out. No feature screen has a Compose type in its signature that a caller
 * must resolve through this edge -- all nine take lambdas, plain values, their own ViewModel, and
 * at most a `SnackbarHostState`, which `:app` resolves through its own Compose dependency.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("smartphotos.android.library")
        pluginManager.apply("smartphotos.android.compose")
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("com.google.dagger.hilt.android")
        /*
         * Every feature declares a `@Serializable` route, so the compiler plugin and the runtime
         * artifact below are unanimous across all nine.
         *
         * An earlier draft of this file argued them out, on the grounds that "a route is not
         * required to be serializable-by-plugin -- a feature with no destination of its own would
         * not need it". That was written against a two-module sample. Nine features later every one
         * of them owns at least one destination and restated both lines verbatim, which is the
         * threshold this build uses. A future feature with no route of its own pays an unused
         * compiler plugin; that is cheaper than nine copies of the same two lines.
         */
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

        verifyNoLateralDependencies()

        /*
         * The `sharedTest/` arrangement, for every feature rather than for the seven that had
         * copied it.
         *
         * A Compose behaviour suite placed there compiles into both the unit-test and androidTest
         * source sets, so it runs under Robolectric in CI *and* on-device, written once. That is
         * what makes it the default: CI runs `compileDebugAndroidTestKotlin` and no emulator, so a
         * suite left in androidTest alone is a suite nothing executes -- which is exactly how four
         * assertions came to sit wrong for months, asserting one screen's copy against another's.
         *
         * It went from one module to seven in a single change, which is past the threshold this
         * build uses. A feature with no `sharedTest/` directory pays a source set that resolves to
         * nothing and three unused test artifacts; that is cheaper than seven copies, and cheaper
         * than the eighth feature discovering by hand that its screen test never ran.
         */
        extensions.configure<LibraryExtension> {
            sourceSets.getByName("test").java.srcDir("src/sharedTest/kotlin")
            sourceSets.getByName("androidTest").java.srcDir("src/sharedTest/kotlin")
            // Robolectric renders a real screen on the JVM and resolves this module's strings with
            // it. Also set by `smartphotos.android.screenshot`; setting it twice is idempotent.
            testOptions.unitTests.isIncludeAndroidResources = true
        }

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
            // `collectAsStateWithLifecycle`, which every one of the nine feature screens
            // calls. It arrives transitively from compose-ui either way; declared so the
            // lifecycle pin decides its version rather than the Compose BOM.
            add("implementation", libs.findLibrary("androidx-lifecycle-runtime-compose").get())
            /*
             * `hiltViewModel()`, which every feature screen defaults its ViewModel parameter to.
             * This is now the only module in the build that declares it, and the only one that
             * needs to: a default argument compiles into the callee, so :app constructing all nine
             * screens in its nav graph does not make the call site :app's.
             *
             * Two near-neighbours are deliberately absent, and neither is in the version catalog
             * any more. lifecycle-viewmodel-compose supplies the plain `viewModel()` and is
             * imported by no file here. hilt-navigation-compose is the older home of
             * `hiltViewModel()` itself -- superseded by the artifact below, not additional to it.
             */
            add(
                "implementation",
                libs.findLibrary("androidx-hilt-lifecycle-viewmodel-compose").get()
            )
            add("implementation", libs.findLibrary("hilt-android").get())
            add("ksp", libs.findLibrary("hilt-android-compiler").get())
            add("implementation", libs.findLibrary("kotlinx-coroutines-android").get())

            add("debugImplementation", libs.findLibrary("androidx-ui-tooling").get())
            /*
             * `createComposeRule()` launches a ComponentActivity, which exists only in the manifest
             * this artifact merges into the debug variant -- without it every Compose test fails
             * with "Unable to resolve activity for Intent ... ComponentActivity". Eight of the nine
             * feature modules declared this line, each with its own copy of that explanation;
             * :feature:explore was the ninth, carrying a debug-only manifest contribution it had no
             * Compose test to need. `ExploreScreenTest` ended that -- all nine need this now.
             *
             * debugImplementation, not testImplementation, and that is the part worth keeping: the
             * manifest merge is per-variant, so this cannot arrive through :core:testing's
             * test-only classpath no matter which configuration declares it there.
             */
            add("debugImplementation", libs.findLibrary("androidx-ui-test-manifest").get())

            // kotlinx-serialization-core, the runtime half of the plugin applied above. Same nine
            // out of nine, same reasoning.
            add("implementation", libs.findLibrary("kotlinx-serialization-core").get())

            add("testImplementation", project(":core:testing"))
            add("testImplementation", libs.findLibrary("junit").get())
            add("testImplementation", libs.findLibrary("mockk").get())
            add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
            // Eight of nine declared this for the same reason: a ViewModel test asserting on the
            // *sequence* a StateFlow emits, or on a SharedFlow with no `.value` to read. Only
            // :feature:explore did not, and its ViewModel is the one with no such assertion yet.
            add("testImplementation", libs.findLibrary("turbine").get())

            /*
             * The on-device half of a feature's screen test. Eight of the nine modules declared
             * these five lines verbatim; :feature:explore was the exception, with no androidTest
             * source set at all. It stopped being one when `ExploreScreenTest` landed in its
             * `sharedTest/`, which compiles into androidTest -- so all nine now use these.
             *
             * :core:testing arrives here as well as on `testImplementation` above because a
             * `sharedTest/` suite compiles into both source sets and builds its ViewModel from the
             * same fakes in each. None of these suites injects anything, which is why no feature
             * module declares a `testInstrumentationRunner`: the default AndroidJUnitRunner is
             * enough, and :app keeps its HiltTestRunner for the suites that do use a component.
             */
            add("androidTestImplementation", project(":core:testing"))
            // The screen-test vocabulary -- waitForText and friends, the resource lookup, the
            // timeout constant. On both test source sets for the same reason :core:testing is:
            // a `sharedTest/` suite compiles into each and names these helpers from both.
            add("androidTestImplementation", project(":core:ui-testing"))
            add("androidTestImplementation", libs.findLibrary("androidx-junit").get())
            add("androidTestImplementation", libs.findLibrary("androidx-test-runner").get())
            add(
                "androidTestImplementation",
                platform(libs.findLibrary("androidx-compose-bom").get()),
            )
            add("androidTestImplementation", libs.findLibrary("androidx-ui-test-junit4").get())
            /*
             * Espresso, which no feature's test sources name and every one of them needs anyway:
             * a Compose rule syncs through `Espresso.onIdle()` on device, so the version that ends
             * up on this classpath decides whether the suite runs at all.
             *
             * Without this line it is not the catalog's 3.7.0. `androidx.test.ext:junit` carries a
             * transitive espresso-core 3.5.0 and nothing raised it, so all nine features resolved
             * that -- while :app, the one module declaring espresso for itself, resolved 3.7.0.
             * 3.5.0 reaches for `InputManager.getInstance` via reflection, which API 36 removed, so
             * every Compose test on a modern device died in `onIdle` with `NoSuchMethodException`
             * before reaching its first assertion: 51 failures across seven modules, and :app green
             * beside them. CI compiles androidTest and never runs it, so nothing reported this.
             *
             * Declared, not merely constrained, so the version travels with the catalog pin.
             */
            add("androidTestImplementation", libs.findLibrary("androidx-espresso-core").get())

            /*
             * The JVM half of the same `sharedTest/` suite. Robolectric is what makes
             * `AndroidJUnit4` resolve to a sandbox rather than the on-device runner, and
             * ui-test-junit4 is what lets a Compose rule exist in the unit-test source set at all.
             * `androidx-junit` carries the runner annotation those suites name.
             *
             * No Compose BOM line: it is already on `implementation` above, which the unit-test
             * compile classpath extends.
             */
            add("testImplementation", libs.findLibrary("robolectric").get())
            add("testImplementation", libs.findLibrary("androidx-junit").get())
            add("testImplementation", libs.findLibrary("androidx-ui-test-junit4").get())
            add("testImplementation", project(":core:ui-testing"))
        }
    }
}

/**
 * Fails configuration if this feature module declares a dependency on another `:feature:*` module
 * or on `:core:data`.
 *
 * AGENTS.md states both rules -- "No feature module depends on another, and none reaches
 * `:core:data`" -- and until now nothing held them. That asymmetry is what this build usually
 * avoids: `:core:domain`'s purity is enforced by leaving the Android plugin off it, so `import
 * android.*` fails to compile rather than failing review. The layering rules one level up were
 * prose, and prose is how the `:core:testing` -> `:core:data` edge survived: unused, invisible in
 * the imports, and quietly putting Firestore, Room and DataStore on all nine features' unit-test
 * classpaths. A rule nothing checks is a rule that decays.
 *
 * Every configuration is scanned, not just the compile ones, because that edge lived on a test
 * classpath. `:core:testing` is the deliberate exception in the other direction -- it is the
 * fixtures module every feature takes, and it no longer reaches `:core:data` itself.
 *
 * Runs at configuration time so it fails before any compilation starts. Under the configuration
 * cache it re-runs whenever configuration does, which is exactly when a dependency could have
 * changed.
 */
private fun Project.verifyNoLateralDependencies() = afterEvaluate {
    val forbidden = configurations.filter {
        // The declaration buckets (`implementation`, `testImplementation`, ...) rather than the
        // resolvable classpaths that extend them. Both see the same dependency, so scanning
        // everything reports one bad line three or four times over and names configurations nobody
        // wrote. Declaring straight onto a resolvable configuration is its own mistake, and not one
        // this build makes.
        !it.isCanBeResolved
    }.flatMap { configuration ->
        configuration.dependencies
            .asSequence()
            .filterIsInstance<ProjectDependency>()
            .map { it.path }
            // Defensive, and kept after the filter above made it redundant: AGP puts a module's own
            // main variant on its androidTest classpaths, which reads as "this feature depends on a
            // :feature:* module". Those are resolvable configurations so they no longer reach here,
            // but a module is never a violation of its own layering rule and should not depend on
            // which bucket AGP chose.
            .filter { it != path }
            .filter { it.startsWith(":feature:") || it == ":core:data" }
            .map { "$it (via ${configuration.name})" }
            .toList()
    }.distinct().sorted()

    if (forbidden.isNotEmpty()) {
        throw GradleException(
            """
            |$path declares a dependency the feature layering forbids:
            |
            |${forbidden.joinToString("\n") { "  - $it" }}
            |
            |A :feature:* module depends on :core:* only. It must not depend on another feature --
            |two screens that need the same thing means that thing belongs in a :core: module -- and
            |it must not reach :core:data, because the repositories a ViewModel injects are
            |interfaces in :core:domain, bound in :app.
            |
            |If a feature needs an Android-typed contract, move the *interface* down to :core:common
            |and leave its implementation in :core:data -- the same move MediaFileRepository made.
            """.trimMargin(),
        )
    }
}