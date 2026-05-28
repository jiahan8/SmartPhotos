package com.jiahan.smartcamera.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiahan.smartcamera.data.repository.AnalyticsRepository
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.note.NoteHandler
import com.jiahan.smartcamera.util.AppConstants.DEBOUNCE_MS
import com.jiahan.smartcamera.util.ErrorHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Success(val notes: List<HomeNote>) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val noteHandler: NoteHandler,
    private val errorHandler: ErrorHandler
) : ViewModel() {

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState = _uiState.asStateFlow()
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()
    private val _noteToDelete = MutableStateFlow<HomeNote?>(null)
    val noteToDelete = _noteToDelete.asStateFlow()
    private val _actionError = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val actionError = _actionError.asSharedFlow()

    init {
        viewModelScope.launch {
            searchQuery
                .debounce(DEBOUNCE_MS)
                .collect { query ->
                    if (query.isBlank()) {
                        _uiState.value = SearchUiState.Idle
                    } else {
                        searchNotes()
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
        _searchQuery.value = query
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            searchNotes()
            _isRefreshing.value = false
        }
    }

    private suspend fun searchNotes() {
        _uiState.value = SearchUiState.Loading
        noteRepository.searchNotes(query = _searchQuery.value)
            .onSuccess { results ->
                _uiState.value = SearchUiState.Success(results)
                analyticsRepository.logSearchCustomEvent(_searchQuery.value)
                analyticsRepository.logSearchEvent(_searchQuery.value)
            }
            .onFailure { e ->
                errorHandler.logError(e)
                _uiState.value = SearchUiState.Error(errorHandler.getErrorMessage(e))
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
        _noteToDelete.value = note
    }


    private fun updateSuccessNotes(transform: (List<HomeNote>) -> List<HomeNote>) {
        val current = _uiState.value as? SearchUiState.Success ?: return
        _uiState.value = current.copy(notes = transform(current.notes))
    }
}