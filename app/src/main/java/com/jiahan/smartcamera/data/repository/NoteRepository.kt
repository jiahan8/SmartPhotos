package com.jiahan.smartcamera.data.repository

import android.net.Uri
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.domain.MediaDetail
import com.jiahan.smartcamera.domain.NoteMediaDetail
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
    suspend fun getNotes(page: Int = 0, pageSize: Int = 10): Result<List<HomeNote>>
    suspend fun addNote(homeNote: HomeNote): Result<Unit>
    suspend fun searchNotes(query: String): Result<List<HomeNote>>
    suspend fun deleteNote(documentPath: String): Result<Unit>
    suspend fun favoriteNote(homeNote: HomeNote): Result<Unit>
    suspend fun searchFavoriteNotes(query: String): Result<List<HomeNote>>
    suspend fun getNote(documentPath: String): Result<HomeNote>

    /** Fire-and-forget pre-upload; errors are logged internally. */
    suspend fun quickUploadMediaToFirebase(uriList: List<Uri>)
    suspend fun uploadMediaToFirebase(noteMediaDetailList: List<NoteMediaDetail>): Result<List<MediaDetail>>
    fun getFavoriteNotesStream(query: String): Flow<List<HomeNote>>
    suspend fun syncFavoriteNotes(): Result<Unit>
}