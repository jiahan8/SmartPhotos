package com.jiahan.smartcamera.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiahan.smartcamera.data.repository.AnalyticsRepository
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.note.NoteActionsDelegate
import com.jiahan.smartcamera.note.NoteHandler
import com.jiahan.smartcamera.note.NoteShareDelegate
import com.jiahan.smartcamera.util.AppConstants.DEBOUNCE_MS
import com.jiahan.smartcamera.util.AppConstants.STATEFLOW_WHILE_SUBSCRIBED_MS
import com.jiahan.smartcamera.util.ErrorHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
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
) {
    val notes: List<HomeNote>?
        get() = (content as? SearchContent.Success)?.notes
}

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val analyticsRepository: AnalyticsRepository,
    noteHandler: NoteHandler,
    private val noteActions: NoteActionsDelegate,
    private val noteShare: NoteShareDelegate,
    private val errorHandler: ErrorHandler
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState = _uiState.asStateFlow()
    val actionError = noteActions.actionError
    val shareEvent = noteShare.shareEvent

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
        noteHandler.observeNoteMutations(viewModelScope) { transform -> updateSuccessNotes(transform) }
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
            if (noteActions.deleteNote(documentPath)) {
                updateSuccessNotes { it.filter { note -> note.documentPath != documentPath } }
            }
        }
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

    private fun updateSuccessNotes(transform: (List<HomeNote>) -> List<HomeNote>) {
        val notes = _uiState.value.notes ?: return
        _uiState.update { it.copy(content = SearchContent.Success(transform(notes))) }
    }
}