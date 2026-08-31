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
    private val noteErrorReporter: NoteErrorReporter
) {
    val actionError = noteErrorReporter.actionError

    // Both writes go through to the `notes` table, so every screen observing the mirror sees them.
    // That is what retired the NoteHandler emissions this used to make on success.
    suspend fun deleteNote(noteId: String): Boolean =
        noteRepository.deleteNote(noteId)
            .onFailure { e -> noteErrorReporter.reportError(e) }
            .isSuccess

    suspend fun favoriteNote(homeNote: HomeNote): Boolean =
        noteRepository.favoriteNote(homeNote)
            .onFailure { e -> noteErrorReporter.reportError(e) }
            .isSuccess
}