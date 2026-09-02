/*
 * The fifth feature module: the Favorite screen, its ViewModel, its route and both test suites.
 *
 * The first of the five packages that `note/`'s delegates used to hold in :app, and the smallest,
 * which is why it went first -- a pilot for the four that follow rather than a hard case. By the
 * time it moved there was nothing left to decouple: NoteHandler had been deleted, the two delegates
 * had gone down to :core:common, and the only thing this package still reached up for was :app's
 * `R`. **That is the shape to expect for the remaining four** -- the work is in the resources, not
 * in the Kotlin.
 *
 * Favorite is also the screen this whole migration was modelled on. It never injected NoteHandler,
 * because `getFavoriteNotesStream` was a Room-backed Flow from the start, so it had no
 * peer-to-peer event to collect while Home and Search did. Its `combine(<query>, <status>)` shape
 * is what the other three were rewritten into.
 *
 * Three strings, and they split the way explore's six did. `favorite_note_to_see_it_here` and
 * `search_favorites` are this screen's alone and travelled. `no_results_found` has consumers here
 * and in `search`, which is the next module out, so it went *down* to :core:ui rather than
 * sideways -- the `cd_back` rule. Note what did **not** travel: `R.string.favorite`. It looks like
 * this module's, but FavoriteScreen never renders it; its two consumers are the bottom-bar label in
 * `navigation/TopLevelDestination.kt` and the favorite action's `onClickLabel` in
 * `preview/NotePreviewScreen.kt`. Those are a destination name and an action label that happen to
 * share a word -- the `explore` case, not the `profile` one -- so it stays in :app and becomes a
 * decision when preview moves.
 */
plugins {
    id("smartphotos.android.feature")
}

android {
    namespace = "com.jiahan.smartcamera.feature.favorite"
}

dependencies {

    /*
     * :core:domain, :core:ui, the Compose set, icons, Hilt, lifecycle, the serialization plugin and
     * its runtime, and the whole test/androidTest baseline -- :core:testing, junit, mockk,
     * kotlinx-coroutines-test, Turbine and the five on-device lines -- all arrive from
     * `smartphotos.android.feature`. What is left here is what only this feature needs.
     */

    // NoteShareDelegate and NoteErrorReporter, which four screens share and which came down here
    // when NoteActionsDelegate inlined. The third module to declare :core:common, and the first to
    // want it for the note delegates rather than the validators or the media seam.
    implementation(project(":core:common"))

    // ShareCompat.IntentBuilder, for the share chooser.
    implementation(libs.androidx.core.ktx)
}
