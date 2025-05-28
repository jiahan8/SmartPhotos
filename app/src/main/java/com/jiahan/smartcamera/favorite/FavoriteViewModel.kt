package com.jiahan.smartcamera.favorite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.note.NoteHandler
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
class FavoriteViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val noteHandler: NoteHandler
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
        viewModelScope.launch {
            searchQuery
                .debounce(300)
                .collect { query ->
                    searchNotes()
                }
        }
        viewModelScope.launch {
            noteHandler.noteDeletedEvent.collect { documentPath ->
                _notes.value = _notes.value.filter { it.documentPath != documentPath }
            }
        }
        viewModelScope.launch {
            noteHandler.noteFavoritedEvent.collect { updatedNote ->
                _notes.value = if (updatedNote.favorite) {
                    if (_notes.value.none { it.documentPath == updatedNote.documentPath }) {
                        val mutableList = _notes.value.toMutableList()

                        val insertIndex = mutableList.indexOfFirst {
                            it.createdDate?.let { existingTime ->
                                updatedNote.createdDate != null && updatedNote.createdDate > existingTime
                            } == true
                        }

                        if (insertIndex != -1) {
                            mutableList.add(insertIndex, updatedNote)
                            mutableList
                        } else {
                            mutableList.add(updatedNote)
                            mutableList
                        }
                    } else {
                        _notes.value.map { note ->
                            if (updatedNote.documentPath == note.documentPath) {
                                updatedNote
                            } else {
                                note
                            }
                        }
                    }
                } else {
                    _notes.value.filter { it.documentPath != updatedNote.documentPath }
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
            _notes.value = noteRepository.searchFavoritedNotes(
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
                _notes.value = _notes.value.map { note ->
                    if (homeNote.documentPath == note.documentPath) {
                        note.copy(favorite = !note.favorite)
                    } else {
                        note
                    }
                }
                noteHandler.notifyNoteFavorited(homeNote.copy(favorite = !homeNote.favorite))
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