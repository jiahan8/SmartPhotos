package com.jiahan.smartcamera.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.note.NoteActionsDelegate
import com.jiahan.smartcamera.note.NoteHandler
import com.jiahan.smartcamera.note.NoteShareDelegate
import com.jiahan.smartcamera.util.AppConstants.DEFAULT_PAGE_SIZE
import com.jiahan.smartcamera.util.ErrorHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface HomeContent {
    data object Loading : HomeContent
    data class Success(val notes: List<HomeNote>) : HomeContent
    data class Error(val message: String) : HomeContent
}

data class HomeUiState(
    val content: HomeContent = HomeContent.Loading,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val noteToDelete: HomeNote? = null
) {
    val notes: List<HomeNote>?
        get() = (content as? HomeContent.Success)?.notes
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val noteHandler: NoteHandler,
    private val noteActions: NoteActionsDelegate,
    private val noteShare: NoteShareDelegate,
    private val errorHandler: ErrorHandler
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()
    val actionError = noteActions.actionError
    val shareEvent = noteShare.shareEvent

    private var currentPage = 0
    private val pageSize = DEFAULT_PAGE_SIZE
    private var hasMoreData = true

    init {
        viewModelScope.launch { fetchNotes(initialLoading = true) }
        viewModelScope.launch { noteHandler.noteAddedEvent.collect { fetchNotes(initialLoading = true) } }
        noteHandler.observeNoteMutations(viewModelScope) { transform -> updateSuccessNotes(transform) }
    }

    fun logImageLoadError(throwable: Throwable) {
        errorHandler.logError(throwable, tag = "ImageLoad")
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            fetchNotes(initialLoading = true)
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    private suspend fun fetchNotes(initialLoading: Boolean) {
        if (initialLoading) {
            if (!_uiState.value.isRefreshing) {
                _uiState.update { it.copy(content = HomeContent.Loading) }
            }
            currentPage = 0
            hasMoreData = true
        }
        if (!hasMoreData) return

        noteRepository.getNotes(page = currentPage, pageSize = pageSize)
            .onSuccess { result ->
                val prev = if (initialLoading) emptyList()
                else _uiState.value.notes ?: emptyList()
                _uiState.update { it.copy(content = HomeContent.Success(prev + result)) }
                hasMoreData = result.size >= pageSize
                currentPage++
            }
            .onFailure { e ->
                errorHandler.logError(e)
                if (initialLoading) {
                    _uiState.update {
                        it.copy(content = HomeContent.Error(errorHandler.getErrorMessage(e)))
                    }
                }
            }
    }

    fun loadMoreNotes() {
        if (_uiState.value.isLoadingMore || !hasMoreData) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            fetchNotes(initialLoading = false)
            _uiState.update { it.copy(isLoadingMore = false) }
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            if (noteActions.deleteNote(noteId)) {
                updateSuccessNotes { it.filter { note -> note.noteId != noteId } }
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
        _uiState.update { it.copy(content = HomeContent.Success(transform(notes))) }
    }
}