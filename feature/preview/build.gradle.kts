/*
 * The eighth feature module: the three preview screens (note, photo, video), their ViewModels,
 * their routes and the note preview's instrumented suite.
 *
 * The largest feature module and the one with the most third-party surface -- ExoPlayer and Coil
 * both arrive here rather than in the convention, because no other feature plays video or loads a
 * full-screen image. That is the feature convention's rule working as intended: a dependency goes
 * in `build-logic` only when a second module wants it *for the same reason*.
 *
 * `PreviewRoutes.kt` carries `MediaSourceType`, the one enum used as a navigation argument, and it
 * keeps its `@Keep`: Navigation resolves enum arguments through `Class.forName()`, so R8 renaming
 * it breaks navigation in release builds only -- a failure invisible to both debug runs and unit
 * tests. `SmartPhotosNavGraph` constructs `MediaSourceType.REMOTE`, which is a downward read.
 *
 * Two strings did not travel, and they are the `explore` case rather than the `profile` one.
 * `NotePreviewScreen` rendered `R.string.note` as its title and `R.string.favorite` as an action's
 * `onClickLabel`, and both names are also bottom-bar tab labels in `navigation/TopLevelDestination`
 * -- but the tabs point at the note *composer* and at Favorite, which are different destinations
 * from "the screen showing this note" and "favorite this note". Same copy, different referents, so
 * :app keeps the tab labels and this module declares `note_preview_title` and `cd_favorite_note`.
 * **The consumer count cannot tell that case from a genuinely shared string; only the call sites
 * can.** `copy_text` is the contrast: one string, already down in :core:ui, read here as `UiR`.
 *
 * All three ViewModels have since moved again, to :feature:preview-viewmodel's `commonMain`, the way
 * EditNoteViewModel did. What stays here for each is its `Hilt*ViewModel`: the subclass Hilt builds
 * and the place its route is decoded. The photo and video subclasses decode all the way to a
 * PhotoSource or VideoSource, which is what keeps MediaSourceType, and its `@Keep`, in this module.
 */
plugins {
    id("smartphotos.android.feature")
}

android {
    namespace = "com.jiahan.smartcamera.feature.preview"

}

dependencies {

    /*
     * :core:domain, :core:ui, the Compose set, icons, Hilt, lifecycle, the serialization plugin and
     * its runtime, and the whole test/androidTest baseline -- :core:testing, junit, mockk,
     * kotlinx-coroutines-test, Turbine and the five on-device lines -- all arrive from
     * `smartphotos.android.feature`. What is left here is what only this feature needs.
     */

    // The three preview ViewModels, in this feature's multiplatform half. api for the reason
    // :feature:explore's build file gives for its own: each is its screen's `viewModel` parameter
    // type and a Hilt subclass's supertype, and :app has to resolve both.
    api(project(":feature:preview-viewmodel"))

    // NoteActionError.resolve, NoteDelegateModule for the two note delegates NotePreviewViewModel
    // injects, and toPlatformUri() -- for a note's shared media, and for the MediaUri the photo and
    // video previews render and share.
    implementation(project(":core:common"))

    // ShareCompat.IntentBuilder.
    implementation(libs.androidx.core.ktx)

    // toRoute<...>() in the three Hilt*ViewModel subclasses, which decode each route so the shared
    // ViewModels take a plain noteId, PhotoSource or VideoSource.
    implementation(libs.androidx.navigation.compose)

    // AsyncImage, for the full-screen photo.
    implementation(libs.coil.compose)

    // VideoPreviewScreen embeds a PlayerView through AndroidView. Only this feature plays video.
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    // NotePreviewScreenTest and the photo and video route-decode suites are Robolectric-backed; the
    // artifacts for that arrive from `smartphotos.android.feature`. The three ViewModel suites no
    // longer are, having followed their subjects into :feature:preview-viewmodel's commonTest.
}
