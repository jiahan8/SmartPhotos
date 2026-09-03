package com.jiahan.smartcamera.note

import kotlinx.serialization.Serializable

/**
 * Navigation route for [NoteScreen]. Routes live in the feature package that owns them rather than
 * in one central hierarchy -- see `smartPhotosNavGraph`.
 */
@Serializable
data object NoteRoute

/**
 * Navigation route for [EditNoteScreen].
 *
 * [EditNoteViewModel] reads it back with `savedStateHandle.toRoute<EditNoteRoute>()`, so [noteId]
 * is the argument name Navigation serializes -- renaming it changes the generated route pattern
 * and the key `EditNoteViewModelTest` builds its `SavedStateHandle` with.
 */
@Serializable
data class EditNoteRoute(val noteId: String)