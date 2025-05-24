package com.jiahan.smartcamera.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.database.data.DatabaseNote
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
) : ViewModel() {

    private val _notes = MutableStateFlow<List<DatabaseNote>>(emptyList())
    val notes: StateFlow<List<DatabaseNote>> = _notes
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    val refreshing = _isRefreshing.asStateFlow()

    init {
        viewModelScope.launch {
            fetchNotes()
        }
    }

    suspend fun fetchNotes() {
        try {
            _notes.value = noteRepository.getNotes()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        performSearch()
    }

    private fun performSearch() {
        viewModelScope.launch {
            try {
                val query = _searchQuery.value
                _notes.value = if (query.isEmpty()) {
                    noteRepository.getNotes()
                } else {
                    noteRepository.searchNotes(query)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteNote(documentPath: String) {
        viewModelScope.launch {
            try {
                noteRepository.deleteNote(documentPath)
                fetchNotes()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setRefreshing(refreshing: Boolean) {
        _isRefreshing.value = refreshing
    }
}