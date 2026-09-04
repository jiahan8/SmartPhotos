package com.jiahan.smartcamera.data.repository

import com.jiahan.smartcamera.domain.MediaDetail
import com.jiahan.smartcamera.domain.Note
import com.jiahan.smartcamera.domain.NoteCursor
import com.jiahan.smartcamera.domain.NotePage
import com.jiahan.smartcamera.util.AppConstants.DEFAULT_PAGE_SIZE
import kotlinx.coroutines.flow.Flow

/**
 * Data-layer contract for note operations.
 *
 * Every fallible operation returns [Result] so that callers never need to
 * wrap calls in try/catch. The Flow-returning members are the exemption, having no
 * single outcome to carry.
 *
 * Media preparation and upload are [MediaUploadRepository]'s, not this one's: a note carries the
 * [MediaDetail] list it produces, but nothing here reads a file or talks to Storage.
 */
interface NoteRepository {
    /**
     * Returns the page starting after [cursor], or the first page when it is null. Pass
     * [NotePage.nextCursor] back to advance; callers own their own position, so two callers
     * paginating at once do not interfere.
     */
    suspend fun getNotes(
        cursor: NoteCursor? = null,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): Result<NotePage>

    suspend fun addNote(note: Note): Result<Unit>
    suspend fun updateNote(note: Note): Result<Unit>
    suspend fun searchNotes(query: String): Result<List<Note>>
    suspend fun deleteNote(noteId: String): Result<Unit>
    suspend fun toggleFavorite(note: Note): Result<Unit>
    suspend fun getNote(noteId: String): Result<Note>

    /**
     * The local mirror of the notes feed, newest first, re-emitting whenever it changes.
     *
     * This is the live query [getNotes] does not provide, and the reason it exists is not offline
     * support: it is what lets Home, Search and NotePreview see each other's mutations by observing
     * one source instead of exchanging `NoteHandler` events. See the Source of truth and
     * Cross-feature communication sections of AGENTS.md.
     *
     * It carries no cursor. [getNotes] owns the remote pagination and writes each page into the
     * mirror; a subscriber here sees the result rather than driving it. [limit] is how a paginating
     * subscriber keeps the two in step: it widens the window as it pages, so the feed shows what it
     * has fetched rather than everything any other caller has since written into the table.
     */
    fun getNotesStream(limit: Int): Flow<List<Note>>

    /**
     * One mirrored note, emitting null once it is gone from the table.
     *
     * [getNote] fetches it and writes it through; a subscriber here then sees every later edit and
     * favorite toggle without re-fetching, which is what a detail screen sitting on the back stack
     * needs.
     */
    fun getNoteStream(noteId: String): Flow<Note?>

    /**
     * The mirror filtered by [query], re-emitting on every write to the table.
     *
     * [searchNotes] is still what reaches Firestore, and it writes its results through, so this
     * covers notes the feed has never paged. Filtering happens in Kotlin rather than SQL because a
     * note's media is a JSON column -- the same reason [getFavoriteNotesStream] does.
     */
    fun searchNotesStream(query: String): Flow<List<Note>>
    fun getFavoriteNotesStream(query: String): Flow<List<Note>>
    suspend fun syncFavoriteNotes(): Result<Unit>
}