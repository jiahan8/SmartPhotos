package com.jiahan.smartcamera.preview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.note.NoteActionsDelegate
import com.jiahan.smartcamera.note.NoteShareDelegate
import com.jiahan.smartcamera.util.AppConstants.STATEFLOW_WHILE_SUBSCRIBED_MS
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.ErrorTag
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface NotePreviewContent {
    data object Loading : NotePreviewContent
    data class Success(val note: HomeNote) : NotePreviewContent
    data class Error(val message: String) : NotePreviewContent
}

data class NotePreviewUiState(
    val noteToDelete: HomeNote? = null
)

/** As on Home: it decides only what a missing row means, never what a present one does. */
private sealed interface FetchStatus {
    data object Pending : FetchStatus
    data object Settled : FetchStatus
    data class Failed(val message: String) : FetchStatus
}

@HiltViewModel
class NotePreviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val noteRepository: NoteRepository,
    private val noteActions: NoteActionsDelegate,
    private val errorHandler: ErrorHandler,
    private val noteShare: NoteShareDelegate
) : ViewModel() {

    private val noteId: String = savedStateHandle.toRoute<NotePreviewRoute>().id

    private val _uiState = MutableStateFlow(NotePreviewUiState())
    val uiState = _uiState.asStateFlow()
    val actionError = noteActions.actionError
    val shareEvent = noteShare.shareEvent

    private val fetchStatus = MutableStateFlow<FetchStatus>(FetchStatus.Pending)

    /**
     * The note, read from the mirror. `getNote` fetches it and writes it through; every later edit
     * and favorite toggle lands in the same row, so this screen reflects them while sitting on the
     * back stack -- what it used to collect `noteUpdatedEvent` to do, and why favoriting no longer
     * patches the state here by hand.
     *
     * A null row after a successful fetch means the note was just deleted from this screen, which
     * navigates back as it deletes: [NotePreviewContent.Loading] rather than an error, so the
     * screen does not flash a failure on its way off the stack.
     */
    val content: StateFlow<NotePreviewContent> =
        combine(noteRepository.getNoteStream(noteId), fetchStatus) { note, status ->
            when {
                note != null -> NotePreviewContent.Success(note)
                status is FetchStatus.Failed -> NotePreviewContent.Error(status.message)
                else -> NotePreviewContent.Loading
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATEFLOW_WHILE_SUBSCRIBED_MS),
            initialValue = NotePreviewContent.Loading
        )

    init {
        viewModelScope.launch {
            noteRepository.getNote(noteId)
                .onSuccess { fetchStatus.value = FetchStatus.Settled }
                .onFailure { e ->
                    errorHandler.logError(e)
                    fetchStatus.value = FetchStatus.Failed(errorHandler.getErrorMessage(e))
                }
        }
    }

    fun logImageLoadError(throwable: Throwable) {
        errorHandler.logError(throwable, tag = ErrorTag.IMAGE_LOAD)
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch { noteActions.deleteNote(noteId) }
    }

    fun favoriteNote(homeNote: HomeNote) {
        viewModelScope.launch { noteActions.favoriteNote(homeNote) }
    }

    fun setNoteToDelete(note: HomeNote?) {
        _uiState.update { it.copy(noteToDelete = note) }
    }

    fun shareNote(note: HomeNote) {
        viewModelScope.launch { noteShare.shareNote(note) }
    }
}