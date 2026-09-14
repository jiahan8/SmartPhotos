package com.jiahan.smartcamera.data.repository

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.net.toUri
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.FileConstants.EXTENSION_JPG
import com.jiahan.smartcamera.util.FileConstants.EXTENSION_MP4
import com.jiahan.smartcamera.util.FileConstants.PREFIX_PHOTO
import com.jiahan.smartcamera.util.FileConstants.PREFIX_THUMBNAIL
import com.jiahan.smartcamera.util.FileConstants.PREFIX_VIDEO
import com.jiahan.smartcamera.util.toMediaUri
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Covers [DefaultMediaFileRepository], which is the app's only writer of cache files.
 *
 * Everything here fails by returning `null` or `false` rather than by throwing, so a broken branch
 * is silent at the call site: a capture that produces no URI just looks like a user who changed
 * their mind. That is what makes the failure arms worth as many tests as the happy paths, and why
 * each one also asserts the error reached [ErrorHandler] -- a swallowed exception with no log is
 * indistinguishable from nothing having happened.
 *
 * Two decisions shape the fixtures:
 *
 * - **A real cache directory**, not a mocked `File`. These functions are `File.createTempFile` plus
 *   a stream copy; a mock would assert that the code calls the API rather than that a file with the
 *   right bytes exists at the end.
 * - **A `file://` source URL** for the download. `URL.openStream()` handles it exactly as it
 *   handles `http://`, so the copy path runs for real with no network and no mocked stream.
 *
 * `FileProvider.getUriForFile` is the one thing stubbed: it resolves against the `<provider>` in
 * :app's manifest, which does not exist on this module's test classpath.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class DefaultMediaFileRepositoryTest {

    private val context: Context = mockk()
    private val contentResolver: ContentResolver = mockk()
    private val errorHandler: ErrorHandler = mockk(relaxed = true)
    private val dispatcher = UnconfinedTestDispatcher()

    private lateinit var cacheDir: File

    private val repository by lazy {
        DefaultMediaFileRepository(
            context = context,
            errorHandler = errorHandler,
            ioDispatcher = dispatcher,
        )
    }

    /** Stands in for the FileProvider URI, keyed to the file so tests can read the bytes back. */
    private fun providerUri(file: File): Uri = "content://test-provider/${file.name}".toUri()

    @Before
    fun setUp() {
        cacheDir = File.createTempFile("cache", "").let { probe ->
            probe.delete()
            probe.mkdirs()
            probe
        }
        every { context.cacheDir } returns cacheDir
        every { context.contentResolver } returns contentResolver

        mockkStatic(FileProvider::class)
        val fileSlot = slot<File>()
        every {
            FileProvider.getUriForFile(any(), any(), capture(fileSlot))
        } answers { providerUri(fileSlot.captured) }
    }

    @After
    fun tearDown() {
        unmockkStatic(FileProvider::class)
        cacheDir.deleteRecursively()
    }

    private fun cacheFiles(prefix: String) =
        cacheDir.listFiles().orEmpty().filter { it.name.startsWith(prefix) }

    /** Points [context].cacheDir at somewhere unwritable, so `createTempFile` throws. */
    private fun breakCacheDir() {
        every { context.cacheDir } returns File(cacheDir, "does/not/exist")
    }

    // -------------------------------------------------------------------------
    // createPhotoUri / createVideoUri
    // -------------------------------------------------------------------------

    @Test
    fun `createPhotoUri creates a jpg in the cache directory`() {
        val uri = repository.createPhotoUri()

        assertNotNull(uri)
        val created = cacheFiles(PREFIX_PHOTO)
        assertEquals(1, created.size)
        assertTrue(created.single().name.endsWith(EXTENSION_JPG))
    }

    @Test
    fun `createVideoUri creates an mp4 in the cache directory`() {
        val uri = repository.createVideoUri()

        assertNotNull(uri)
        val created = cacheFiles(PREFIX_VIDEO)
        assertEquals(1, created.size)
        assertTrue(created.single().name.endsWith(EXTENSION_MP4))
    }

    @Test
    fun `createPhotoUri returns null and logs when the file cannot be created`() {
        breakCacheDir()

        assertNull(repository.createPhotoUri())
        verify { errorHandler.logError(any(), any()) }
    }

    @Test
    fun `createVideoUri returns null and logs when the file cannot be created`() {
        breakCacheDir()

        assertNull(repository.createVideoUri())
        verify { errorHandler.logError(any(), any()) }
    }

    // -------------------------------------------------------------------------
    // saveBitmapAsTempFile
    // -------------------------------------------------------------------------

    @Test
    fun `saveBitmapAsTempFile writes a thumbnail and returns its file uri`() {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)

        val uri = repository.saveBitmapAsTempFile(bitmap)

        assertEquals("file", uri?.scheme)
        assertEquals(1, cacheFiles(PREFIX_THUMBNAIL).size)
    }

    @Test
    fun `saveBitmapAsTempFile recycles the bitmap`() {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)

        repository.saveBitmapAsTempFile(bitmap)

        assertTrue(bitmap.isRecycled)
    }

    /**
     * The recycle sits in a `finally`, and that placement is the point: the caller hands over
     * ownership of the bitmap, so a failure that skipped the recycle would leak it on exactly the
     * path where nothing else cleans up either.
     */
    @Test
    fun `saveBitmapAsTempFile recycles the bitmap even when writing fails`() {
        breakCacheDir()
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)

        assertNull(repository.saveBitmapAsTempFile(bitmap))

        assertTrue(bitmap.isRecycled)
        verify { errorHandler.logError(any(), any()) }
    }

    // -------------------------------------------------------------------------
    // downloadToCacheFile
    // -------------------------------------------------------------------------

    private fun remoteSource(bytes: ByteArray): String {
        val source = File.createTempFile("source", ".bin", cacheDir)
        source.writeBytes(bytes)
        return source.toURI().toString()
    }

    @Test
    fun `downloadToCacheFile copies the bytes into the cache`() = runTest(dispatcher) {
        val payload = "the photo bytes".toByteArray()

        val uri = repository.downloadToCacheFile(remoteSource(payload), isVideo = false)

        assertNotNull(uri)
        val downloaded = cacheFiles(PREFIX_PHOTO).single()
        assertArrayEquals(payload, downloaded.readBytes())
    }

    @Test
    fun `downloadToCacheFile uses the mp4 extension for a video`() = runTest(dispatcher) {
        repository.downloadToCacheFile(remoteSource(byteArrayOf(1, 2, 3)), isVideo = true)

        assertTrue(cacheFiles(PREFIX_VIDEO).single().name.endsWith(EXTENSION_MP4))
    }

    @Test
    fun `downloadToCacheFile returns null and logs when the source is unreachable`() =
        runTest(dispatcher) {
            val missing = File(cacheDir, "absent.bin").toURI().toString()

            assertNull(repository.downloadToCacheFile(missing, isVideo = false))
            verify { errorHandler.logError(any(), any()) }
            // The destination is created before the source is opened, so a failed download leaves
            // an empty file in the cache unless it is cleaned up -- and this is the path a user
            // retries, so they accumulate one per offline tap.
            assertTrue(cacheFiles(PREFIX_PHOTO).isEmpty())
        }

    @Test
    fun `downloadToCacheFile returns null and logs when the url is malformed`() =
        runTest(dispatcher) {
            assertNull(repository.downloadToCacheFile("not a url", isVideo = false))
            verify { errorHandler.logError(any(), any()) }
            assertTrue(cacheFiles(PREFIX_PHOTO).isEmpty())
        }

    // -------------------------------------------------------------------------
    // isVideoUri
    // -------------------------------------------------------------------------

    private val someUri = "content://media/external/1".toUri()

    /** [someUri] as the contract carries it; the repository converts back at the ContentResolver. */
    private val someMediaUri = someUri.toMediaUri()

    @Test
    fun `isVideoUri is true for a video mime type`() {
        every { contentResolver.getType(someUri) } returns "video/mp4"

        assertTrue(repository.isVideoUri(someMediaUri))
    }

    @Test
    fun `isVideoUri is false for an image mime type`() {
        every { contentResolver.getType(someUri) } returns "image/jpeg"

        assertFalse(repository.isVideoUri(someMediaUri))
    }

    /** A provider that reports no type is treated as a photo, per the contract's KDoc. */
    @Test
    fun `isVideoUri is false when the provider reports no type`() {
        every { contentResolver.getType(someUri) } returns null

        assertFalse(repository.isVideoUri(someMediaUri))
    }

    // -------------------------------------------------------------------------
    // hasContent
    // -------------------------------------------------------------------------

    private fun descriptorOfLength(length: Long) {
        val descriptor: AssetFileDescriptor = mockk(relaxed = true)
        every { descriptor.length } returns length
        every { contentResolver.openAssetFileDescriptor(someUri, "r") } returns descriptor
    }

    @Test
    fun `hasContent is true for a non-empty file`() {
        descriptorOfLength(1_024)

        assertTrue(repository.hasContent(someMediaUri))
    }

    /** A canceled capture leaves the empty temp file that was handed to the camera. */
    @Test
    fun `hasContent is false for an empty file`() {
        descriptorOfLength(0)

        assertFalse(repository.hasContent(someMediaUri))
    }

    /**
     * `UNKNOWN_LENGTH` is -1, and the contract says a provider that cannot report a size up front
     * counts as having content -- treating it as empty would silently drop a real capture.
     */
    @Test
    fun `hasContent is true when the length is unknown`() {
        descriptorOfLength(AssetFileDescriptor.UNKNOWN_LENGTH)

        assertTrue(repository.hasContent(someMediaUri))
    }

    @Test
    fun `hasContent is false when the provider returns no descriptor`() {
        every { contentResolver.openAssetFileDescriptor(someUri, "r") } returns null

        assertFalse(repository.hasContent(someMediaUri))
    }

    @Test
    fun `hasContent is false and logs when the provider throws`() {
        every { contentResolver.openAssetFileDescriptor(someUri, "r") } throws
                SecurityException("no permission")

        assertFalse(repository.hasContent(someMediaUri))
        verify { errorHandler.logError(any(), any()) }
    }

    // -------------------------------------------------------------------------
    // deleteFile
    // -------------------------------------------------------------------------

    @Test
    fun `deleteFile goes through the content resolver`() {
        every { contentResolver.delete(someUri, null, null) } returns 1

        repository.deleteFile(someMediaUri)

        verify { contentResolver.delete(someUri, null, null) }
    }

    /** Deletion is cleanup: a provider that refuses must not take the caller down with it. */
    @Test
    fun `deleteFile swallows and logs a provider failure`() {
        every { contentResolver.delete(someUri, null, null) } throws
                SecurityException("no permission")

        repository.deleteFile(someMediaUri)

        verify { errorHandler.logError(any(), any()) }
    }
}