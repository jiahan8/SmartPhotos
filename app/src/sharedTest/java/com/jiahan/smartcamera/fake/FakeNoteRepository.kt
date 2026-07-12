package com.jiahan.smartcamera.fake

import android.net.Uri
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.domain.MediaDetail
import com.jiahan.smartcamera.note.NoteMediaDetail
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [NoteRepository] test double.
 *
 * Paged/search/sync results are individually configurable so a test can drive any UI state without
 * Firebase or the network. The favorites stream is backed by a [MutableStateFlow] so emissions
 * propagate reactively, and mutating operations record their invocations for behavior assertions.
 */
class FakeNoteRepository : NoteRepository {

    var notesResult: Result<List<HomeNote>> = Result.success(emptyList())
    var searchResult: Result<List<HomeNote>> = Result.success(emptyList())
    var deleteResult: Result<Unit> = Result.success(Unit)
    var favoriteResult: Result<Unit> = Result.success(Unit)
    var getNoteResult: Result<HomeNote>? = null
    var syncResult: Result<Unit> = Result.success(Unit)

    private val favoritesFlow = MutableStateFlow<List<HomeNote>>(emptyList())

    var deleteCallCount = 0
    var favoriteCallCount = 0
    var lastDeletedPath: String? = null
    var lastFavoritedNote: HomeNote? = null

    fun setFavorites(notes: List<HomeNote>) {
        favoritesFlow.value = notes
    }

    override suspend fun getNotes(page: Int, pageSize: Int): Result<List<HomeNote>> = notesResult

    override suspend fun addNote(homeNote: HomeNote): Result<Unit> = Result.success(Unit)

    override suspend fun searchNotes(query: String): Result<List<HomeNote>> = searchResult

    override suspend fun deleteNote(documentPath: String): Result<Unit> {
        deleteCallCount++
        lastDeletedPath = documentPath
        return deleteResult
    }

    override suspend fun favoriteNote(homeNote: HomeNote): Result<Unit> {
        favoriteCallCount++
        lastFavoritedNote = homeNote
        return favoriteResult
    }

    override suspend fun getNote(documentPath: String): Result<HomeNote> =
        getNoteResult ?: Result.failure(NoSuchElementException("No note for $documentPath"))

    override suspend fun quickUploadMediaToFirebase(uriList: List<Uri>) {}

    override suspend fun uploadMediaToFirebase(
        noteMediaDetailList: List<NoteMediaDetail>
    ): Result<List<MediaDetail>> = Result.success(emptyList())

    override suspend fun buildLocalMediaDetails(
        uriList: List<Uri>
    ): Result<List<NoteMediaDetail>> = Result.success(emptyList())

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