package com.jiahan.smartcamera.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiahan.smartcamera.data.repository.AnalyticsRepository
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.note.NoteHandler
import com.jiahan.smartcamera.util.AppConstants.DEBOUNCE_MS
import com.jiahan.smartcamera.util.AppConstants.STATEFLOW_WHILE_SUBSCRIBED_MS
import com.jiahan.smartcamera.util.ErrorHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

sealed interface SearchContent {
    data object Idle : SearchContent
    data object Loading : SearchContent
    data class Success(val notes: List<HomeNote>) : SearchContent
    data class Error(val message: String) : SearchContent
}

data class SearchUiState(
    val content: SearchContent = SearchContent.Idle,
    val searchQuery: String = "",
    val isRefreshing: Boolean = false,
    val noteToDelete: HomeNote? = null
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val noteHandler: NoteHandler,
    private val errorHandler: ErrorHandler
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState = _uiState.asStateFlow()
    private val _actionError = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val actionError = _actionError.asSharedFlow()

    val searchQuery = _uiState
        .map { it.searchQuery }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATEFLOW_WHILE_SUBSCRIBED_MS), "")

    init {
        viewModelScope.launch {
            searchQuery
                .debounce(DEBOUNCE_MS.milliseconds)
                .collect { query ->
                    if (query.isBlank()) {
                        _uiState.update { it.copy(content = SearchContent.Idle) }
                    } else {
                        searchNotes(query)
                    }
                }
        }
        viewModelScope.launch {
            noteHandler.noteDeletedEvent.collect { documentPath ->
                updateSuccessNotes { it.filter { note -> note.documentPath != documentPath } }
            }
        }
        viewModelScope.launch {
            noteHandler.noteFavoritedEvent.collect { updatedNote ->
                updateSuccessNotes { notes ->
                    notes.map {
                        if (it.documentPath == updatedNote.documentPath) it.copy(favorite = updatedNote.favorite) else it
                    }
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            searchNotes(_uiState.value.searchQuery)
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    private suspend fun searchNotes(query: String) {
        _uiState.update { it.copy(content = SearchContent.Loading) }
        noteRepository.searchNotes(query = query)
            .onSuccess { results ->
                _uiState.update { it.copy(content = SearchContent.Success(results)) }
                analyticsRepository.logSearchCustomEvent(query)
                analyticsRepository.logSearchEvent(query)
            }
            .onFailure { e ->
                errorHandler.logError(e)
                _uiState.update {
                    it.copy(
                        content = SearchContent.Error(errorHandler.getErrorMessage(e))
                    )
                }
            }
    }

    fun deleteNote(documentPath: String) {
        viewModelScope.launch {
            noteRepository.deleteNote(documentPath)
                .onSuccess {
                    updateSuccessNotes { it.filter { note -> note.documentPath != documentPath } }
                    noteHandler.notifyNoteDeleted(documentPath)
                }
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
                    noteHandler.notifyNoteFavorited(homeNote.copy(favorite = homeNote.favorite.not()))
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

    private fun updateSuccessNotes(transform: (List<HomeNote>) -> List<HomeNote>) {
        val content = _uiState.value.content as? SearchContent.Success ?: return
        _uiState.update { it.copy(content = content.copy(notes = transform(content.notes))) }
    }
}