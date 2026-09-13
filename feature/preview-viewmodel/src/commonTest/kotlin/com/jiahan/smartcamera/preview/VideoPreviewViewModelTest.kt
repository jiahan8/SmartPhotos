package com.jiahan.smartcamera.preview

import app.cash.turbine.test
import com.jiahan.smartcamera.domain.MediaUri
import com.jiahan.smartcamera.fake.FakeErrorHandler
import com.jiahan.smartcamera.fake.FakeMediaCacheRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * [VideoPreviewViewModel]'s suite, moved into `commonTest` the way `PhotoPreviewViewModelTest` was
 * and for the same reasons: the route decode and its cases went to `HiltVideoPreviewViewModelTest`,
 * and [FakeMediaCacheRepository] replaced the mock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VideoPreviewViewModelTest {

    private val mediaCacheRepository = FakeMediaCacheRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(videoSource: VideoSource) = VideoPreviewViewModel(
        videoSource = videoSource,
        errorHandler = FakeErrorHandler(),
        mediaCacheRepository = mediaCacheRepository
    )

    /** Suspends the download at [delay] so a test can observe the in-flight state. */
    private fun holdDownloadInFlight() {
        mediaCacheRepository.downloadAnswer = { _, _ ->
            delay(1.seconds)
            MediaUri(CACHE_URI)
        }
    }

    private companion object {
        const val CACHE_URI = "file:///data/user/0/com.jiahan.smartcamera/cache/clip.mp4"
    }

    // -------------------------------------------------------------------------
    // Share
    // -------------------------------------------------------------------------

    @Test
    fun `shareVideo with local uri emits that uri directly`() = runTest {
        val localUri = MediaUri("content://media/external/video/media/42")

        val vm = createViewModel(VideoSource.LocalUri(localUri))

        vm.shareEvent.test {
            vm.shareVideo()
            assertEquals(localUri, awaitItem())
        }
        assertTrue(mediaCacheRepository.requestedDownloads.isEmpty())
    }

    @Test
    fun `shareVideo with remote url downloads to cache file and emits it`() = runTest {
        val url = "https://example.com/clip.mp4"
        mediaCacheRepository.downloadResult = MediaUri(CACHE_URI)

        val vm = createViewModel(VideoSource.RemoteUrl(url))

        vm.shareEvent.test {
            vm.shareVideo()
            assertEquals(MediaUri(CACHE_URI), awaitItem())
        }
        assertEquals(listOf(url to true), mediaCacheRepository.requestedDownloads)
    }

    @Test
    fun `shareVideo reports SHARE_FAILED when the download fails`() = runTest {
        val vm = createViewModel(VideoSource.RemoteUrl("https://example.com/clip.mp4"))

        vm.actionError.test {
            vm.shareVideo()
            assertEquals(MediaPreviewError.SHARE_FAILED, awaitItem())
        }
    }

    @Test
    fun `shareVideo raises no share event when the download fails`() = runTest {
        val vm = createViewModel(VideoSource.RemoteUrl("https://example.com/clip.mp4"))

        vm.shareEvent.test {
            vm.shareVideo()
            expectNoEvents()
        }
    }

    // -------------------------------------------------------------------------
    // isSharing
    //
    // Sharing a remote video is a download, so the button stays on screen for as long as the
    // network takes -- long enough to be tapped again. The flag is what the screen disables it
    // with, and what the second tap is turned away by.
    // -------------------------------------------------------------------------

    @Test
    fun `isSharing is false before anything is shared`() {
        val vm = createViewModel(VideoSource.RemoteUrl("https://example.com/clip.mp4"))

        assertFalse(vm.isSharing.value)
    }

    @Test
    fun `isSharing is true while the download is in flight and false once it settles`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        holdDownloadInFlight()
        val vm = createViewModel(VideoSource.RemoteUrl("https://example.com/clip.mp4"))

        vm.shareVideo()
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
    fun `a second shareVideo while one is in flight is ignored`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        holdDownloadInFlight()
        val vm = createViewModel(VideoSource.RemoteUrl("https://example.com/clip.mp4"))

        vm.shareVideo()
        advanceTimeBy(1.milliseconds) // first download suspended, isSharing is set
        vm.shareVideo()
        advanceUntilIdle()

        assertEquals(1, mediaCacheRepository.requestedDownloads.size)
    }

    /** Reset in a `finally`, so a failed share does not leave the button wedged. */
    @Test
    fun `isSharing is false after a failed share`() = runTest {
        val vm = createViewModel(VideoSource.RemoteUrl("https://example.com/clip.mp4"))

        vm.shareVideo()

        assertFalse(vm.isSharing.value)
    }

    /** A local share never touches the repository, so the flag has to come back down anyway. */
    @Test
    fun `isSharing is false after a local share`() = runTest {
        val vm = createViewModel(
            VideoSource.LocalUri(MediaUri("content://media/external/video/media/42"))
        )

        vm.shareEvent.test {
            vm.shareVideo()
            awaitItem()
        }

        assertFalse(vm.isSharing.value)
    }
}