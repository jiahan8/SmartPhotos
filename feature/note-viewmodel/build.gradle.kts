/*
 * Kotlin Multiplatform half of :feature:note: EditNoteViewModel, NoteViewModel, IncomingShare and
 * the two ViewModels' suites.
 *
 * EditNoteViewModel was the fourth ViewModel moved to `commonMain`, in the shape
 * :feature:explore-viewmodel's build file and ARCHITECTURE.md's Kotlin Multiplatform section
 * describe. Its one tie beyond Hilt was reading `EditNoteRoute` back through
 * `SavedStateHandle.toRoute`; HiltEditNoteViewModel does that now and passes the `noteId` down.
 *
 * NoteViewModel followed once the media it picks and captures stopped being `android.net.Uri`: the
 * capture destination comes from MediaCaptureRepository as a MediaUri, and NoteScreen converts at
 * each launcher. Its pending share arrives as a constructor argument, taken off IncomingShareHandler
 * by HiltNoteViewModel, so IncomingShare lives here and the handler does not. The convention still
 * supplies every dependency.
 */
plugins {
    id("smartphotos.kmp.viewmodel")
}
