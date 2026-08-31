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
 */
plugins {
    id("smartphotos.android.feature")
    // The three routes are @Serializable. Left out of the feature convention deliberately -- see
    // the note in :feature:settings.
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.jiahan.smartcamera.feature.preview"
}

dependencies {

    /*
     * :core:domain, :core:ui, Compose, icons, Hilt, lifecycle, :core:testing, junit, mockk and
     * kotlinx-coroutines-test all arrive from `smartphotos.android.feature`.
     */

    // NoteShareDelegate and NoteErrorReporter, plus MediaFileRepository -- PhotoPreviewViewModel
    // and VideoPreviewViewModel download to a cache file before sharing.
    implementation(project(":core:common"))

    // ShareCompat.IntentBuilder and androidx.core.net.toUri.
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.core)

    // toRoute<PreviewRoute>() in all three ViewModels.
    implementation(libs.androidx.navigation.compose)

    // AsyncImage, for the full-screen photo.
    implementation(libs.coil.compose)

    // VideoPreviewScreen embeds a PlayerView through AndroidView. Only this feature plays video.
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    // The ViewModel tests assert on SharedFlows with no `.value` to read.
    testImplementation(libs.turbine)
    // NotePreviewViewModelTest is Robolectric-backed: toRoute() builds a real Bundle.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)

    // createAndroidComposeRule() launches a ComponentActivity that exists only in the manifest this
    // artifact merges into the debug variant -- see the same note in :core:ui.
    debugImplementation(libs.androidx.ui.test.manifest)

    androidTestImplementation(project(":core:testing"))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
}
