/*
 * Kotlin Multiplatform half of :feature:preview: the three preview ViewModels and their suites.
 *
 * NotePreviewViewModel came first, the last of the four note screens' ViewModels to move, in
 * :feature:note-viewmodel's shape: its one tie beyond Hilt was reading `NotePreviewRoute` back
 * through `SavedStateHandle.toRoute`, and HiltNotePreviewViewModel does that now and passes the
 * `noteId` down, so the convention supplies every dependency here too.
 *
 * PhotoPreviewViewModel and VideoPreviewViewModel followed once their local source stopped being an
 * `android.net.Uri`: a `MediaUri` now, which the screens resolve with `toPlatformUri()`. Their
 * subclasses decode the route into a PhotoSource or VideoSource, so MediaSourceType stays beside the
 * routes in :feature:preview and this module still needs no serialization plugin.
 */
plugins {
    id("smartphotos.kmp.viewmodel")
}
