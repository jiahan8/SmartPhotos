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
 * [HiltEditNoteViewModel] reads it back with `savedStateHandle.toRoute<EditNoteRoute>()` and hands
 * [noteId] to the shared `EditNoteViewModel`. [noteId] is the argument name Navigation serializes,
 * so renaming it changes the generated route pattern; nothing on the JVM decodes this route any
 * more, and `SmartPhotosNavigationTest.editNote_composesWithItsHiltBuiltViewModel` is what would
 * catch it.
 */
@Serializable
data class EditNoteRoute(val noteId: String)