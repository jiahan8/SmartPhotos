package com.jiahan.smartcamera.preview

import android.app.Application
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.jiahan.smartcamera.data.repository.MediaFileRepository
import com.jiahan.smartcamera.navigation.MediaSourceType
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.ResourceProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * [PhotoPreviewViewModel] parses its typed nav route via [androidx.navigation.toRoute], whose
 * internal [androidx.navigation.serialization.RouteDecoder] constructs a real [android.os.Bundle]
 * — that needs Robolectric's shadow to work outside a real Android runtime, hence Robolectric here.
 *
 * A plain [Application] stands in for [com.jiahan.smartcamera.MyApp] (as in
 * [com.jiahan.smartcamera.screenshot.BaseScreenshotTest]): the real one installs the Firebase App
 * Check provider in `onCreate()`, which throws under Robolectric because no default `FirebaseApp`
 * is initialized there.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class PhotoPreviewViewModelTest {

    private val mediaFileRepository = mockk<MediaFileRepository>()
    private val resourceProvider = mockk<ResourceProvider>(relaxed = true)

    private fun createViewModel(type: MediaSourceType, source: String): PhotoPreviewViewModel {
        val savedStateHandle =
            SavedStateHandle(mapOf("type" to type, "source" to source))
        return PhotoPreviewViewModel(
            savedStateHandle,
            mockk<ErrorHandler>(relaxed = true),
            mediaFileRepository,
            resourceProvider
        )
    }

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    // -------------------------------------------------------------------------
    // Remote URL
    // -------------------------------------------------------------------------

    @Test
    fun `remote type with url returns RemoteUrl`() {
        val url = "https://example.com/photo.jpg?size=large&id=1"
        val vm = createViewModel(MediaSourceType.REMOTE, url)

        val source = vm.photoSource
        assertTrue(source is PhotoSource.RemoteUrl)
        assertEquals(url, (source as PhotoSource.RemoteUrl).url)
    }

    // -------------------------------------------------------------------------
    // Local URI
    // -------------------------------------------------------------------------

    @Test
    fun `local type with uri returns LocalUri`() {
        val uriString = "content://media/external/images/1"
        val mockUri = mockk<Uri>()
        every { Uri.parse(uriString) } returns mockUri

        val vm = createViewModel(MediaSourceType.LOCAL, uriString)

        val source = vm.photoSource
        assertTrue(source is PhotoSource.LocalUri)
        assertEquals(mockUri, (source as PhotoSource.LocalUri).uri)
    }

    // -------------------------------------------------------------------------
    // Share
    // -------------------------------------------------------------------------

    @Test
    fun `sharePhoto with local uri emits that uri directly`() = runTest {
        val uriString = "content://media/external/images/1"
        val mockUri = mockk<Uri>()
        every { Uri.parse(uriString) } returns mockUri

        val vm = createViewModel(MediaSourceType.LOCAL, uriString)

        vm.shareEvent.test {
            vm.sharePhoto()
            assertEquals(mockUri, awaitItem())
        }
    }

    @Test
    fun `sharePhoto with remote url downloads to cache file and emits it`() = runTest {
        val url = "https://example.com/photo.jpg"
        val downloadedUri = mockk<Uri>()
        coEvery {
            mediaFileRepository.downloadToCacheFile(
                url,
                isVideo = false
            )
        } returns downloadedUri

        val vm = createViewModel(MediaSourceType.REMOTE, url)

        vm.shareEvent.test {
            vm.sharePhoto()
            assertEquals(downloadedUri, awaitItem())
        }
    }

    @Test
    fun `sharePhoto emits actionError when download fails`() = runTest {
        val url = "https://example.com/photo.jpg"
        coEvery { mediaFileRepository.downloadToCacheFile(url, isVideo = false) } returns null

        val vm = createViewModel(MediaSourceType.REMOTE, url)

        vm.actionError.test {
            vm.sharePhoto()
            awaitItem()
        }
    }
}