package com.jiahan.smartcamera.fake

import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.domain.Note
import com.jiahan.smartcamera.domain.NoteCursor
import com.jiahan.smartcamera.domain.NotePage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * In-memory [NoteRepository] test double.
 *
 * Paged/search/sync results are individually configurable so a test can drive any UI state without
 * Firebase or the network. Both streams are backed by a [NoteMirror] so emissions propagate
 * reactively, and mutating operations record their invocations for behavior assertions.
 *
 * The two streams model the two Room queries and are kept separately rather than derived from one
 * another, because that is what the real repository has: `getNotes()` over the whole table and
 * `getFavoriteNotes()` over `WHERE favorite = 1`. A mutation therefore has to be reflected in both,
 * and they disagree on purpose -- unfavoriting drops a note from the favorites stream while it
 * stays in the notes stream, which is exactly the distinction the real table now draws.
 */
class FakeNoteRepository : NoteRepository {

    var notesResult: Result<NotePage> = Result.success(NotePage(emptyList()))
    var searchResult: Result<List<Note>> = Result.success(emptyList())
    var deleteResult: Result<Unit> = Result.success(Unit)
    var favoriteResult: Result<Unit> = Result.success(Unit)
    var updateResult: Result<Unit> = Result.success(Unit)
    var addNoteResult: Result<Unit> = Result.success(Unit)
    var getNoteResult: Result<Note>? = null
    var syncResult: Result<Unit> = Result.success(Unit)

    /** The `notes` table. Shared with the mockk-based tests so the semantics are defined once. */
    val notes = NoteMirror()
    private val favorites = NoteMirror()

    var notesCallCount = 0
    var lastNotesCursor: NoteCursor? = null
    var deleteCallCount = 0
    var favoriteCallCount = 0
    var updateCallCount = 0
    var addNoteCallCount = 0
    var lastDeletedNoteId: String? = null
    var lastFavoritedNote: Note? = null
    var lastUpdatedNote: Note? = null
    var lastAddedNote: Note? = null

    fun setFavorites(notes: List<Note>) {
        favorites.set(notes)
    }

    /** Seeds the mirror [getNotesStream] observes, the way a fetched page would. */
    fun setNotesStream(notes: List<Note>) {
        this.notes.set(notes)
    }

    /** Stubs a successful page. [nextCursor] drives pagination independently of [notes].size. */
    fun setNotes(notes: List<Note>, nextCursor: NoteCursor? = null) {
        notesResult = Result.success(NotePage(notes, nextCursor))
    }

    override suspend fun getNotes(cursor: NoteCursor?, pageSize: Int): Result<NotePage> {
        lastNotesCursor = cursor
        notesCallCount++
        // Mirrors `cacheNotes`: the real repository writes every page it fetches into Room on its
        // way through, which is what makes getNotesStream the feed's read path rather than the
        // returned page.
        notesResult.getOrNull()?.let { notes.upsert(it.notes) }
        return notesResult
    }

    private fun matchesQuery(note: Note, query: String): Boolean =
        query.isBlank() ||
                note.text?.contains(query, ignoreCase = true) == true ||
                note.username.contains(query, ignoreCase = true)

    override suspend fun addNote(note: Note): Result<Unit> {
        addNoteCallCount++
        lastAddedNote = note
        return addNoteResult
    }

    override suspend fun updateNote(note: Note): Result<Unit> {
        updateCallCount++
        lastUpdatedNote = note
        // Unconditional, matching the real repository once its favorite gate was dropped.
        if (updateResult.isSuccess) {
            notes.update { rows ->
                rows.map { if (it.noteId == note.noteId) note else it }
            }
        }
        return updateResult
    }

    override suspend fun searchNotes(query: String): Result<List<Note>> {
        // Mirrors the real repository writing its results through, so searchNotesStream can cover
        // notes the feed never paged.
        searchResult.getOrNull()?.let { notes.upsert(it) }
        return searchResult
    }

    override suspend fun deleteNote(noteId: String): Result<Unit> {
        deleteCallCount++
        lastDeletedNoteId = noteId
        // Mirrors the real repository deleting the row and getFavoriteNotesStream's underlying
        // Room query reactively dropping it, so tests exercising that pipeline (e.g. delete-then-
        // assert-removed-from-list) see the same behavior as production.
        if (deleteResult.isSuccess) {
            notes.delete(noteId)
            favorites.delete(noteId)
        }
        return deleteResult
    }

    override suspend fun toggleFavorite(note: Note): Result<Unit> {
        favoriteCallCount++
        lastFavoritedNote = note
        // Mirrors getFavoriteNotesStream reactively reflecting the toggle (added when newly
        // favorited, dropped when un-favorited). Note this models the *stream*, not the table:
        // the real repository stopped deleting the row on unfavorite when Room became the feed's
        // mirror, but that query is `WHERE favorite = 1`, so what a subscriber sees is unchanged.
        if (favoriteResult.isSuccess) {
            val toggled = note.copy(isFavorite = !note.isFavorite)
            // The note keeps its row either way, flag flipped -- the real repository upserts in
            // both directions now rather than deleting on unfavorite.
            notes.upsert(toggled)
            if (toggled.isFavorite) favorites.upsert(toggled) else favorites.delete(toggled.noteId)
        }
        return favoriteResult
    }

    override suspend fun getNote(noteId: String): Result<Note> {
        val result = getNoteResult ?: Result.failure(NoSuchElementException("No note for $noteId"))
        result.getOrNull()?.let { notes.upsert(it) }
        return result
    }

    override fun getNotesStream(limit: Int): Flow<List<Note>> = notes.stream(limit)

    override fun getNoteStream(noteId: String): Flow<Note?> = notes.noteStream(noteId)

    override fun searchNotesStream(query: String): Flow<List<Note>> =
        notes.stream().map { rows -> rows.filter { matchesQuery(it, query) } }

    override fun getFavoriteNotesStream(query: String): Flow<List<Note>> =
        favorites.stream().map { rows ->
            if (query.isBlank()) {
                rows
            } else {
                rows.filter {
                    it.text?.contains(query, ignoreCase = true) == true ||
                            it.username.contains(query, ignoreCase = true)
                }
            }
        }

    override suspend fun syncFavoriteNotes(): Result<Unit> = syncResult
}