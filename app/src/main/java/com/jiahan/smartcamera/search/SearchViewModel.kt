package com.jiahan.smartcamera.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.domain.HomeNote
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
) : ViewModel() {

    private val _notes = MutableStateFlow<List<HomeNote>>(emptyList())
    val notes: StateFlow<List<HomeNote>> = _notes
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    val refreshing = _isRefreshing.asStateFlow()
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()
    private val _noteToDelete = MutableStateFlow<HomeNote?>(null)
    val noteToDelete: StateFlow<HomeNote?> = _noteToDelete.asStateFlow()

    init {
        _notes.value = emptyList()
        viewModelScope.launch {
            searchQuery
                .debounce(300)
                .collect { query ->
                    if (query.isBlank()) {
                        _notes.value = emptyList()
                    } else {
                        searchNotes()
                    }
                }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    suspend fun searchNotes() {
        try {
            _isLoading.value = true
            _notes.value = noteRepository.searchNotes(
                query = _searchQuery.value,
            )
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            _isLoading.value = false
        }
    }

    fun deleteNote(documentPath: String) {
        viewModelScope.launch {
            try {
                noteRepository.deleteNote(documentPath)
                _notes.value = _notes.value.filter { it.documentPath != documentPath }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun favoriteNote(homeNote: HomeNote) {
        viewModelScope.launch {
            try {
                noteRepository.favoriteNote(homeNote)
                _notes.value = _notes.value.map { note ->
                    if (homeNote.documentPath == note.documentPath) {
                        note.copy(favorite = !note.favorite)
                    } else {
                        note
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
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