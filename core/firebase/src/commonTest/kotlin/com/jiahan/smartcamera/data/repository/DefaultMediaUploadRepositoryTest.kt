package com.jiahan.smartcamera.data.repository

import com.jiahan.smartcamera.domain.AppError
import com.jiahan.smartcamera.domain.MediaDetail
import com.jiahan.smartcamera.domain.MediaUri
import com.jiahan.smartcamera.domain.NoteMediaDetail
import com.jiahan.smartcamera.fake.FakeAuthRepository
import com.jiahan.smartcamera.fake.FakeErrorHandler
import com.jiahan.smartcamera.fake.FakeMediaFileRepository
import com.jiahan.smartcamera.fake.FakeRemoteConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Covers [DefaultMediaUploadRepository]: which media it uploads, where each lands, and what happens
 * to the local files around the upload.
 *
 * It used to stop where Storage began, since `Firebase.storage(...)` needs an initialised FirebaseApp,
 * and the uploads themselves were left to the device. [MediaStorage] is the seam that lifted that, so
 * the paths, a video's thumbnail and a failed item dropped from the batch are pinned here as well.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultMediaUploadRepositoryTest {

    private companion object {
        const val USER_ID = "user-1"
        val PHOTO = MediaUri("file:///tmp/photo.jpg")
        val VIDEO = MediaUri("file:///tmp/video.mp4")
        val THUMBNAIL = MediaUri("file:///tmp/thumbnail.jpg")
    }

    private class FakeMediaStorage : MediaStorage {
        val puts = mutableListOf<Pair<String, MediaUri>>()
        val failingFiles = mutableSetOf<MediaUri>()

        override suspend fun putFile(path: String, file: MediaUri) {
            if (file in failingFiles) throw IllegalStateException("upload failed")
            puts += path to file
        }

        override suspend fun getDownloadUrl(path: String): String = "https://storage.example/$path"
    }

    private val dispatcher = UnconfinedTestDispatcher()

    private val authRepository = FakeAuthRepository().apply { currentUserId = USER_ID }
    private val mediaFileRepository = FakeMediaFileRepository()
    private val storage = FakeMediaStorage()
    private val errorHandler = FakeErrorHandler()
    private val remoteConfigRepository = FakeRemoteConfigRepository().apply {
        storageFolder = "notes"
        storageCacheFolder = "cache"
    }

    private val repository = DefaultMediaUploadRepository(
        storage = storage,
        remoteConfigRepository = remoteConfigRepository,
        authRepository = authRepository,
        mediaFileRepository = mediaFileRepository,
        errorHandler = errorHandler,
        // SupervisorJob mirrors di/AppModule's ApplicationScope: without it a failing child of
        // uploadMediaToCache would cancel its siblings and the scope, a failure mode production
        // does not have.
        applicationScope = CoroutineScope(SupervisorJob() + dispatcher),
        ioDispatcher = dispatcher,
    )

    // -------------------------------------------------------------------------
    // uploadMedia
    // -------------------------------------------------------------------------

    @Test
    fun `uploadMedia signed out fails as NotAuthenticated`() = runTest(dispatcher) {
        authRepository.currentUserId = null

        val result = repository.uploadMedia(listOf(NoteMediaDetail(photoUri = PHOTO)))

        assertIs<AppError.NotAuthenticated>(result.exceptionOrNull())
        assertTrue(storage.puts.isEmpty())
    }

    @Test
    fun `uploadMedia succeeds with nothing to upload`() = runTest(dispatcher) {
        val result = repository.uploadMedia(emptyList())

        assertEquals(emptyList(), result.getOrThrow())
    }

    @Test
    fun `uploadMedia puts a photo under the folder and account and returns its URL`() =
        runTest(dispatcher) {
            val media = repository.uploadMedia(listOf(NoteMediaDetail(photoUri = PHOTO)))
                .getOrThrow()
                .single()

            val (path, file) = storage.puts.single()
            assertEquals(PHOTO, file)
            assertTrue(path.startsWith("notes/$USER_ID/"), path)
            assertTrue(path.endsWith(".jpg"), path)
            assertEquals(MediaDetail(photoUrl = "https://storage.example/$path"), media)
        }

    @Test
    fun `uploadMedia puts a video and then its thumbnail`() = runTest(dispatcher) {
        val media = repository.uploadMedia(
            listOf(NoteMediaDetail(videoUri = VIDEO, thumbnailUri = THUMBNAIL, isVideo = true))
        ).getOrThrow().single()

        assertEquals(listOf(VIDEO, THUMBNAIL), storage.puts.map { it.second })
        val (videoPath, thumbnailPath) = storage.puts.map { it.first }
        assertTrue(videoPath.startsWith("notes/$USER_ID/"), videoPath)
        assertTrue(videoPath.endsWith(".mp4"), videoPath)
        assertTrue(thumbnailPath.startsWith("notes/$USER_ID/smartcamerathumbnail_"), thumbnailPath)
        assertTrue(thumbnailPath.endsWith(".jpg"), thumbnailPath)
        assertEquals(
            MediaDetail(
                videoUrl = "https://storage.example/$videoPath",
                thumbnailUrl = "https://storage.example/$thumbnailPath",
                isVideo = true,
            ),
            media,
        )
    }

    @Test
    fun `uploadMedia drops an item whose upload fails and keeps the rest`() = runTest(dispatcher) {
        storage.failingFiles += VIDEO

        val media = repository.uploadMedia(
            listOf(NoteMediaDetail(videoUri = VIDEO, isVideo = true), NoteMediaDetail(photoUri = PHOTO))
        ).getOrThrow()

        // The contract's promise: a failed item is logged and dropped, and the batch still succeeds
        // with what did upload -- so the returned list can be shorter than the one passed in.
        assertEquals(listOf(PHOTO), storage.puts.map { it.second })
        assertEquals(1, media.size)
        assertEquals(1, errorHandler.loggedErrors.size)
    }

    // -------------------------------------------------------------------------
    // uploadMediaToCache
    //
    // Fire-and-forget, so there is no Result to assert -- what these pin is where the upload goes
    // and the file handling around it, which is the half a caller can observe.
    // -------------------------------------------------------------------------

    @Test
    fun `uploadMediaToCache puts a capture in the cache folder and then deletes it`() =
        runTest(dispatcher) {
            repository.uploadMediaToCache(listOf(PHOTO), deleteAfterUpload = true)

            val (path, file) = storage.puts.single()
            assertEquals(PHOTO, file)
            assertTrue(path.startsWith("cache/$USER_ID/"), path)
            assertEquals(listOf(PHOTO), mediaFileRepository.deletedUris)
        }

    @Test
    fun `uploadMediaToCache signed out still deletes a file it was asked to delete`() =
        runTest(dispatcher) {
            // The upload is skipped with no user to scope the path to, but the caller handed over
            // a temp file it had already stopped tracking. Not deleting it would leak the capture.
            authRepository.currentUserId = null

            repository.uploadMediaToCache(listOf(PHOTO), deleteAfterUpload = true)

            assertTrue(storage.puts.isEmpty())
            assertEquals(listOf(PHOTO), mediaFileRepository.deletedUris)
        }

    @Test
    fun `uploadMediaToCache leaves a file the app does not own`() = runTest(dispatcher) {
        // deleteAfterUpload = false is the gallery-pick case: the location belongs to the picker,
        // not to us, so deleting it would remove the user's own photo.
        authRepository.currentUserId = null

        repository.uploadMediaToCache(listOf(PHOTO), deleteAfterUpload = false)

        assertTrue(mediaFileRepository.deletedUris.isEmpty())
    }

    @Test
    fun `uploadMediaToCache skips an empty file but still deletes it`() = runTest(dispatcher) {
        // hasContent gates the upload rather than the delete: a zero-byte capture is not worth
        // uploading, and is exactly the file that most needs cleaning up.
        mediaFileRepository.emptyUris += PHOTO

        repository.uploadMediaToCache(listOf(PHOTO), deleteAfterUpload = true)

        assertTrue(storage.puts.isEmpty())
        assertEquals(listOf(PHOTO), mediaFileRepository.deletedUris)
    }

    // -------------------------------------------------------------------------
    // buildLocalMediaDetails
    // -------------------------------------------------------------------------

    @Test
    fun `buildLocalMediaDetails marks a photo as a photo and gives it no thumbnail`() =
        runTest(dispatcher) {
            val result = repository.buildLocalMediaDetails(listOf(PHOTO))

            assertEquals(listOf(NoteMediaDetail(photoUri = PHOTO)), result.getOrThrow())
        }

    @Test
    fun `buildLocalMediaDetails gives a video its thumbnail`() = runTest(dispatcher) {
        mediaFileRepository.videoUris += VIDEO
        mediaFileRepository.thumbnailUri = THUMBNAIL

        val result = repository.buildLocalMediaDetails(listOf(VIDEO))

        assertEquals(
            listOf(NoteMediaDetail(videoUri = VIDEO, thumbnailUri = THUMBNAIL, isVideo = true)),
            result.getOrThrow(),
        )
    }

    @Test
    fun `buildLocalMediaDetails drops an unreadable item and keeps the rest`() =
        runTest(dispatcher) {
            // Per-item safeCall: one bad pick must not lose the others, which is the whole reason
            // the mapping is mapNotNull over an inner safeCall rather than a plain map.
            val bad = MediaUri("file:///tmp/bad.jpg")
            mediaFileRepository.unreadableUris += bad

            val result = repository.buildLocalMediaDetails(listOf(bad, PHOTO))

            assertEquals(listOf(PHOTO), result.getOrThrow().map { it.photoUri })
            assertEquals(1, errorHandler.loggedErrors.size)
        }
}