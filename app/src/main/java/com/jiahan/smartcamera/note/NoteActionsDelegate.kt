package com.jiahan.smartcamera.note

import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.util.ErrorHandler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject

class NoteActionsDelegate @Inject constructor(
    private val noteRepository: NoteRepository,
    private val noteHandler: NoteHandler,
    private val errorHandler: ErrorHandler
) {
    private val _actionError = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val actionError = _actionError.asSharedFlow()

    suspend fun deleteNote(documentPath: String): Boolean =
        noteRepository.deleteNote(documentPath)
            .onSuccess { noteHandler.notifyNoteDeleted(documentPath) }
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