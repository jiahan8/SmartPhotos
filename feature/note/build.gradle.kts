/*
 * The ninth and last feature module: the note composer, the editor, their ViewModels, their routes,
 * `IncomingShareHandler`, and both instrumented suites.
 *
 * This is the one package that had real work left in it rather than just a build file, and the work
 * was the same shape as :feature:auth's: `noteErrorMessageResId` read a structured `details.reason`
 * payload off a `FirebaseFunctionsException` in the ViewModel layer, so moving it here would have
 * put `firebase-functions` on a feature module's classpath. It was **deleted rather than moved** --
 * `DefaultNoteRepository` folds those reasons into `AppError.NoteTextTooLong`,
 * `NoteMediaLimitExceeded` and `NoteEmpty`, and `getErrorMessage` renders them. Two ViewModel call
 * sites shrank to a plain `getErrorMessage`, and the three strings went *down* to :core:common,
 * where `appErrorMessageResId` in :app and this module's own client-side length checks can both
 * see them -- exactly why `username_not_available` is already there.
 *
 * All fourteen of its remaining strings were exclusive and travelled. `IncomingShareHandler` came
 * with them and is read downward by :app's `AppModule` and `MainViewModel`, which is the same shape
 * as :feature:search's deep-link constant: the handler belongs to the screen that consumes the
 * share, and :app is the one that receives the intent.
 */
plugins {
    id("smartphotos.android.feature")
    // NoteRoute and EditNoteRoute are @Serializable. Left out of the feature convention
    // deliberately -- see the note in :feature:settings.
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.jiahan.smartcamera.feature.note"
}

dependencies {

    /*
     * :core:domain, :core:ui, Compose, icons, Hilt, lifecycle, :core:testing, junit, mockk and
     * kotlinx-coroutines-test all arrive from `smartphotos.android.feature`.
     */

    // MediaFileRepository and toMediaUri() for the picked media, plus the three note-validation
    // strings that appErrorMessageResId also reads.
    implementation(project(":core:common"))

    // The photo-picker and camera launchers NoteScreen holds.
    implementation(libs.androidx.activity.compose)
    // ContextCompat.checkSelfPermission, for the camera permission check.
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.core)

    // toRoute<EditNoteRoute>() in EditNoteViewModel.
    implementation(libs.androidx.navigation.compose)

    // AsyncImage, for the picked-media thumbnails.
    implementation(libs.coil.compose)

    // Both ViewModel tests assert on flows with no `.value` to read.
    testImplementation(libs.turbine)
    // EditNoteViewModelTest is Robolectric-backed: toRoute() builds a real Bundle.
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
