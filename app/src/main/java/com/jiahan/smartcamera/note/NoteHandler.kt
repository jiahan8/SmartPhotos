package com.jiahan.smartcamera.note

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject

/**
 * The one cross-feature event the notes mirror cannot replace.
 *
 * This used to carry four: added, deleted, favorited and updated, with Home and Search collecting
 * the last three and applying list transforms by hand. Those three are writes to the `notes` table
 * now, so every screen observing it sees them without an event -- see AGENTS.md's Source of truth
 * and Cross-feature communication sections.
 *
 * [noteAddedEvent] survives because a new note cannot be mirrored locally: `addNote` delegates to
 * the createNote Cloud Function and discards its result, and the note's id and server-stamped
 * `created` exist only server-side. Refetching the first page is the only way it reaches the table,
 * so this stays until `addNote` returns the created note -- at which point delete this class rather
 * than finding it a new job.
 */
class NoteHandler @Inject constructor() {
    private val _noteAddedEvent = MutableSharedFlow<Unit>()
    val noteAddedEvent = _noteAddedEvent.asSharedFlow()

    suspend fun notifyNoteAdded() {
        _noteAddedEvent.emit(Unit)
    }
}