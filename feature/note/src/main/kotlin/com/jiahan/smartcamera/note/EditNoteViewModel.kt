package com.jiahan.smartcamera.note

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.jiahan.smartcamera.data.repository.AnalyticsRepository
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.util.AppConstants.MAX_POST_TEXT_LENGTH
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.ErrorTag
import com.jiahan.smartcamera.util.ResourceProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.jiahan.smartcamera.core.common.R as CommonR

sealed interface EditNoteContent {
    data object Loading : EditNoteContent
    data class Success(val note: HomeNote) : EditNoteContent
    data class Error(val message: String) : EditNoteContent
}

sealed interface SaveStatus {
    data object Idle : SaveStatus
    data object Saving : SaveStatus
    data object Success : SaveStatus
    data class Error(val message: String) : SaveStatus
}

data class EditNoteUiState(
    val content: EditNoteContent = EditNoteContent.Loading,
    val noteText: String = "",
    val noteTextError: String? = null,
    val showDiscardDialog: Boolean = false,
    val saveStatus: SaveStatus = SaveStatus.Idle
)

// The text an edit would actually persist: trimmed, and null when blank, matching how
// createNote/updateNote store it. Comparing this (rather than the raw field) against the loaded
// note's text is what lets whitespace-only changes count as no change at all.
private val EditNoteUiState.editedText: String?
    get() = noteText.trim().ifBlank { null }

// Whether saving would actually write something different from what the note already holds.
// False until the note has loaded, since there is nothing to compare against yet.
private val EditNoteUiState.isTextChanged: Boolean
    get() {
        val note = (content as? EditNoteContent.Success)?.note ?: return false
        return editedText != note.text
    }

/**
 * Backs the edit-note screen, which changes a note's text and nothing else -- its media is fixed
 * at creation time and shown read-only, so unlike [NoteViewModel] there is no media picking,
 * capture or upload here, and saving is a single updateNote call.
 */
@HiltViewModel
class EditNoteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val noteRepository: NoteRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val resourceProvider: ResourceProvider,
    private val errorHandler: ErrorHandler,
) : ViewModel() {

    private val noteId: String = savedStateHandle.toRoute<EditNoteRoute>().noteId

    private val _uiState = MutableStateFlow(EditNoteUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            noteRepository.getNote(noteId)
                .onSuccess { note ->
                    _uiState.update {
                        it.copy(
                            content = EditNoteContent.Success(note),
                            noteText = note.text.orEmpty()
                        )
                    }
                }
                .onFailure { e ->
                    errorHandler.logError(e)
                    _uiState.update {
                        it.copy(content = EditNoteContent.Error(errorHandler.getErrorMessage(e)))
                    }
                }
        }
    }

    val saveButtonEnabled = _uiState
        .map { state ->
            val note = (state.content as? EditNoteContent.Success)?.note
            val editedText = state.editedText
            // Nothing to save until the note has loaded; a note still needs text or media, since
            // clearing the text of a note that carries no media would leave an empty note behind;
            // and an unchanged text has nothing to write.
            note != null &&
                    state.saveStatus !is SaveStatus.Saving &&
                    state.noteTextError == null &&
                    (editedText != null || !note.mediaList.isNullOrEmpty()) &&
                    state.isTextChanged
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    // Drives the discard confirmation on the way out. Deliberately not the same as
    // saveButtonEnabled: an edit that is too long to save is still an edit worth confirming
    // before it is thrown away.
    val hasUnsavedChanges = _uiState
        .map { state -> state.isTextChanged }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    fun updateNoteText(text: String) {
        analyticsRepository.logEditNoteCustomEvent(text)
        _uiState.update {
            it.copy(
                noteText = text,
                noteTextError = when {
                    text.length > MAX_POST_TEXT_LENGTH ->
                        resourceProvider.getString(CommonR.string.note_validation)

                    else -> null
                }
            )
        }
    }

    fun saveNote() {
        val state = _uiState.value
        val note = (state.content as? EditNoteContent.Success)?.note ?: return
        val updatedNote = note.copy(text = state.editedText)
        viewModelScope.launch {
            _uiState.update { it.copy(saveStatus = SaveStatus.Saving) }
            noteRepository.updateNote(updatedNote)
                .onSuccess {
                    // No event: updateNote writes the new text through to the `notes` table, and
                    // Home, Search, Favorite and NotePreview all render that.
                    _uiState.update { it.copy(saveStatus = SaveStatus.Success) }
                }
                .onFailure { e ->
                    errorHandler.logError(e)
                    // updateNote folds its validation reasons into AppError, and getErrorMessage
                    // renders those -- same message, with the Firebase type left in the data layer.
                    _uiState.update {
                        it.copy(saveStatus = SaveStatus.Error(errorHandler.getErrorMessage(e)))
                    }
                }
        }
    }

    fun setShowDiscardDialog(show: Boolean) {
        _uiState.update { it.copy(showDiscardDialog = show) }
    }

    fun resetSaveStatus() {
        _uiState.update { it.copy(saveStatus = SaveStatus.Idle) }
    }

    fun logImageLoadError(throwable: Throwable) {
        errorHandler.logError(throwable, tag = ErrorTag.IMAGE_LOAD)
    }
}