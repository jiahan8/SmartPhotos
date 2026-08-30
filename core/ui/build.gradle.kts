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
 * NoteItemScreenshotTest did NOT come with NoteItem, and that is a toolchain limit rather than a
 * choice. It extends BaseScreenshotTest, which ScreenScreenshotTest in :app also extends, so the
 * harness has to be shared; AGP's testFixtures is the mechanism for that, but the Kotlin Android
 * plugin creates no Kotlin compilation for the testFixtures variant -- only
 * compileDebugTestFixturesJavaWithJavac, which is NO-SOURCE for a .kt file. Verified against
 * Kotlin 2.4.10 / AGP 9.3.1. The alternative was a second copy of the same Robolectric @Config,
 * which drifts silently: change the device qualifier in one and the other module's goldens keep
 * the old profile. So the screenshot tests and all nine goldens stay in :app for now, alongside
 * the three :core:data repository tests phase 7 left there for the same kind of reason. This is
 * the debt :core:testing is meant to pay at phase 12 -- and it is now the second module blocked on
 * it, which is the forcing function that module was waiting for.
 */
plugins {
    id("smartphotos.android.library")
    id("smartphotos.android.compose")
}

android {
    namespace = "com.jiahan.smartcamera.core.ui"

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

    // DateTimeUtilsTest and FlowUtilsTest only -- both plain JVM tests with no Robolectric and no
    // Compose. The screenshot tests that render this module's composables stayed in :app; see the
    // comment above the module header for why.
    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
