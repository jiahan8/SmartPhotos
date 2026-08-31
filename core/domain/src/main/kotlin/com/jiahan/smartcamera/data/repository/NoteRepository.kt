package com.jiahan.smartcamera.data.repository

import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.domain.MediaDetail
import com.jiahan.smartcamera.domain.MediaUri
import com.jiahan.smartcamera.domain.NoteCursor
import com.jiahan.smartcamera.domain.NoteMediaDetail
import com.jiahan.smartcamera.domain.NotePage
import kotlinx.coroutines.flow.Flow

/**
 * Data-layer contract for note operations.
 *
 * Every fallible operation returns [Result] so that callers never need to
 * wrap calls in try/catch.
 * Only [getNotesStream], [getFavoriteNotesStream] and [quickUploadMediaToFirebase] are exempt:
 * the first two are reactive Flows and the last is fire-and-forget.
 */
interface NoteRepository {
    /**
     * Returns the page starting after [cursor], or the first page when it is null. Pass
     * [NotePage.nextCursor] back to advance; callers own their own position, so two callers
     * paginating at once do not interfere.
     */
    suspend fun getNotes(cursor: NoteCursor? = null, pageSize: Int = 10): Result<NotePage>
    suspend fun addNote(homeNote: HomeNote): Result<Unit>
    suspend fun updateNote(homeNote: HomeNote): Result<Unit>
    suspend fun searchNotes(query: String): Result<List<HomeNote>>
    suspend fun deleteNote(noteId: String): Result<Unit>
    suspend fun favoriteNote(homeNote: HomeNote): Result<Unit>
    suspend fun getNote(noteId: String): Result<HomeNote>

    /**
     * Fire-and-forget upload of [uriList] into the cache storage folder: failures are logged
     * internally instead of returned, and files with no content are skipped.
     *
     * Pass `deleteAfterUpload = true` for temporary capture files the caller owns — each one is
     * deleted once its upload is done, whether that upload succeeded, failed, or was skipped.
     * Leave it `false` for URIs the app doesn't own, such as gallery picks.
     */
    suspend fun quickUploadMediaToFirebase(
        uriList: List<MediaUri>,
        deleteAfterUpload: Boolean = false
    )
    suspend fun uploadMediaToFirebase(noteMediaDetailList: List<NoteMediaDetail>): Result<List<MediaDetail>>
    suspend fun buildLocalMediaDetails(uriList: List<MediaUri>): Result<List<NoteMediaDetail>>
    /**
     * The local mirror of the notes feed, newest first, re-emitting whenever it changes.
     *
     * This is the live query [getNotes] does not provide, and the reason it exists is not offline
     * support: it is what lets Home, Search and NotePreview see each other's mutations by observing
     * one source instead of exchanging `NoteHandler` events. See the Source of truth and
     * Cross-feature communication sections of AGENTS.md.
     *
     * It carries no cursor. [getNotes] owns the remote pagination and writes each page into the
     * mirror; a subscriber here sees the result rather than driving it.
     */
    fun getNotesStream(): Flow<List<HomeNote>>
    fun getFavoriteNotesStream(query: String): Flow<List<HomeNote>>
    suspend fun syncFavoriteNotes(): Result<Unit>
}