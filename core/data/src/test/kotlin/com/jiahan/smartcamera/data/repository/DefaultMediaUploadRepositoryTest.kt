package com.jiahan.smartcamera.data.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.jiahan.smartcamera.domain.AppError
import com.jiahan.smartcamera.domain.MediaUri
import com.jiahan.smartcamera.domain.NoteMediaDetail
import com.jiahan.smartcamera.util.ErrorHandler
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Covers the branches of [DefaultMediaUploadRepository] that resolve before Firebase Storage is
 * touched -- which is as far as a JVM test reaches, since `Firebase.storage(...)` needs an
 * initialised FirebaseApp.
 *
 * That is not much of a limit in practice: the branches worth pinning here are the ones that
 * decide *whether* to upload at all, and every one of them is on this side of the SDK call. The
 * upload itself is covered on-device.
 *
 * Split out of [DefaultNoteRepositoryTest] with its subject.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class DefaultMediaUploadRepositoryTest {

    private companion object {
        const val USER_ID = "user-1"
        const val PHOTO_PATH = "file:///tmp/photo.jpg"
    }

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private val authRepository: AuthRepository = mockk()
    private val remoteConfigRepository: RemoteConfigRepository = mockk(relaxed = true)
    private val mediaFileRepository: MediaFileRepository = mockk(relaxed = true)
    private val errorHandler: ErrorHandler = mockk(relaxed = true)

    private val dispatcher = UnconfinedTestDispatcher()

    private val repository = DefaultMediaUploadRepository(
        context = context,
        remoteConfigRepository = remoteConfigRepository,
        authRepository = authRepository,
        mediaFileRepository = mediaFileRepository,
        errorHandler = errorHandler,
        // SupervisorJob mirrors di/AppModule's @ApplicationScope: without it a failing child of
        // uploadMediaToCache would cancel its siblings and the scope, a failure mode
        // production does not have.
        applicationScope = CoroutineScope(SupervisorJob() + dispatcher),
        ioDispatcher = dispatcher,
    )

    // -------------------------------------------------------------------------
    // uploadMedia
    // -------------------------------------------------------------------------

    @Test
    fun `uploadMedia signed out fails as NotAuthenticated`() = runTest(dispatcher) {
        every { authRepository.currentUserId } returns null

        val result = repository.uploadMedia(
            listOf(NoteMediaDetail(photoUri = MediaUri(PHOTO_PATH)))
        )

        assertTrue(result.exceptionOrNull() is AppError.NotAuthenticated)
    }

    @Test
    fun `uploadMedia succeeds with nothing to upload`() = runTest(dispatcher) {
        every { authRepository.currentUserId } returns USER_ID

        val result = repository.uploadMedia(emptyList())

        assertEquals(emptyList<Any>(), result.getOrNull())
    }

    // -------------------------------------------------------------------------
    // uploadMediaToCache
    //
    // Fire-and-forget, so there is no Result to assert -- what these pin is the file handling
    // around the upload, which is the half a caller can observe.
    // -------------------------------------------------------------------------

    @Test
    fun `uploadMediaToCache signed out still deletes a file it was asked to delete`() =
        runTest(dispatcher) {
            // The upload is skipped with no user to scope the path to, but the caller handed over
            // a temp file it had already stopped tracking. Not deleting it would leak the capture.
            every { authRepository.currentUserId } returns null

            repository.uploadMediaToCache(listOf(MediaUri(PHOTO_PATH)), deleteAfterUpload = true)

            verify { mediaFileRepository.deleteFile(any()) }
        }

    @Test
    fun `uploadMediaToCache leaves a file the app does not own`() = runTest(dispatcher) {
        // deleteAfterUpload = false is the gallery-pick case: the URI belongs to the picker, not
        // to us, so deleting it would remove the user's own photo.
        every { authRepository.currentUserId } returns null

        repository.uploadMediaToCache(listOf(MediaUri(PHOTO_PATH)), deleteAfterUpload = false)

        verify(exactly = 0) { mediaFileRepository.deleteFile(any()) }
    }

    @Test
    fun `uploadMediaToCache skips an empty file but still deletes it`() = runTest(dispatcher) {
        // hasContent gates the upload rather than the delete: a zero-byte capture is not worth
        // uploading, and is exactly the file that most needs cleaning up.
        every { authRepository.currentUserId } returns USER_ID
        every { mediaFileRepository.hasContent(any()) } returns false

        repository.uploadMediaToCache(listOf(MediaUri(PHOTO_PATH)), deleteAfterUpload = true)

        verify { mediaFileRepository.deleteFile(any()) }
    }

    // -------------------------------------------------------------------------
    // buildLocalMediaDetails
    // -------------------------------------------------------------------------

    @Test
    fun `buildLocalMediaDetails marks a photo as a photo and gives it no thumbnail`() =
        runTest(dispatcher) {
            every { mediaFileRepository.isVideoUri(any()) } returns false

            val result = repository.buildLocalMediaDetails(listOf(MediaUri(PHOTO_PATH)))

            val media = result.getOrNull()!!.single()
            assertEquals(MediaUri(PHOTO_PATH), media.photoUri)
            assertEquals(null, media.videoUri)
            assertEquals(null, media.thumbnailUri)
            assertEquals(false, media.isVideo)
        }

    @Test
    fun `buildLocalMediaDetails drops an unreadable item and keeps the rest`() =
        runTest(dispatcher) {
            // Per-item safeCall: one bad pick must not lose the others, which is the whole reason
            // the mapping is mapNotNull over an inner safeCall rather than a plain map.
            val bad = MediaUri("file:///tmp/bad.jpg")
            every { mediaFileRepository.isVideoUri(any()) } returns false
            every {
                mediaFileRepository.isVideoUri(match { it.toString() == bad.value })
            } throws IllegalStateException("unreadable")

            val result = repository.buildLocalMediaDetails(listOf(bad, MediaUri(PHOTO_PATH)))

            assertEquals(listOf(MediaUri(PHOTO_PATH)), result.getOrNull()!!.map { it.photoUri })
        }
}