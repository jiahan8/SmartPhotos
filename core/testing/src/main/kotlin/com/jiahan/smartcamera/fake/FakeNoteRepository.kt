package com.jiahan.smartcamera.fake

import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.domain.MediaDetail
import com.jiahan.smartcamera.domain.MediaUri
import com.jiahan.smartcamera.domain.NoteCursor
import com.jiahan.smartcamera.domain.NoteMediaDetail
import com.jiahan.smartcamera.domain.NotePage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [NoteRepository] test double.
 *
 * Paged/search/sync results are individually configurable so a test can drive any UI state without
 * Firebase or the network. Both streams are backed by a [MutableStateFlow] so emissions propagate
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
    var searchResult: Result<List<HomeNote>> = Result.success(emptyList())
    var deleteResult: Result<Unit> = Result.success(Unit)
    var favoriteResult: Result<Unit> = Result.success(Unit)
    var updateResult: Result<Unit> = Result.success(Unit)
    var addNoteResult: Result<Unit> = Result.success(Unit)
    var getNoteResult: Result<HomeNote>? = null
    var syncResult: Result<Unit> = Result.success(Unit)
    var buildLocalMediaDetailsResult: Result<List<NoteMediaDetail>> = Result.success(emptyList())

    private val notesFlow = MutableStateFlow<List<HomeNote>>(emptyList())
    private val favoritesFlow = MutableStateFlow<List<HomeNote>>(emptyList())

    var deleteCallCount = 0
    var favoriteCallCount = 0
    var updateCallCount = 0
    var addNoteCallCount = 0
    var lastDeletedNoteId: String? = null
    var lastFavoritedNote: HomeNote? = null
    var lastUpdatedNote: HomeNote? = null
    var lastAddedNote: HomeNote? = null

    fun setFavorites(notes: List<HomeNote>) {
        favoritesFlow.value = notes
    }

    /** Seeds the mirror [getNotesStream] observes, the way a fetched page would. */
    fun setNotesStream(notes: List<HomeNote>) {
        notesFlow.value = notes
    }

    /** Stubs a successful page. [nextCursor] drives pagination independently of [notes].size. */
    fun setNotes(notes: List<HomeNote>, nextCursor: NoteCursor? = null) {
        notesResult = Result.success(NotePage(notes, nextCursor))
    }

    override suspend fun getNotes(cursor: NoteCursor?, pageSize: Int): Result<NotePage> =
        notesResult

    override suspend fun addNote(homeNote: HomeNote): Result<Unit> {
        addNoteCallCount++
        lastAddedNote = homeNote
        return addNoteResult
    }

    override suspend fun updateNote(homeNote: HomeNote): Result<Unit> {
        updateCallCount++
        lastUpdatedNote = homeNote
        // Unconditional, matching the real repository once its favorite gate was dropped.
        if (updateResult.isSuccess) {
            notesFlow.update { notes ->
                notes.map { if (it.noteId == homeNote.noteId) homeNote else it }
            }
        }
        return updateResult
    }

    override suspend fun searchNotes(query: String): Result<List<HomeNote>> = searchResult

    override suspend fun deleteNote(noteId: String): Result<Unit> {
        deleteCallCount++
        lastDeletedNoteId = noteId
        // Mirrors the real repository deleting the row and getFavoriteNotesStream's underlying
        // Room query reactively dropping it, so tests exercising that pipeline (e.g. delete-then-
        // assert-removed-from-list) see the same behavior as production.
        if (deleteResult.isSuccess) {
            notesFlow.update { notes -> notes.filterNot { it.noteId == noteId } }
            favoritesFlow.update { notes -> notes.filterNot { it.noteId == noteId } }
        }
        return deleteResult
    }

    override suspend fun favoriteNote(homeNote: HomeNote): Result<Unit> {
        favoriteCallCount++
        lastFavoritedNote = homeNote
        // Mirrors getFavoriteNotesStream reactively reflecting the toggle (added when newly
        // favorited, dropped when un-favorited). Note this models the *stream*, not the table:
        // the real repository stopped deleting the row on unfavorite when Room became the feed's
        // mirror, but that query is `WHERE favorite = 1`, so what a subscriber sees is unchanged.
        if (favoriteResult.isSuccess) {
            val toggled = homeNote.copy(favorite = !homeNote.favorite)
            // The note keeps its row either way, flag flipped -- the real repository upserts in
            // both directions now rather than deleting on unfavorite.
            notesFlow.update { notes ->
                if (notes.none { it.noteId == toggled.noteId }) notes + toggled
                else notes.map { if (it.noteId == toggled.noteId) toggled else it }
            }
            favoritesFlow.update { notes ->
                when {
                    toggled.favorite && notes.none { it.noteId == toggled.noteId } ->
                        notes + toggled

                    !toggled.favorite ->
                        notes.filterNot { it.noteId == toggled.noteId }

                    else -> notes.map { if (it.noteId == toggled.noteId) toggled else it }
                }
            }
        }
        return favoriteResult
    }

    override suspend fun getNote(noteId: String): Result<HomeNote> =
        getNoteResult ?: Result.failure(NoSuchElementException("No note for $noteId"))

    override suspend fun quickUploadMediaToFirebase(
        uriList: List<MediaUri>,
        deleteAfterUpload: Boolean
    ) = Unit

    override suspend fun uploadMediaToFirebase(
        noteMediaDetailList: List<NoteMediaDetail>
    ): Result<List<MediaDetail>> = Result.success(emptyList())

    override suspend fun buildLocalMediaDetails(
        uriList: List<MediaUri>
    ): Result<List<NoteMediaDetail>> = buildLocalMediaDetailsResult

    override fun getNotesStream(): Flow<List<HomeNote>> = notesFlow

    override fun getFavoriteNotesStream(query: String): Flow<List<HomeNote>> =
        favoritesFlow.map { notes ->
            if (query.isBlank()) {
                notes
            } else {
                notes.filter {
                    it.text?.contains(query, ignoreCase = true) == true ||
                            it.username.contains(query, ignoreCase = true)
                }
            }
        }

    override suspend fun syncFavoriteNotes(): Result<Unit> = syncResult
}