package com.jiahan.smartcamera.fake

import com.jiahan.smartcamera.domain.Note
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * The `notes` table, as a test fixture.
 *
 * Every note-rendering screen reads Room rather than what a fetch returned, so a test that stubs
 * only the fetch renders nothing. Standing that pipeline up means modelling two things -- how a
 * fetched page lands in the table ([upsert]) and how a query reads it back ([stream]) -- and both
 * had been re-derived per test file, which is how they drifted: the copy in `HomeViewModelTest`
 * ordered by date while [FakeNoteRepository] kept insertion order, so the same page produced
 * different feeds depending on which double a test happened to use.
 *
 * This is that model, defined once. [FakeNoteRepository] delegates to it, and a test that needs
 * mockk for per-cursor or per-query stubbing drives it directly instead of writing its own.
 */
class NoteMirror {

    private val rows = MutableStateFlow<List<Note>>(emptyList())

    /**
     * `upsertNotes`, keyed by note id.
     *
     * Nothing is ever removed: the table is not reconciled against the server, so a reload adds to
     * the mirror rather than replacing it. That is a documented gap, not an oversight, and a
     * fixture that replaced instead would hide it.
     */
    fun upsert(notes: List<Note>) {
        rows.update { existing ->
            val refreshed = existing.map { old ->
                notes.firstOrNull { it.noteId == old.noteId } ?: old
            }
            refreshed + notes.filter { new -> existing.none { it.noteId == new.noteId } }
        }
    }

    fun upsert(note: Note) = upsert(listOf(note))

    /** `DELETE FROM notes WHERE note_id = :noteId`. */
    fun delete(noteId: String) {
        rows.update { notes -> notes.filterNot { it.noteId == noteId } }
    }

    /** Seeds the table outright, standing in for rows an earlier session left behind. */
    fun set(notes: List<Note>) {
        rows.value = notes
    }

    fun update(transform: (List<Note>) -> List<Note>) {
        rows.update(transform)
    }

    /** `SELECT * FROM notes ORDER BY created_date DESC`. */
    fun stream(): Flow<List<Note>> = rows.map { it.newestFirst() }

    /**
     * `SELECT * FROM notes ORDER BY created_date DESC LIMIT :limit`.
     *
     * The order is applied before the limit, as SQLite does, so which rows a windowed read returns
     * depends on their dates rather than on the order they were written.
     */
    fun stream(limit: Int): Flow<List<Note>> = rows.map { it.newestFirst().take(limit) }

    /** `SELECT * FROM notes WHERE note_id = :noteId`, null once the row is gone. */
    fun noteStream(noteId: String): Flow<Note?> =
        rows.map { notes -> notes.firstOrNull { it.noteId == noteId } }

    private companion object {
        /**
         * SQLite sorts NULLs last under DESC, and `sortedWith` is stable, so rows sharing a date
         * keep the order they were written in.
         */
        fun List<Note>.newestFirst(): List<Note> =
            sortedWith(compareByDescending(nullsFirst()) { it.createdDate })
    }
}