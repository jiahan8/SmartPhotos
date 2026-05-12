package com.jiahan.smartcamera.favorite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiahan.smartcamera.data.repository.AnalyticsRepository
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.note.NoteHandler
import com.jiahan.smartcamera.util.AppConstants.DEBOUNCE_MS
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class FavoriteViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val noteHandler: NoteHandler
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()
    private val _isSyncing = MutableStateFlow(false)
    private val _isRefreshing = MutableStateFlow(false)
    val refreshing = _isRefreshing.asStateFlow()
    val isLoadingMore = MutableStateFlow(false).asStateFlow()
    private val _noteToDelete = MutableStateFlow<HomeNote?>(null)
    val noteToDelete = _noteToDelete.asStateFlow()

    val notes = _searchQuery
        .debounce(DEBOUNCE_MS)
        .flatMapLatest { query -> noteRepository.getFavoriteNotesStream(query) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    val isLoading = combine(_isSyncing, notes) { syncing, notesList ->
        syncing && notesList.isEmpty()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true,
    )

    init {
        viewModelScope.launch { syncNotes() }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            syncNotes()
            _isRefreshing.value = false
        }
    }

    private suspend fun syncNotes() {
        try {
            _isSyncing.value = true
            noteRepository.syncFavoriteNotes()
            analyticsRepository.logFavoriteSearchCustomEvent(_searchQuery.value)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            _isSyncing.value = false
        }
    }

    fun deleteNote(documentPath: String) {
        viewModelScope.launch {
            try {
                noteRepository.deleteNote(documentPath)
                noteHandler.notifyNoteDeleted(documentPath)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun favoriteNote(homeNote: HomeNote) {
        viewModelScope.launch {
            try {
                noteRepository.favoriteNote(homeNote)
                noteHandler.notifyNoteFavorited(homeNote.copy(favorite = homeNote.favorite.not()))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setNoteToDelete(note: HomeNote?) {
        _noteToDelete.value = note
    }
}