/*
 * The sixth feature module: the Search screen, its ViewModel, its route and both test suites.
 *
 * Same shape as :feature:favorite, which is the point of having done that one first -- three
 * sources, two test suites, and a set of strings to decide. All six of search's own strings turned
 * out to be exclusive to `SearchScreen`, so unlike favorite and explore there was nothing to send
 * down; the one shared string, `no_results_found`, had already gone to :core:ui when favorite
 * moved, which is what that decision was for.
 *
 * `SearchRoute` carries `SEARCH_DEEP_LINK_URI_PATTERN`, read by `SmartPhotosNavGraph`. That is a
 * downward read like any other now -- :app depends on this module -- and it is the reason the
 * constant lives on the route rather than in `navigation/`: the pattern belongs to the destination.
 *
 * Worth recording what this module does *not* have, because it is the interesting half. Search used
 * to be the last consumer of `NoteHandler.observeNoteMutations`, applying three list transforms by
 * hand so a note deleted or favorited elsewhere would update here. All of that is gone: results are
 * a filtered read of the `notes` table, so a mutation on any screen arrives by re-emission. The
 * remote `searchNotes` still reads the whole collection from Firestore -- it just writes the
 * results through on the way out, which is what keeps this a live search rather than a narrower one.
 */
plugins {
    id("smartphotos.android.feature")
    // SearchRoute is @Serializable. Left out of the feature convention deliberately -- see the
    // note in :feature:settings.
    alias(libs.plugins.kotlin.serialization)
    // searchScreen_idle's golden lives here now -- the other half of :app's ScreenScreenshotTest,
    // whose three Home captures went to :feature:home. See the note in that module's build file.
    id("smartphotos.android.screenshot")
}

android {
    namespace = "com.jiahan.smartcamera.feature.search"
}

dependencies {

    /*
     * :core:domain, :core:ui, Compose, icons, Hilt, lifecycle, :core:testing, junit, mockk and
     * kotlinx-coroutines-test all arrive from `smartphotos.android.feature`.
     */

    // NoteShareDelegate and NoteErrorReporter.
    implementation(project(":core:common"))

    // ShareCompat.IntentBuilder, for the share chooser.
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.core)

    // SearchViewModelTest asserts on `actionError`, a SharedFlow with no `.value` to read.
    testImplementation(libs.turbine)

    androidTestImplementation(project(":core:testing"))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
}
