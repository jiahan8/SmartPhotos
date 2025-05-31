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

    private val _note = MutableStateFlow<HomeNote>(HomeNote(documentPath = documentPath))
    val note = _note.asStateFlow()

    init {
        getNote(documentPath)
    }

    fun getNote(documentPath: String) {
        viewModelScope.launch {
            try {
                val note = noteRepository.getNote(documentPath)
                _note.value = note
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}