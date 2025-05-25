package com.jiahan.smartcamera.note

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject

class NoteHandler @Inject constructor() {
    private val _noteAddedEvent = MutableSharedFlow<Unit>()
    val noteAddedEvent = _noteAddedEvent.asSharedFlow()

    suspend fun notifyNoteAdded() {
        _noteAddedEvent.emit(Unit)
    }
}