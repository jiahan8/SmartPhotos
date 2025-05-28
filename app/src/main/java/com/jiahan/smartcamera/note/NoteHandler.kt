package com.jiahan.smartcamera.note

import com.jiahan.smartcamera.domain.HomeNote
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    suspend fun notifyNoteDeleted(documentPath: String) {
        _noteDeletedEvent.emit(documentPath)
    }

    suspend fun notifyNoteFavorited(homeNote: HomeNote) {
        _noteFavoritedEvent.emit(homeNote)
    }
}