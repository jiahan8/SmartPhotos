package com.jiahan.smartcamera.explore

import com.jiahan.smartcamera.MainDispatcherRule
import com.jiahan.smartcamera.data.repository.PhotoRepository
import com.jiahan.smartcamera.domain.Photo
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.ErrorTag
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExploreViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val photoRepository: PhotoRepository = mockk()
    private val errorHandler: ErrorHandler = mockk()

    private lateinit var viewModel: ExploreViewModel

    @Before
    fun setUp() {
        every { errorHandler.logError(any(), any()) } just runs
        every { errorHandler.getErrorMessage(any()) } returns "An error occurred"
        coEvery { photoRepository.listPhotos(any(), any()) } returns Result.success(emptyList())
        viewModel = ExploreViewModel(photoRepository, errorHandler)
    }

    @After
    fun tearDown() = unmockkAll()

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun makePhoto(id: String) = Photo(
        id = id,
        imageUrl = "https://example.com/$id.jpg",
        thumbUrl = "https://example.com/$id-thumb.jpg",
        width = 100,
        height = 100,
        username = "testUser"
    )

    private fun createViewModel() = ExploreViewModel(photoRepository, errorHandler)

    // -------------------------------------------------------------------------
    // Initial load
    // -------------------------------------------------------------------------

    @Test
    fun `init emits Success with empty list when repository returns empty`() = runTest {
        val content = viewModel.uiState.value.content
        assertTrue(content is ExploreContent.Success)
        assertTrue((content as ExploreContent.Success).photos.isEmpty())
    }

    @Test
    fun `init emits Success with photos when repository returns data`() = runTest {
        val photos = listOf(makePhoto("a"), makePhoto("b"))
        coEvery { photoRepository.listPhotos(1, any()) } returns Result.success(photos)
        val vm = createViewModel()
        assertEquals(ExploreContent.Success(photos), vm.uiState.value.content)
    }

    @Test
    fun `init emits Error state when repository fails`() = runTest {
        val exception = RuntimeException("network error")
        coEvery { photoRepository.listPhotos(any(), any()) } returns Result.failure(exception)
        every { errorHandler.getErrorMessage(exception) } returns "network error"
        val vm = createViewModel()

        val content = vm.uiState.value.content
        assertTrue(content is ExploreContent.Error)
        assertEquals("network error", (content as ExploreContent.Error).message)
    }

    // -------------------------------------------------------------------------
    // Refresh
    // -------------------------------------------------------------------------

    @Test
    fun `refresh reloads first page and updates state`() = runTest {
        val refreshedPhotos = listOf(makePhoto("r1"), makePhoto("r2"))
        coEvery { photoRepository.listPhotos(1, any()) } returns Result.success(refreshedPhotos)

        viewModel.refresh()

        assertEquals(ExploreContent.Success(refreshedPhotos), viewModel.uiState.value.content)
    }

    @Test
    fun `refresh always requests the first page`() = runTest {
        viewModel.refresh()
        coVerify { photoRepository.listPhotos(1, any()) }
    }

    @Test
    fun `isRefreshing is false after refresh completes`() = runTest {
        viewModel.refresh()
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun `refresh failure replaces existing photos with Error state`() = runTest {
        val initialPhotos = listOf(makePhoto("a"), makePhoto("b"))
        coEvery { photoRepository.listPhotos(1, any()) } returns Result.success(initialPhotos)
        val vm = createViewModel()
        assertTrue(vm.uiState.value.content is ExploreContent.Success)

        val exception = RuntimeException("refresh failed")
        coEvery { photoRepository.listPhotos(1, any()) } returns Result.failure(exception)
        every { errorHandler.getErrorMessage(exception) } returns "refresh failed"

        vm.refresh()

        val content = vm.uiState.value.content
        assertTrue(content is ExploreContent.Error)
        assertEquals("refresh failed", (content as ExploreContent.Error).message)
        assertFalse(vm.uiState.value.isRefreshing)
    }

    // -------------------------------------------------------------------------
    // Load more
    // -------------------------------------------------------------------------

    @Test
    fun `loadMorePhotos appends second page to existing photos`() = runTest {
        val page1 = (1..10).map { makePhoto("photo$it") }
        val page2 = (11..15).map { makePhoto("photo$it") }
        coEvery { photoRepository.listPhotos(1, any()) } returns Result.success(page1)
        coEvery { photoRepository.listPhotos(2, any()) } returns Result.success(page2)
        val vm = createViewModel()

        vm.loadMorePhotos()

        val content = vm.uiState.value.content as ExploreContent.Success
        assertEquals(15, content.photos.size)
    }

    @Test
    fun `loadMorePhotos does nothing when first page was smaller than pageSize`() = runTest {
        // 2 items < DEFAULT_PAGE_SIZE(10) → hasMoreData = false
        val twoPhotos = listOf(makePhoto("a"), makePhoto("b"))
        coEvery { photoRepository.listPhotos(any(), any()) } returns Result.success(twoPhotos)
        val vm = createViewModel() // triggers the init fetch

        // Reset recorded calls (keep stubs) so we measure only what loadMorePhotos triggers
        clearMocks(photoRepository, answers = false)

        vm.loadMorePhotos() // hasMoreData = false → should be a no-op

        coVerify(exactly = 0) { photoRepository.listPhotos(any(), any()) }
    }

    @Test
    fun `isLoadingMore is false after loadMorePhotos completes`() = runTest {
        val page1 = (1..10).map { makePhoto("photo$it") }
        coEvery { photoRepository.listPhotos(1, any()) } returns Result.success(page1)
        coEvery { photoRepository.listPhotos(2, any()) } returns Result.success(emptyList())
        val vm = createViewModel()

        vm.loadMorePhotos()

        assertFalse(vm.uiState.value.isLoadingMore)
    }

    @Test
    fun `isLoadingMore is true while loadMorePhotos is in progress`() = runTest {
        val paused = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(paused)
        val page1 = (1..10).map { makePhoto("photo$it") }
        coEvery { photoRepository.listPhotos(1, any()) } returns Result.success(page1)
        coEvery { photoRepository.listPhotos(2, any()) } coAnswers {
            delay(1.seconds)
            Result.success(emptyList())
        }
        val vm = createViewModel()
        advanceUntilIdle() // let init fetch complete

        vm.loadMorePhotos()
        advanceTimeBy(1.milliseconds) // let loadMorePhotos start; page-2 fetch suspends at delay(1s)
        assertTrue(vm.uiState.value.isLoadingMore)

        advanceUntilIdle() // complete the delay
        assertFalse(vm.uiState.value.isLoadingMore)
    }

    @Test
    fun `loadMorePhotos failure preserves existing Success state`() = runTest {
        val page1 = (1..10).map { makePhoto("photo$it") }
        coEvery { photoRepository.listPhotos(1, any()) } returns Result.success(page1)
        coEvery {
            photoRepository.listPhotos(2, any())
        } returns Result.failure(RuntimeException("page fail"))
        val vm = createViewModel()

        vm.loadMorePhotos()

        // Existing photos are unchanged despite the page-2 failure
        val content = vm.uiState.value.content as ExploreContent.Success
        assertEquals(10, content.photos.size)
        assertFalse(vm.uiState.value.isLoadingMore)
    }

    // -------------------------------------------------------------------------
    // logImageLoadError
    // -------------------------------------------------------------------------

    @Test
    fun `logImageLoadError logs through ErrorHandler with ImageLoad tag`() = runTest {
        val exception = RuntimeException("decode failed")

        viewModel.logImageLoadError(exception)

        verify { errorHandler.logError(exception, ErrorTag.IMAGE_LOAD) }
    }
}
