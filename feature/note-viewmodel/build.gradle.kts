/*
 * Kotlin Multiplatform half of :feature:note: EditNoteViewModel and its suite.
 *
 * The fourth ViewModel moved to `commonMain`, in the shape :feature:explore-viewmodel's build file
 * and ARCHITECTURE.md's Kotlin Multiplatform section describe. Its one tie beyond Hilt was reading
 * `EditNoteRoute` back through `SavedStateHandle.toRoute`; HiltEditNoteViewModel does that now and
 * passes the `noteId` down, so the convention supplies every dependency here too.
 *
 * NoteViewModel is not here: it holds `android.net.Uri` for the media it picks and captures.
 */
plugins {
    id("smartphotos.kmp.viewmodel")
}
