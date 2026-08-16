package com.jiahan.smartcamera.note

import com.jiahan.smartcamera.domain.HomeNote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class NoteHandler @Inject constructor() {
    private val _noteAddedEvent = MutableSharedFlow<Unit>()
    val noteAddedEvent = _noteAddedEvent.asSharedFlow()

    private val _noteDeletedEvent = MutableSharedFlow<String>()
    val noteDeletedEvent = _noteDeletedEvent.asSharedFlow()

    private val _noteFavoritedEvent = MutableSharedFlow<HomeNote>()
    val noteFavoritedEvent = _noteFavoritedEvent.asSharedFlow()

    suspend fun notifyNoteAdded() {
        _noteAddedEvent.emit(Unit)
    }

    suspend fun notifyNoteDeleted(noteId: String) {
        _noteDeletedEvent.emit(noteId)
    }

    suspend fun notifyNoteFavorited(homeNote: HomeNote) {
        _noteFavoritedEvent.emit(homeNote)
    }

    fun observeNoteMutations(
        scope: CoroutineScope,
        updateNotes: ((List<HomeNote>) -> List<HomeNote>) -> Unit
    ) {
        scope.launch {
            noteDeletedEvent.collect { noteId ->
                updateNotes { it.filter { note -> note.noteId != noteId } }
            }
        }
        scope.launch {
            noteFavoritedEvent.collect { updatedNote ->
                updateNotes { notes ->
                    notes.map { if (it.noteId == updatedNote.noteId) it.copy(favorite = updatedNote.favorite) else it }
                }
            }
        }
    }
}