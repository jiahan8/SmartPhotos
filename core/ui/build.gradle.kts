/*
 * Android library: the Compose vocabulary every feature screen draws with.
 *
 * `common/` (14 composables), `ui/theme/` and the two `util/` helpers whose only callers are in
 * those two packages. This was the last lateral edge inside :app -- every feature package imported
 * `common/`, so no feature could become its own module while this code sat beside them.
 *
 * The dependency shape is what makes it a sibling of :core:data rather than another layer: nothing
 * here touches a repository, Room or DataStore. It reaches :core:domain for HomeNote, MediaDetail
 * and AppConstants, and stops there.
 *
 * Unlike phases 6 and 7 this was not a pure `git mv`. Resources moved for the first time, and with
 * android.nonTransitiveRClass=true this module's R holds only its own 16 strings -- so the nine
 * :app screens that share that vocabulary import it as `UiR` rather than finding it on their own R.
 *
 * One module, not two. Now in Android splits core:designsystem (theme, atoms) from core:ui
 * (composites that know domain types, which NoteItem does -- it takes a HomeNote). At 1,294 lines
 * nothing consumes one half without the other, so the split would buy nothing here. Revisit if a
 * second app or a Wear/TV surface appears.
 *
 * NoteItemScreenshotTest and its four goldens live here now, in src/test/screenshots. They could
 * not travel with NoteItem at first: the test extends BaseScreenshotTest, which ScreenScreenshotTest
 * in :app also extends, and AGP's testFixtures -- the mechanism for lending a harness across a
 * module boundary -- does not work here, because the Kotlin Android plugin creates no Kotlin
 * compilation for that variant (only compileDebugTestFixturesJavaWithJavac, NO-SOURCE against a .kt
 * file; verified on Kotlin 2.4.10 / AGP 9.3.1). Copying the Robolectric @Config instead would drift
 * silently -- change the device qualifier in one module and the other's goldens keep verifying the
 * old profile. :core:testing resolved it by being a plain library module rather than testFixtures,
 * and being blocked on it twice is what justified building it.
 */
plugins {
    id("smartphotos.android.library")
    id("smartphotos.android.compose")
    // NoteItem's goldens live here now, so the Roborazzi tasks have to as well. :app keeps its own
    // copy of this plugin for ScreenScreenshotTest -- two modules capture screenshots, and each
    // configures its own outputDir. A third would earn a convention plugin; two do not.
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.jiahan.smartcamera.core.ui"

    testOptions {
        // Robolectric renders NoteItem on the JVM and needs this module's own resources with it.
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

roborazzi {
    // VCS-tracked rather than the transient build/ dir, so the PNGs are the committed baseline
    // verifyRoborazziDebug compares against. Same reason as :app's.
    outputDir.set(layout.projectDirectory.dir("src/test/screenshots"))
}

dependencies {

    // api, not implementation: HomeNote and MediaDetail are parameters of NoteItem and
    // MediaThumbnail, so anything calling them compiles against :core:domain through this edge.
    api(project(":core:domain"))

    /*
     * api, not implementation, for the Compose artifacts whose types appear in this module's public
     * signatures -- the same rule :core:data states below for its @Inject constructor parameters,
     * arrived at from the other direction.
     *
     * `Modifier` is a parameter of 25 public composables here; `SnackbarHostState`, `Typography`,
     * `Color`, `Shape`, `ImageVector` and `LazyListState` each appear in at least one. A consumer
     * cannot call `NoteItem(modifier = ...)` without resolving `Modifier`, so hiding these behind
     * `implementation` makes this module's API uncompilable on its own. It builds today only
     * because :app happens to declare the same artifacts for its own screens -- exactly the
     * accident that let :core:data's DataStore and Room bindings pass until androidTest walked the
     * graph. The first :feature:* module that depends on :core:ui without redeclaring Compose is
     * where it would have surfaced.
     *
     * The BOM is `api` for the same reason it is `platform`: a consumer resolving Compose through
     * this edge should land on the versions this module compiled against, not its own.
     */
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.ui)
    api(libs.androidx.ui.graphics)
    api(libs.androidx.material3)
    api(libs.androidx.foundation)

    // implementation, deliberately: used inside function bodies only. @Preview is declared on this
    // module's own preview functions, and the icons below are drawn here rather than handed out --
    // `ImageVector` itself travels with ui-graphics above.
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material.icons.core)
    // Rounded.MoreHoriz, Rounded.ContentCopy and Rounded.EditNote are not in material-icons-core,
    // which carries only a small default set. The other nine icons used here are.
    implementation(libs.androidx.material.icons.extended)

    // AsyncImage, in MediaThumbnail and ProfileAvatar.
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.ui.tooling)
    // createComposeRule() launches a ComponentActivity, which exists only in the manifest this
    // artifact merges into the debug variant. Robolectric reads that merged manifest, so without it
    // every screenshot test fails with "Unable to resolve activity for Intent ... ComponentActivity".
    // debugImplementation, not testImplementation: the merge is per-variant, so it cannot arrive
    // through :core:testing's test-only classpath.
    debugImplementation(libs.androidx.ui.test.manifest)

    // DateTimeUtilsTest and FlowUtilsTest only -- both plain JVM tests with no Robolectric and no
    // Compose. The screenshot tests that render this module's composables stayed in :app; see the
    // comment above the module header for why.
    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
