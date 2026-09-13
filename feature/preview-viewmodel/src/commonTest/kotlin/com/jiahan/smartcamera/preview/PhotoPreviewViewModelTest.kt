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
 * [PhotoPreviewViewModel]'s suite, in `commonTest` beside its subject, on :core:domain-testing's
 * fakes with [Dispatchers.setMain] called directly -- `ExploreViewModelTest` records why each of
 * those.
 *
 * It used to run under Robolectric, because the ViewModel decoded its route with `toRoute`, whose
 * `RouteDecoder` builds a real `android.os.Bundle`. That decode is `HiltPhotoPreviewViewModel`'s
 * now, and the three cases about what a route becomes went with it to that class's suite in
 * :feature:preview; this one hands the ViewModel a [PhotoSource] and asserts the [MediaUri] a share
 * emits. [FakeMediaCacheRepository] stands where a strict mock did: its request log for the
 * verified calls, `downloadAnswer` for the stub that held a download in flight.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PhotoPreviewViewModelTest {

    private val mediaCacheRepository = FakeMediaCacheRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(photoSource: PhotoSource) = PhotoPreviewViewModel(
        photoSource = photoSource,
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
        const val CACHE_URI = "file:///data/user/0/com.jiahan.smartcamera/cache/photo.jpg"
    }

    // -------------------------------------------------------------------------
    // Share
    // -------------------------------------------------------------------------

    @Test
    fun `sharePhoto with local uri emits that uri directly`() = runTest {
        val localUri = MediaUri("content://media/external/images/media/1")

        val vm = createViewModel(PhotoSource.LocalUri(localUri))

        vm.shareEvent.test {
            vm.sharePhoto()
            assertEquals(localUri, awaitItem())
        }
        assertTrue(mediaCacheRepository.requestedDownloads.isEmpty())
    }

    @Test
    fun `sharePhoto with remote url downloads to cache file and emits it`() = runTest {
        val url = "https://example.com/photo.jpg"
        mediaCacheRepository.downloadResult = MediaUri(CACHE_URI)

        val vm = createViewModel(PhotoSource.RemoteUrl(url))

        vm.shareEvent.test {
            vm.sharePhoto()
            assertEquals(MediaUri(CACHE_URI), awaitItem())
        }
        assertEquals(listOf(url to false), mediaCacheRepository.requestedDownloads)
    }

    @Test
    fun `sharePhoto reports SHARE_FAILED when the download fails`() = runTest {
        val vm = createViewModel(PhotoSource.RemoteUrl("https://example.com/photo.jpg"))

        vm.actionError.test {
            vm.sharePhoto()
            assertEquals(MediaPreviewError.SHARE_FAILED, awaitItem())
        }
    }

    @Test
    fun `sharePhoto raises no share event when the download fails`() = runTest {
        val vm = createViewModel(PhotoSource.RemoteUrl("https://example.com/photo.jpg"))

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
        val vm = createViewModel(PhotoSource.RemoteUrl("https://example.com/photo.jpg"))

        assertFalse(vm.isSharing.value)
    }

    @Test
    fun `isSharing is true while the download is in flight and false once it settles`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        holdDownloadInFlight()
        val vm = createViewModel(PhotoSource.RemoteUrl("https://example.com/photo.jpg"))

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
        holdDownloadInFlight()
        val vm = createViewModel(PhotoSource.RemoteUrl("https://example.com/photo.jpg"))

        vm.sharePhoto()
        advanceTimeBy(1.milliseconds) // first download suspended, isSharing is set
        vm.sharePhoto()
        advanceUntilIdle()

        assertEquals(1, mediaCacheRepository.requestedDownloads.size)
    }

    /** Reset in a `finally`, so a failed share does not leave the button wedged. */
    @Test
    fun `isSharing is false after a failed share`() = runTest {
        val vm = createViewModel(PhotoSource.RemoteUrl("https://example.com/photo.jpg"))

        vm.sharePhoto()

        assertFalse(vm.isSharing.value)
    }

    /** A local share never touches the repository, so the flag has to come back down anyway. */
    @Test
    fun `isSharing is false after a local share`() = runTest {
        val vm = createViewModel(
            PhotoSource.LocalUri(MediaUri("content://media/external/images/media/1"))
        )

        vm.shareEvent.test {
            vm.sharePhoto()
            awaitItem()
        }

        assertFalse(vm.isSharing.value)
    }
}