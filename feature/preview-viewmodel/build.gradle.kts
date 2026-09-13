/*
 * Kotlin Multiplatform half of :feature:preview: NotePreviewViewModel and its suite.
 *
 * The last of the four note screens' ViewModels to move, in :feature:note-viewmodel's shape: its
 * one tie beyond Hilt was reading `NotePreviewRoute` back through `SavedStateHandle.toRoute`, and
 * HiltNotePreviewViewModel does that now and passes the `noteId` down, so the convention supplies
 * every dependency here too.
 *
 * PhotoPreviewViewModel and VideoPreviewViewModel are not here: both hold `android.net.Uri` for a
 * local media source.
 */
plugins {
    id("smartphotos.kmp.viewmodel")
}
