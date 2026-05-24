package com.jiahan.smartcamera.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.note.NoteHandler
import com.jiahan.smartcamera.util.AppConstants.DEFAULT_PAGE_SIZE
import com.jiahan.smartcamera.util.ErrorHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(val notes: List<HomeNote>) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val noteHandler: NoteHandler,
    private val errorHandler: ErrorHandler
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState = _uiState.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()
    private val _noteToDelete = MutableStateFlow<HomeNote?>(null)
    val noteToDelete = _noteToDelete.asStateFlow()

    private var currentPage = 0
    private val pageSize = DEFAULT_PAGE_SIZE
    private var hasMoreData = true

    init {
        viewModelScope.launch { fetchNotes(initialLoading = true) }
        viewModelScope.launch { noteHandler.noteAddedEvent.collect { fetchNotes(initialLoading = true) } }
        viewModelScope.launch {
            noteHandler.noteDeletedEvent.collect { documentPath ->
                updateSuccessNotes { it.filter { note -> note.documentPath != documentPath } }
            }
        }
        viewModelScope.launch {
            noteHandler.noteFavoritedEvent.collect { updatedNote ->
                updateSuccessNotes { notes ->
                    notes.map { if (it.documentPath == updatedNote.documentPath) it.copy(favorite = updatedNote.favorite) else it }
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            fetchNotes(initialLoading = true)
            _isRefreshing.value = false
        }
    }

    private suspend fun fetchNotes(initialLoading: Boolean) {
        if (initialLoading) {
            if (!_isRefreshing.value) {
                _uiState.value = HomeUiState.Loading
            }
            currentPage = 0
            hasMoreData = true
        }
        if (!hasMoreData) return

        noteRepository.getNotes(page = currentPage, pageSize = pageSize)
            .onSuccess { result ->
                val prev = if (initialLoading) emptyList()
                else (_uiState.value as? HomeUiState.Success)?.notes ?: emptyList()
                _uiState.value = HomeUiState.Success(prev + result)
                hasMoreData = result.size >= pageSize
                currentPage++
            }
            .onFailure { e ->
                errorHandler.logError(e)
                if (initialLoading) {
                    _uiState.value = HomeUiState.Error(errorHandler.getErrorMessage(e))
                }
            }
    }

    fun loadMoreNotes() {
        if (_isLoadingMore.value || !hasMoreData) return

        viewModelScope.launch {
            _isLoadingMore.value = true
            fetchNotes(initialLoading = false)
            _isLoadingMore.value = false
        }
    }

    fun deleteNote(documentPath: String) {
        viewModelScope.launch {
            noteRepository.deleteNote(documentPath)
                .onSuccess {
                    updateSuccessNotes { it.filter { note -> note.documentPath != documentPath } }
                    noteHandler.notifyNoteDeleted(documentPath)
                }
                .onFailure { e -> errorHandler.logError(e) }
        }
    }

    fun favoriteNote(homeNote: HomeNote) {
        viewModelScope.launch {
            noteRepository.favoriteNote(homeNote)
                .onSuccess {
                    noteHandler.notifyNoteFavorited(homeNote.copy(favorite = homeNote.favorite.not()))
                }
                .onFailure { e -> errorHandler.logError(e) }
        }
    }


    fun setNoteToDelete(note: HomeNote?) {
        _noteToDelete.value = note
    }

    private fun updateSuccessNotes(transform: (List<HomeNote>) -> List<HomeNote>) {
        val current = _uiState.value as? HomeUiState.Success ?: return
        _uiState.value = current.copy(notes = transform(current.notes))
    }
}