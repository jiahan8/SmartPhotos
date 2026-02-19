package com.jiahan.smartcamera.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.data.repository.RemoteConfigRepository
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.note.NoteHandler
import com.jiahan.smartcamera.util.AppConstants.DEFAULT_PAGE_SIZE
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val remoteConfigRepository: RemoteConfigRepository,
    private val noteRepository: NoteRepository,
    private val noteHandler: NoteHandler
) : ViewModel() {

    private val _notes = MutableStateFlow<List<HomeNote>>(emptyList())
    val notes = _notes.asStateFlow()
    private val _isInitialLoading = MutableStateFlow(true)
    val isInitialLoading = _isInitialLoading.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    val refreshing = _isRefreshing.asStateFlow()
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()
    private val _noteToDelete = MutableStateFlow<HomeNote?>(null)
    val noteToDelete = _noteToDelete.asStateFlow()

    private var currentPage = 0
    private val pageSize = DEFAULT_PAGE_SIZE
    private var hasMoreData = true

    private var isHandlingLocalFavoriteAction = false

    init {
        viewModelScope.launch {
            remoteConfigRepository.fetchAndActivateConfig()
            fetchNotes(initialLoading = true)
            noteHandler.noteAddedEvent.collect {
                fetchNotes(initialLoading = true)
            }
        }
        viewModelScope.launch {
            noteHandler.noteDeletedEvent.collect { documentPath ->
                _notes.value = _notes.value.filter { it.documentPath != documentPath }
            }
        }
        viewModelScope.launch {
            noteHandler.noteFavoritedEvent.collect { updatedNote ->
                if (!isHandlingLocalFavoriteAction) {
                    _notes.value = _notes.value.map { note ->
                        if (updatedNote.documentPath == note.documentPath) {
                            note.copy(favorite = note.favorite.not())
                        } else {
                            note
                        }
                    }
                }
            }
        }
    }

    suspend fun fetchNotes(initialLoading: Boolean) {
        try {
            if (initialLoading) {
                _isInitialLoading.value = true
                currentPage = 0
                hasMoreData = true
            }

            if (!hasMoreData) return

            val result = noteRepository.getNotes(page = currentPage, pageSize = pageSize)
            _notes.value = if (initialLoading) result else _notes.value + result

            hasMoreData = result.size >= pageSize
            currentPage++
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            _isInitialLoading.value = false
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
            try {
                noteRepository.deleteNote(documentPath)
                _notes.value = _notes.value.filter { it.documentPath != documentPath }
                noteHandler.notifyNoteDeleted(documentPath)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun favoriteNote(homeNote: HomeNote) {
        viewModelScope.launch {
            try {
                isHandlingLocalFavoriteAction = true
                noteRepository.favoriteNote(homeNote)
                _notes.value = _notes.value.map { note ->
                    if (homeNote.documentPath == note.documentPath) {
                        note.copy(favorite = note.favorite.not())
                    } else {
                        note
                    }
                }
                noteHandler.notifyNoteFavorited(homeNote.copy(favorite = homeNote.favorite.not()))
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isHandlingLocalFavoriteAction = false
            }
        }
    }

    fun setRefreshing(refreshing: Boolean) {
        _isRefreshing.value = refreshing
    }

    fun setNoteToDelete(note: HomeNote?) {
        _noteToDelete.value = note
    }
}