package com.jiahan.smartcamera.preview

import android.app.Application
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.jiahan.smartcamera.MainDispatcherRule
import com.jiahan.smartcamera.data.repository.MediaFileRepository
import com.jiahan.smartcamera.util.ErrorHandler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * [PhotoPreviewViewModel] parses its typed nav route via [androidx.navigation.toRoute], whose
 * internal `RouteDecoder` constructs a real [android.os.Bundle] — that needs Robolectric's shadow
 * to work outside a real Android runtime, hence Robolectric here.
 *
 * A plain [Application] stands in for `MyApp` (as in `BaseScreenshotTest`): the real one installs
 * the Firebase App Check provider in `onCreate()`, which throws under Robolectric because no
 * default `FirebaseApp` is initialized there.
 *
 * Robolectric being present is also why nothing here stubs `Uri.parse`. The route carries a
 * [MediaSourceType.LOCAL] location as a `String` and `photoSource` turns it back into a `Uri`, so a
 * static mock would replace the one conversion these assertions exist to check -- the same argument
 * `MediaUriExtTest` makes for testing that pair against real parsing rather than a stub.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class PhotoPreviewViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val mediaFileRepository = mockk<MediaFileRepository>()

    @After
    fun tearDown() = unmockkAll()

    private fun createViewModel(type: MediaSourceType, source: String): PhotoPreviewViewModel {
        val savedStateHandle =
            SavedStateHandle(mapOf("type" to type, "source" to source))
        return PhotoPreviewViewModel(
            savedStateHandle,
            mockk<ErrorHandler>(relaxed = true),
            mediaFileRepository
        )
    }

    /** Suspends the download at [delay] so a test can observe the in-flight state. */
    private fun stubSlowDownload(url: String) {
        coEvery { mediaFileRepository.downloadToCacheFile(url, isVideo = false) } coAnswers {
            delay(1.seconds)
            CACHE_URI.toUri()
        }
    }

    private companion object {
        const val CACHE_URI = "file:///data/user/0/com.jiahan.smartcamera/cache/photo.jpg"
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
        val uriString = "content://media/external/images/media/1"

        val vm = createViewModel(MediaSourceType.LOCAL, uriString)

        val source = vm.photoSource
        assertTrue(source is PhotoSource.LocalUri)
        assertEquals(uriString.toUri(), (source as PhotoSource.LocalUri).uri)
    }

    /** A location the picker can hand over that a naive `String` wrapper would mangle. */
    @Test
    fun `local type preserves a percent-encoded uri`() {
        val uriString = "content://media/external/images/media/My%20Photo%20%281%29.jpg"

        val vm = createViewModel(MediaSourceType.LOCAL, uriString)

        assertEquals(uriString, (vm.photoSource as PhotoSource.LocalUri).uri.toString())
    }

    // -------------------------------------------------------------------------
    // Share
    // -------------------------------------------------------------------------

    @Test
    fun `sharePhoto with local uri emits that uri directly`() = runTest {
        val uriString = "content://media/external/images/media/1"

        val vm = createViewModel(MediaSourceType.LOCAL, uriString)

        vm.shareEvent.test {
            vm.sharePhoto()
            assertEquals(uriString.toUri(), awaitItem())
        }
    }

    @Test
    fun `sharePhoto with remote url downloads to cache file and emits it`() = runTest {
        val url = "https://example.com/photo.jpg"
        val downloadedUri = CACHE_URI.toUri()
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
    fun `sharePhoto reports SHARE_FAILED when the download fails`() = runTest {
        val url = "https://example.com/photo.jpg"
        coEvery { mediaFileRepository.downloadToCacheFile(url, isVideo = false) } returns null

        val vm = createViewModel(MediaSourceType.REMOTE, url)

        vm.actionError.test {
            vm.sharePhoto()
            assertEquals(MediaPreviewError.SHARE_FAILED, awaitItem())
        }
    }

    @Test
    fun `sharePhoto raises no share event when the download fails`() = runTest {
        val url = "https://example.com/photo.jpg"
        coEvery { mediaFileRepository.downloadToCacheFile(url, isVideo = false) } returns null

        val vm = createViewModel(MediaSourceType.REMOTE, url)

        vm.shareEvent.test {
            vm.sharePhoto()
            expectNoEvents()
        }
    }

    // -------------------------------------------------------------------------
    // isSharing
    //
    // Sharing a remote photo is a download, so the button stays on screen for as long as the
    // network takes -- long enough to be tapped again. The flag is what the screen disables it
    // with, and what the second tap is turned away by.
    // -------------------------------------------------------------------------

    @Test
    fun `isSharing is false before anything is shared`() {
        val vm = createViewModel(MediaSourceType.REMOTE, "https://example.com/photo.jpg")

        assertFalse(vm.isSharing.value)
    }

    @Test
    fun `isSharing is true while the download is in flight and false once it settles`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val url = "https://example.com/photo.jpg"
        stubSlowDownload(url)
        val vm = createViewModel(MediaSourceType.REMOTE, url)

        vm.sharePhoto()
        advanceTimeBy(1.milliseconds) // the download is suspended, nothing has settled
        assertTrue(vm.isSharing.value)

        advanceUntilIdle()
        assertFalse(vm.isSharing.value)
    }

    /**
     * The double-tap guard. Without it the second pass downloads the file again and emits a second
     * `shareEvent`, opening a chooser on top of the one already up.
     */
    @Test
    fun `a second sharePhoto while one is in flight is ignored`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val url = "https://example.com/photo.jpg"
        stubSlowDownload(url)
        val vm = createViewModel(MediaSourceType.REMOTE, url)

        vm.sharePhoto()
        advanceTimeBy(1.milliseconds) // first download suspended, isSharing is set
        vm.sharePhoto()
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaFileRepository.downloadToCacheFile(url, isVideo = false) }
    }

    /** Reset in a `finally`, so a failed share does not leave the button wedged. */
    @Test
    fun `isSharing is false after a failed share`() = runTest {
        val url = "https://example.com/photo.jpg"
        coEvery { mediaFileRepository.downloadToCacheFile(url, isVideo = false) } returns null
        val vm = createViewModel(MediaSourceType.REMOTE, url)

        vm.sharePhoto()

        assertFalse(vm.isSharing.value)
    }

    /** A local share never touches the repository, so the flag has to come back down anyway. */
    @Test
    fun `isSharing is false after a local share`() = runTest {
        val vm = createViewModel(MediaSourceType.LOCAL, "content://media/external/images/media/1")

        vm.shareEvent.test {
            vm.sharePhoto()
            awaitItem()
        }

        assertFalse(vm.isSharing.value)
    }
}