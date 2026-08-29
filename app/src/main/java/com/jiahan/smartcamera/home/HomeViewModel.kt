package com.jiahan.smartcamera.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.data.repository.RemoteConfigRepository
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.domain.NoteCursor
import com.jiahan.smartcamera.note.NoteActionsDelegate
import com.jiahan.smartcamera.note.NoteHandler
import com.jiahan.smartcamera.note.NoteShareDelegate
import com.jiahan.smartcamera.util.AppConstants.DEFAULT_PAGE_SIZE
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.ErrorTag
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
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
    val noteToDelete: HomeNote? = null,
    val isExploreIconVisible: Boolean = false
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
    private val errorHandler: ErrorHandler,
    private val remoteConfigRepository: RemoteConfigRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()
    val actionError = noteActions.actionError
    val shareEvent = noteShare.shareEvent

    private val pageSize = DEFAULT_PAGE_SIZE
    private var nextCursor: NoteCursor? = null
    private var hasMoreData = true
    private var reloadJob: Job? = null
    private var loadMoreJob: Job? = null

    init {
        reload(showRefreshIndicator = false)
        viewModelScope.launch {
            noteHandler.noteAddedEvent.collect { reload(showRefreshIndicator = false) }
        }
        noteHandler.observeNoteMutations(viewModelScope) { transform -> updateSuccessNotes(transform) }
        viewModelScope.launch {
            remoteConfigRepository.observeExploreIconVisible().collect { visible ->
                _uiState.update { it.copy(isExploreIconVisible = visible) }
            }
        }
    }

    fun logImageLoadError(throwable: Throwable) {
        errorHandler.logError(throwable, tag = ErrorTag.IMAGE_LOAD)
    }

    fun refresh() {
        reload(showRefreshIndicator = true)
    }

    /**
     * Rebuilds the feed from the first page — the one path that resets [nextCursor], so every
     * caller that wants a fresh list goes through it.
     *
     * A page load still in flight is cancelled first: it was issued against a cursor this reset
     * invalidates, so letting it land would splice a stale window into the new list.
     */
    private fun reload(showRefreshIndicator: Boolean) {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            loadMoreJob?.cancelAndJoin()
            _uiState.update {
                it.copy(isRefreshing = showRefreshIndicator, isLoadingMore = false)
            }
            fetchNotes(initialLoading = true)
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    private suspend fun fetchNotes(initialLoading: Boolean) {
        if (initialLoading) {
            if (!_uiState.value.isRefreshing) {
                _uiState.update { it.copy(content = HomeContent.Loading) }
            }
            nextCursor = null
            hasMoreData = true
        }
        if (!hasMoreData) return

        noteRepository.getNotes(cursor = nextCursor, pageSize = pageSize)
            .onSuccess { notePage ->
                val prev = if (initialLoading) emptyList()
                else _uiState.value.notes ?: emptyList()
                _uiState.update { it.copy(content = HomeContent.Success(prev + notePage.notes)) }
                nextCursor = notePage.nextCursor
                hasMoreData = notePage.hasMore
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
        if (reloadJob?.isActive == true) return
        if (_uiState.value.isLoadingMore || !hasMoreData) return

        loadMoreJob = viewModelScope.launch {
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