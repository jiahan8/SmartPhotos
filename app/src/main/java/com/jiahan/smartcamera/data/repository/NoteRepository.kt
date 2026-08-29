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
 * Only [getFavoriteNotesStream] and [quickUploadMediaToFirebase] are exempt:
 * the former is a reactive Flow and the latter is fire-and-forget.
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
    fun getFavoriteNotesStream(query: String): Flow<List<HomeNote>>
    suspend fun syncFavoriteNotes(): Result<Unit>
}