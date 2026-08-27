package com.jiahan.smartcamera.note

import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.domain.HomeNote
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject

/**
 * Scoped per ViewModel like [NoteShareDelegate] and [NoteErrorReporter] — see the scoping note on
 * [NoteErrorReporter], whose shared [NoteErrorReporter.actionError] this class exposes.
 */
@ViewModelScoped
class NoteActionsDelegate @Inject constructor(
    private val noteRepository: NoteRepository,
    private val noteHandler: NoteHandler,
    private val noteErrorReporter: NoteErrorReporter
) {
    val actionError = noteErrorReporter.actionError

    suspend fun deleteNote(noteId: String): Boolean =
        noteRepository.deleteNote(noteId)
            .onSuccess { noteHandler.notifyNoteDeleted(noteId) }
            .onFailure { e -> noteErrorReporter.reportError(e) }
            .isSuccess

    suspend fun favoriteNote(homeNote: HomeNote): Boolean =
        noteRepository.favoriteNote(homeNote)
            .onSuccess { noteHandler.notifyNoteFavorited(homeNote.copy(favorite = homeNote.favorite.not())) }
            .onFailure { e -> noteErrorReporter.reportError(e) }
            .isSuccess
}