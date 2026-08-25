package com.jiahan.smartcamera.explore

import com.jiahan.smartcamera.MainDispatcherRule
import com.jiahan.smartcamera.data.repository.AnalyticsRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExploreViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val photoRepository: PhotoRepository = mockk()
    private val analyticsRepository: AnalyticsRepository = mockk()
    private val errorHandler: ErrorHandler = mockk()

    private lateinit var viewModel: ExploreViewModel

    @Before
    fun setUp() {
        every { errorHandler.logError(any(), any()) } just runs
        every { errorHandler.getErrorMessage(any()) } returns "An error occurred"
        every { analyticsRepository.logExploreSearchCustomEvent(any()) } just runs
        coEvery { photoRepository.listPhotos(any(), any()) } returns Result.success(emptyList())
        viewModel = ExploreViewModel(photoRepository, analyticsRepository, errorHandler)
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

    private fun createViewModel() =
        ExploreViewModel(photoRepository, analyticsRepository, errorHandler)

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
        val page1 = (1..30).map { makePhoto("photo$it") }
        val page2 = (31..35).map { makePhoto("photo$it") }
        coEvery { photoRepository.listPhotos(1, any()) } returns Result.success(page1)
        coEvery { photoRepository.listPhotos(2, any()) } returns Result.success(page2)
        val vm = createViewModel()

        vm.loadMorePhotos()

        val content = vm.uiState.value.content as ExploreContent.Success
        assertEquals(35, content.photos.size)
    }

    @Test
    fun `loadMorePhotos does nothing when first page was smaller than pageSize`() = runTest {
        // 2 items < UNSPLASH_MAX_PAGE_SIZE(30) → hasMoreData = false
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
        val page1 = (1..30).map { makePhoto("photo$it") }
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
        val page1 = (1..30).map { makePhoto("photo$it") }
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
        val page1 = (1..30).map { makePhoto("photo$it") }
        coEvery { photoRepository.listPhotos(1, any()) } returns Result.success(page1)
        coEvery {
            photoRepository.listPhotos(2, any())
        } returns Result.failure(RuntimeException("page fail"))
        val vm = createViewModel()

        vm.loadMorePhotos()

        // Existing photos are unchanged despite the page-2 failure
        val content = vm.uiState.value.content as ExploreContent.Success
        assertEquals(30, content.photos.size)
        assertFalse(vm.uiState.value.isLoadingMore)
    }

    // -------------------------------------------------------------------------
    // Search — toggle
    // -------------------------------------------------------------------------

    @Test
    fun `toggleSearch flips isSearchActive from false to true`() = runTest {
        assertFalse(viewModel.uiState.value.isSearchActive)
        viewModel.toggleSearch()
        assertTrue(viewModel.uiState.value.isSearchActive)
    }

    @Test
    fun `toggleSearch when closing preserves searchQuery and searchContent`() = runTest {
        val results = listOf(makePhoto("s1"))
        coEvery { photoRepository.searchPhotos("cats", 1, any()) } returns Result.success(results)

        viewModel.toggleSearch()
        viewModel.updateSearchQuery("cats")
        viewModel.submitSearch()
        assertEquals(results, viewModel.uiState.value.searchPhotos)

        viewModel.toggleSearch()

        assertFalse(viewModel.uiState.value.isSearchActive)
        assertEquals("cats", viewModel.uiState.value.searchQuery)
        assertEquals(results, viewModel.uiState.value.searchPhotos)
    }

    @Test
    fun `reopening search after close shows preserved results without re-fetching`() = runTest {
        val results = listOf(makePhoto("s1"))
        coEvery { photoRepository.searchPhotos("cats", 1, any()) } returns Result.success(results)

        viewModel.toggleSearch()
        viewModel.updateSearchQuery("cats")
        viewModel.submitSearch()
        viewModel.toggleSearch() // close
        viewModel.toggleSearch() // reopen

        assertTrue(viewModel.uiState.value.isSearchActive)
        assertEquals("cats", viewModel.uiState.value.searchQuery)
        assertEquals(results, viewModel.uiState.value.searchPhotos)
        coVerify(exactly = 1) { photoRepository.searchPhotos(any(), any(), any()) }
    }

    @Test
    fun `closing search does not re-invoke photoRepository listPhotos`() = runTest {
        coEvery {
            photoRepository.searchPhotos(any(), any(), any())
        } returns Result.success(emptyList())

        viewModel.toggleSearch()
        viewModel.updateSearchQuery("dogs")
        viewModel.submitSearch()
        viewModel.toggleSearch()

        coVerify(exactly = 1) { photoRepository.listPhotos(any(), any()) } // only the init load
    }

    // -------------------------------------------------------------------------
    // Search — query updates
    // -------------------------------------------------------------------------

    @Test
    fun `updateSearchQuery updates searchQuery without calling searchPhotos`() = runTest {
        viewModel.updateSearchQuery("c")
        viewModel.updateSearchQuery("ca")
        viewModel.updateSearchQuery("cat")

        assertEquals("cat", viewModel.uiState.value.searchQuery)
        coVerify(exactly = 0) { photoRepository.searchPhotos(any(), any(), any()) }
    }

    // -------------------------------------------------------------------------
    // Search — submit
    // -------------------------------------------------------------------------

    @Test
    fun `submitSearch with blank query is a no-op`() = runTest {
        viewModel.updateSearchQuery("   ")
        viewModel.submitSearch()

        coVerify(exactly = 0) { photoRepository.searchPhotos(any(), any(), any()) }
        assertNull(viewModel.uiState.value.searchContent)
    }

    @Test
    fun `submitSearch calls searchPhotos with trimmed query and page 1`() = runTest {
        coEvery {
            photoRepository.searchPhotos("cats", 1, any())
        } returns Result.success(emptyList())

        viewModel.updateSearchQuery("  cats  ")
        viewModel.submitSearch()

        coVerify { photoRepository.searchPhotos("cats", 1, any()) }
    }

    @Test
    fun `submitSearch emits Success with results`() = runTest {
        val results = listOf(makePhoto("s1"), makePhoto("s2"))
        coEvery { photoRepository.searchPhotos("cats", 1, any()) } returns Result.success(results)

        viewModel.updateSearchQuery("cats")
        viewModel.submitSearch()

        assertEquals(ExploreContent.Success(results), viewModel.uiState.value.searchContent)
    }

    @Test
    fun `submitSearch emits Error state on repository failure`() = runTest {
        val exception = RuntimeException("search failed")
        coEvery {
            photoRepository.searchPhotos("cats", 1, any())
        } returns Result.failure(exception)
        every { errorHandler.getErrorMessage(exception) } returns "search failed"

        viewModel.updateSearchQuery("cats")
        viewModel.submitSearch()

        val content = viewModel.uiState.value.searchContent
        assertTrue(content is ExploreContent.Error)
        assertEquals("search failed", (content as ExploreContent.Error).message)
    }

    // -------------------------------------------------------------------------
    // Search — load more
    // -------------------------------------------------------------------------

    @Test
    fun `loadMoreSearchResults appends second page to existing search results`() = runTest {
        val page1 = (1..30).map { makePhoto("s$it") }
        val page2 = (31..35).map { makePhoto("s$it") }
        coEvery { photoRepository.searchPhotos("cats", 1, any()) } returns Result.success(page1)
        coEvery { photoRepository.searchPhotos("cats", 2, any()) } returns Result.success(page2)

        viewModel.updateSearchQuery("cats")
        viewModel.submitSearch()
        viewModel.loadMoreSearchResults()

        val content = viewModel.uiState.value.searchContent as ExploreContent.Success
        assertEquals(35, content.photos.size)
    }

    @Test
    fun `loadMoreSearchResults does nothing when first search page was smaller than pageSize`() =
        runTest {
            coEvery {
                photoRepository.searchPhotos("cats", any(), any())
            } returns Result.success(listOf(makePhoto("s1")))

            viewModel.updateSearchQuery("cats")
            viewModel.submitSearch()

            // Reset recorded calls (keep stubs) so we measure only what loadMoreSearchResults
            // triggers
            clearMocks(photoRepository, answers = false)

            viewModel.loadMoreSearchResults() // searchHasMoreData = false → should be a no-op

            coVerify(exactly = 0) { photoRepository.searchPhotos(any(), any(), any()) }
        }

    @Test
    fun `loadMoreSearchResults does nothing if called before any search was submitted`() = runTest {
        viewModel.loadMoreSearchResults()

        coVerify(exactly = 0) { photoRepository.searchPhotos(any(), any(), any()) }
        assertNull(viewModel.uiState.value.searchContent)
    }

    @Test
    fun `isSearchLoadingMore is true while loadMoreSearchResults is in progress`() = runTest {
        val paused = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(paused)
        val page1 = (1..30).map { makePhoto("s$it") }
        coEvery { photoRepository.searchPhotos("cats", 1, any()) } returns Result.success(page1)
        coEvery { photoRepository.searchPhotos("cats", 2, any()) } coAnswers {
            delay(1.seconds)
            Result.success(emptyList())
        }
        val vm = createViewModel()
        advanceUntilIdle() // let init fetch complete

        vm.updateSearchQuery("cats")
        vm.submitSearch()
        advanceUntilIdle() // let the first search page load

        vm.loadMoreSearchResults()
        advanceTimeBy(1.milliseconds) // let it start; page-2 fetch suspends at delay(1s)
        assertTrue(vm.uiState.value.isSearchLoadingMore)

        advanceUntilIdle() // complete the delay
        assertFalse(vm.uiState.value.isSearchLoadingMore)
    }

    @Test
    fun `loadMoreSearchResults failure preserves existing search Success state`() = runTest {
        val page1 = (1..30).map { makePhoto("s$it") }
        coEvery { photoRepository.searchPhotos("cats", 1, any()) } returns Result.success(page1)
        coEvery {
            photoRepository.searchPhotos("cats", 2, any())
        } returns Result.failure(RuntimeException("page fail"))

        viewModel.updateSearchQuery("cats")
        viewModel.submitSearch()
        viewModel.loadMoreSearchResults()

        val content = viewModel.uiState.value.searchContent as ExploreContent.Success
        assertEquals(30, content.photos.size)
        assertFalse(viewModel.uiState.value.isSearchLoadingMore)
    }

    @Test
    fun `submitting a new search query resets search pagination to page 1`() = runTest {
        val catsPage1 = (1..30).map { makePhoto("cat$it") }
        val dogsPage1 = listOf(makePhoto("dog1"))
        coEvery {
            photoRepository.searchPhotos("cats", 1, any())
        } returns Result.success(catsPage1)
        coEvery {
            photoRepository.searchPhotos("dogs", 1, any())
        } returns Result.success(dogsPage1)

        viewModel.updateSearchQuery("cats")
        viewModel.submitSearch()

        viewModel.updateSearchQuery("dogs")
        viewModel.submitSearch()

        coVerify { photoRepository.searchPhotos("dogs", 1, any()) }
        val content = viewModel.uiState.value.searchContent as ExploreContent.Success
        assertEquals(dogsPage1, content.photos)
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
