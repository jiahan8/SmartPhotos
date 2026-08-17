package com.jiahan.smartcamera.note

import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.util.ErrorHandler
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject

/**
 * Scoped per ViewModel (not just per constructor param): [NoteShareDelegate] also depends on this
 * class, so without [ViewModelScoped] a ViewModel that injects both directly and via
 * [NoteShareDelegate] would get two separate instances with two separate [actionError] flows,
 * silently dropping [reportError] calls made through the [NoteShareDelegate]-owned instance.
 */
@ViewModelScoped
class NoteActionsDelegate @Inject constructor(
    private val noteRepository: NoteRepository,
    private val noteHandler: NoteHandler,
    private val errorHandler: ErrorHandler
) {
    private val _actionError = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val actionError = _actionError.asSharedFlow()

    fun reportError(message: String) {
        _actionError.tryEmit(message)
    }

    suspend fun deleteNote(noteId: String): Boolean =
        noteRepository.deleteNote(noteId)
            .onSuccess { noteHandler.notifyNoteDeleted(noteId) }
            .onFailure { e ->
                errorHandler.logError(e)
                _actionError.tryEmit(errorHandler.getErrorMessage(e))
            }
            .isSuccess

    suspend fun favoriteNote(homeNote: HomeNote): Boolean =
        noteRepository.favoriteNote(homeNote)
            .onSuccess { noteHandler.notifyNoteFavorited(homeNote.copy(favorite = homeNote.favorite.not())) }
            .onFailure { e ->
                errorHandler.logError(e)
                _actionError.tryEmit(errorHandler.getErrorMessage(e))
            }
            .isSuccess
}