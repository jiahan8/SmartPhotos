package com.jiahan.smartcamera.preview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiahan.smartcamera.Screen
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.note.NoteHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotePreviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val noteRepository: NoteRepository,
    private val noteHandler: NoteHandler
) : ViewModel() {

    private val documentPath: String = checkNotNull(savedStateHandle[Screen.NotePreview.ID_ARG])

    private val _note =
        MutableStateFlow<HomeNote>(HomeNote(documentPath = documentPath, username = ""))
    val note = _note.asStateFlow()
    private val _noteToDelete = MutableStateFlow<HomeNote?>(null)
    val noteToDelete = _noteToDelete.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    init {
        getNote(documentPath)
    }

    private fun getNote(documentPath: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val note = noteRepository.getNote(documentPath)
                _note.value = note
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
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
                _note.value = homeNote.copy(favorite = homeNote.favorite.not())
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