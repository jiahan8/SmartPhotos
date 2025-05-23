package com.jiahan.smartcamera.note

import androidx.lifecycle.ViewModel
import com.jiahan.smartcamera.data.repository.NotesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class NoteViewModel @Inject constructor(
    private val notesRepository: NotesRepository,
) : ViewModel() {

    suspend fun getNotes() = notesRepository.getNotes()

    suspend fun searchNotes(query: String) =
        if (query.isEmpty()) {
            getNotes()
        } else {
            notesRepository.searchNotes(query)
        }
}