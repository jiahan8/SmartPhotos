package com.jiahan.smartcamera.preview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.note.NoteActionsDelegate
import com.jiahan.smartcamera.note.NoteHandler
import com.jiahan.smartcamera.note.NoteShareDelegate
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.ErrorTag
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface NotePreviewContent {
    data object Loading : NotePreviewContent
    data class Success(val note: HomeNote) : NotePreviewContent
    data class Error(val message: String) : NotePreviewContent
}

data class NotePreviewUiState(
    val content: NotePreviewContent = NotePreviewContent.Loading,
    val noteToDelete: HomeNote? = null
)

@HiltViewModel
class NotePreviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val noteRepository: NoteRepository,
    private val noteHandler: NoteHandler,
    noteActions: NoteActionsDelegate,
    private val errorHandler: ErrorHandler,
    private val noteShare: NoteShareDelegate
) : ViewModel() {

    private val noteId: String = savedStateHandle.toRoute<NotePreviewRoute>().id

    private val _uiState = MutableStateFlow(NotePreviewUiState())
    val uiState = _uiState.asStateFlow()
    private val _actionError = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val actionError = merge(_actionError, noteActions.actionError)
    val shareEvent = noteShare.shareEvent

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(content = NotePreviewContent.Loading) }
            noteRepository.getNote(noteId)
                .onSuccess { note ->
                    _uiState.update { it.copy(content = NotePreviewContent.Success(note)) }
                }
                .onFailure { e ->
                    errorHandler.logError(e)
                    _uiState.update {
                        it.copy(content = NotePreviewContent.Error(errorHandler.getErrorMessage(e)))
                    }
                }
        }

        // Reflects an edit made on EditNoteScreen once the user navigates back here, since this
        // ViewModel/screen instance stays on the back stack rather than reloading.
        viewModelScope.launch {
            noteHandler.noteUpdatedEvent.collect { updatedNote ->
                if (updatedNote.noteId == noteId) {
                    _uiState.update { it.copy(content = NotePreviewContent.Success(updatedNote)) }
                }
            }
        }
    }

    fun logImageLoadError(throwable: Throwable) {
        errorHandler.logError(throwable, tag = ErrorTag.IMAGE_LOAD)
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            noteRepository.deleteNote(noteId)
                .onSuccess { noteHandler.notifyNoteDeleted(noteId) }
                .onFailure { e ->
                    errorHandler.logError(e)
                    _actionError.tryEmit(errorHandler.getErrorMessage(e))
                }
        }
    }

    fun favoriteNote(homeNote: HomeNote) {
        viewModelScope.launch {
            noteRepository.favoriteNote(homeNote)
                .onSuccess {
                    val toggled = homeNote.copy(favorite = homeNote.favorite.not())
                    _uiState.update { it.copy(content = NotePreviewContent.Success(toggled)) }
                    noteHandler.notifyNoteFavorited(toggled)
                }
                .onFailure { e ->
                    errorHandler.logError(e)
                    _actionError.tryEmit(errorHandler.getErrorMessage(e))
                }
        }
    }

    fun setNoteToDelete(note: HomeNote?) {
        _uiState.update { it.copy(noteToDelete = note) }
    }

    fun shareNote(note: HomeNote) {
        viewModelScope.launch { noteShare.shareNote(note) }
    }
}