package com.jiahan.smartcamera.preview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiahan.smartcamera.Screen
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.note.NoteHandler
import com.jiahan.smartcamera.util.ErrorHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface NotePreviewUiState {
    data object Loading : NotePreviewUiState
    data class Success(val note: HomeNote) : NotePreviewUiState
    data class Error(val message: String) : NotePreviewUiState
}

@HiltViewModel
class NotePreviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val noteRepository: NoteRepository,
    private val noteHandler: NoteHandler,
    private val errorHandler: ErrorHandler
) : ViewModel() {

    private val documentPath: String = checkNotNull(savedStateHandle[Screen.NotePreview.ID_ARG])

    private val _uiState = MutableStateFlow<NotePreviewUiState>(NotePreviewUiState.Loading)
    val uiState = _uiState.asStateFlow()
    private val _noteToDelete = MutableStateFlow<HomeNote?>(null)
    val noteToDelete = _noteToDelete.asStateFlow()

    init {
        loadNote(documentPath)
    }

    private fun loadNote(documentPath: String) {
        viewModelScope.launch {
            _uiState.value = NotePreviewUiState.Loading
            noteRepository.getNote(documentPath)
                .onSuccess { _uiState.value = NotePreviewUiState.Success(it) }
                .onFailure { e ->
                    errorHandler.logError(e)
                    _uiState.value = NotePreviewUiState.Error(errorHandler.getErrorMessage(e))
                }
        }
    }

    fun deleteNote(documentPath: String) {
        viewModelScope.launch {
            noteRepository.deleteNote(documentPath)
                .onSuccess { noteHandler.notifyNoteDeleted(documentPath) }
                .onFailure { e -> errorHandler.logError(e) }
        }
    }

    fun favoriteNote(homeNote: HomeNote) {
        viewModelScope.launch {
            noteRepository.favoriteNote(homeNote)
                .onSuccess {
                    val toggled = homeNote.copy(favorite = homeNote.favorite.not())
                    _uiState.value = NotePreviewUiState.Success(toggled)
                    noteHandler.notifyNoteFavorited(toggled)
                }
                .onFailure { e -> errorHandler.logError(e) }
        }
    }

    fun setNoteToDelete(note: HomeNote?) {
        _noteToDelete.value = note
    }
}