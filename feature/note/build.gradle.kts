/*
 * The ninth and last feature module: the note composer, the editor, their ViewModels, their routes,
 * `IncomingShareHandler`, and both instrumented suites.
 *
 * This is the one package that had real work left in it rather than just a build file, and the work
 * was the same shape as :feature:auth's: `noteErrorMessageResId` read a structured `details.reason`
 * payload off a `FirebaseFunctionsException` in the ViewModel layer, so moving it here would have
 * put `firebase-functions` on a feature module's classpath. It was **deleted rather than moved** --
 * `DefaultNoteRepository` folds those reasons into `AppError.NoteTextTooLong`,
 * `NoteMediaLimitExceeded` and `NoteEmpty`, which the screens render like any other `AppError`. Two
 * ViewModel call sites shrank to a plain `toErrorMessage`, and the three strings went *down* to
 * :core:common, where `appErrorMessageResId` and this module's screens can both see them -- exactly
 * why `username_not_available` is already there.
 *
 * All fourteen of its remaining strings were exclusive and travelled. `IncomingShareHandler` came
 * with them and is read downward by :app's `AppModule` and `MainViewModel`, which is the same shape
 * as :feature:search's deep-link constant: the handler belongs to the screen that consumes the
 * share, and :app is the one that receives the intent.
 *
 * EditNoteViewModel has since moved again, to :feature:note-viewmodel's `commonMain`, the way
 * ExploreViewModel did, and NoteViewModel followed once its capture destination stopped being an
 * `android.net.Uri`. HiltEditNoteViewModel and HiltNoteViewModel are the subclasses Hilt builds, and
 * stay here -- the second is also where `IncomingShareHandler`'s pending share is consumed, since
 * the handler is a Hilt singleton :app posts to.
 */
plugins {
    id("smartphotos.android.feature")
}

android {
    namespace = "com.jiahan.smartcamera.feature.note"

}

dependencies {

    /*
     * :core:domain, :core:ui, the Compose set, icons, Hilt, lifecycle, the serialization plugin and
     * its runtime, and the whole test/androidTest baseline -- :core:testing, junit, mockk,
     * kotlinx-coroutines-test, Turbine and the five on-device lines -- all arrive from
     * `smartphotos.android.feature`. What is left here is what only this feature needs.
     */

    // EditNoteViewModel and NoteViewModel, in this feature's multiplatform half. api for the reason
    // :feature:explore's build file gives for its own: each is its screen's `viewModel` parameter
    // type and a Hilt subclass's supertype, and :app has to resolve both -- and IncomingShare, which
    // :app's MainViewModel builds.
    api(project(":feature:note-viewmodel"))

    // toMediaUri() and toPlatformUri() at NoteScreen's picker and camera launchers, plus the three
    // note-validation strings that appErrorMessageResId also reads.
    implementation(project(":core:common"))

    // The photo-picker and camera launchers NoteScreen holds.
    implementation(libs.androidx.activity.compose)
    // ContextCompat.checkSelfPermission, for the camera permission check.
    implementation(libs.androidx.core.ktx)

    // toRoute<EditNoteRoute>() in HiltEditNoteViewModel, which decodes the route so the shared
    // EditNoteViewModel can take a plain noteId.
    implementation(libs.androidx.navigation.compose)

    // AsyncImage, for the picked-media thumbnails.
    implementation(libs.coil.compose)

    // The two screen suites are Robolectric-backed; the artifacts for that arrive from
    // `smartphotos.android.feature`. EditNoteViewModelTest no longer needs it, having followed its
    // subject into :feature:note-viewmodel's commonTest.
}
