package com.jiahan.smartcamera.fake

import com.jiahan.smartcamera.data.repository.MediaUploadRepository
import com.jiahan.smartcamera.domain.MediaDetail
import com.jiahan.smartcamera.domain.MediaUri
import com.jiahan.smartcamera.domain.NoteMediaDetail

/**
 * In-memory [MediaUploadRepository] test double.
 *
 * Every result is configurable and every call is recorded, so a test can drive the note-creation
 * flow without Firebase Storage. Split out of [FakeNoteRepository] with the three methods it
 * doubles.
 *
 * [uploadMediaToCache] records rather than returning: it is fire-and-forget in the contract too,
 * so what a test can assert is that it was called, and with what.
 */
class FakeMediaUploadRepository : MediaUploadRepository {

    var buildLocalMediaDetailsResult: Result<List<NoteMediaDetail>> = Result.success(emptyList())
    var uploadMediaResult: Result<List<MediaDetail>> = Result.success(emptyList())

    var buildLocalMediaDetailsCallCount = 0
    var uploadMediaCallCount = 0
    var lastBuiltUriList: List<MediaUri>? = null
    var lastUploadedMediaList: List<NoteMediaDetail>? = null

    /** Every [uploadMediaToCache] call, in order, as the URIs passed and the delete flag. */
    val cacheUploads = mutableListOf<Pair<List<MediaUri>, Boolean>>()

    override suspend fun buildLocalMediaDetails(
        uriList: List<MediaUri>
    ): Result<List<NoteMediaDetail>> {
        buildLocalMediaDetailsCallCount++
        lastBuiltUriList = uriList
        return buildLocalMediaDetailsResult
    }

    override suspend fun uploadMedia(
        noteMediaDetailList: List<NoteMediaDetail>
    ): Result<List<MediaDetail>> {
        uploadMediaCallCount++
        lastUploadedMediaList = noteMediaDetailList
        return uploadMediaResult
    }

    override suspend fun uploadMediaToCache(uriList: List<MediaUri>, deleteAfterUpload: Boolean) {
        cacheUploads += uriList to deleteAfterUpload
    }
}