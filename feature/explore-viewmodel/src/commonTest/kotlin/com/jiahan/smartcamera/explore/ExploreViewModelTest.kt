package com.jiahan.smartcamera.explore

import com.jiahan.smartcamera.domain.Photo
import com.jiahan.smartcamera.domain.PhotoPage
import com.jiahan.smartcamera.fake.FakeAnalyticsRepository
import com.jiahan.smartcamera.fake.FakeErrorHandler
import com.jiahan.smartcamera.fake.FakePhotoRepository
import com.jiahan.smartcamera.util.ErrorMessage
import com.jiahan.smartcamera.util.ErrorTag
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * [ExploreViewModel]'s suite, in `commonTest` beside its subject, so one source runs on the JVM and
 * on the Apple targets.
 *
 * That is what shaped it. JUnit rules and mockk exist on neither Apple target, so the suite calls
 * [Dispatchers.setMain] itself where `MainDispatcherRule` used to, and drives
 * [FakePhotoRepository] where it stubbed a mock. Per-page answers stay strict -- a page or query a
 * test did not expect fails it, as an unstubbed mock call did -- and the fake's request logs stand
 * in for `coVerify`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExploreViewModelTest {

    private val photoRepository = FakePhotoRepository()
    private val analyticsRepository = FakeAnalyticsRepository()
    private val errorHandler = FakeErrorHandler()

    private lateinit var viewModel: ExploreViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = createViewModel()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun makePhoto(id: String) = Photo(
        id = id,
        photoUrl = "https://example.com/$id.jpg",
        thumbnailUrl = "https://example.com/$id-thumb.jpg",
        width = 100,
        height = 100,
        username = "testUser"
    )

    private fun createViewModel() =
        ExploreViewModel(photoRepository, analyticsRepository, errorHandler)

    private fun page(photos: List<Photo>, hasMore: Boolean) =
        Result.success(PhotoPage(photos, hasMore))

    private fun unexpectedListPage(page: Int): Nothing =
        fail("listPhotos was not expected to ask for page $page")

    private fun unexpectedSearch(query: String, page: Int): Nothing =
        fail("searchPhotos was not expected to ask for \"$query\" page $page")

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
        photoRepository.setPhotos(photos)
        val vm = createViewModel()
        assertEquals(ExploreContent.Success(photos), vm.uiState.value.content)
    }

    @Test
    fun `init emits Error state when repository fails`() = runTest {
        photoRepository.listResult = Result.failure(RuntimeException("network error"))
        val vm = createViewModel()

        val content = vm.uiState.value.content
        assertTrue(content is ExploreContent.Error)
        assertEquals(
            ErrorMessage.Unlocalized("network error"),
            (content as ExploreContent.Error).message
        )
    }

    // -------------------------------------------------------------------------
    // Refresh
    // -------------------------------------------------------------------------

    @Test
    fun `refresh reloads first page and updates state`() = runTest {
        val refreshedPhotos = listOf(makePhoto("r1"), makePhoto("r2"))
        photoRepository.setPhotos(refreshedPhotos)

        viewModel.refresh()

        assertEquals(ExploreContent.Success(refreshedPhotos), viewModel.uiState.value.content)
    }

    @Test
    fun `refresh always requests the first page`() = runTest {
        photoRepository.requestedListPages.clear() // the init load's request is not this test's

        viewModel.refresh()

        assertEquals(listOf(1), photoRepository.requestedListPages)
    }

    @Test
    fun `isRefreshing is false after refresh completes`() = runTest {
        viewModel.refresh()
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun `refresh failure replaces existing photos with Error state`() = runTest {
        photoRepository.setPhotos(listOf(makePhoto("a"), makePhoto("b")))
        val vm = createViewModel()
        assertTrue(vm.uiState.value.content is ExploreContent.Success)

        photoRepository.listResult = Result.failure(RuntimeException("refresh failed"))

        vm.refresh()

        val content = vm.uiState.value.content
        assertTrue(content is ExploreContent.Error)
        assertEquals(
            ErrorMessage.Unlocalized("refresh failed"),
            (content as ExploreContent.Error).message
        )
        assertFalse(vm.uiState.value.isRefreshing)
    }

    // -------------------------------------------------------------------------
    // Load more
    // -------------------------------------------------------------------------

    @Test
    fun `loadMorePhotos appends second page to existing photos`() = runTest {
        val page1 = (1..30).map { makePhoto("photo$it") }
        val page2 = (31..35).map { makePhoto("photo$it") }
        photoRepository.listAnswer = { page ->
            when (page) {
                1 -> page(page1, hasMore = true)
                2 -> page(page2, hasMore = false)
                else -> unexpectedListPage(page)
            }
        }
        val vm = createViewModel()

        vm.loadMorePhotos()

        val content = vm.uiState.value.content as ExploreContent.Success
        assertEquals(35, content.photos.size)
    }

    @Test
    fun `loadMorePhotos does nothing when the page reports no more data`() = runTest {
        photoRepository.setPhotos(listOf(makePhoto("a"), makePhoto("b")), hasMore = false)
        val vm = createViewModel() // triggers the init fetch

        // Forget the requests so far, keeping the answers, so this measures only loadMorePhotos.
        photoRepository.requestedListPages.clear()

        vm.loadMorePhotos() // hasMore = false → should be a no-op

        assertTrue(photoRepository.requestedListPages.isEmpty())
    }

    @Test
    fun `isLoadingMore is false after loadMorePhotos completes`() = runTest {
        val page1 = (1..30).map { makePhoto("photo$it") }
        photoRepository.listAnswer = { page ->
            when (page) {
                1 -> page(page1, hasMore = true)
                2 -> page(emptyList(), hasMore = false)
                else -> unexpectedListPage(page)
            }
        }
        val vm = createViewModel()

        vm.loadMorePhotos()

        assertFalse(vm.uiState.value.isLoadingMore)
    }

    @Test
    fun `isLoadingMore is true while loadMorePhotos is in progress`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val page1 = (1..30).map { makePhoto("photo$it") }
        photoRepository.listAnswer = { page ->
            when (page) {
                1 -> page(page1, hasMore = true)
                2 -> {
                    delay(1.seconds)
                    page(emptyList(), hasMore = false)
                }

                else -> unexpectedListPage(page)
            }
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
        photoRepository.listAnswer = { page ->
            when (page) {
                1 -> page(page1, hasMore = true)
                2 -> Result.failure(RuntimeException("page fail"))
                else -> unexpectedListPage(page)
            }
        }
        val vm = createViewModel()

        vm.loadMorePhotos()

        // Existing photos are unchanged despite the page-2 failure
        val content = vm.uiState.value.content as ExploreContent.Success
        assertEquals(30, content.photos.size)
        assertFalse(vm.uiState.value.isLoadingMore)
    }

    @Test
    fun `loadMorePhotos still fetches when a full page parsed to fewer photos`() =
        runTest {
            // A malformed Unsplash entry is dropped by parsePhoto, so photos.size <
            // UNSPLASH_MAX_PAGE_SIZE even though the callable returned a full page. hasMore, not
            // the parsed list's length, decides whether pagination continues.
            val shortPage = (1..29).map { makePhoto("photo$it") }
            photoRepository.listAnswer = { page ->
                when (page) {
                    1 -> page(shortPage, hasMore = true)
                    2 -> page(listOf(makePhoto("photo30")), hasMore = false)
                    else -> unexpectedListPage(page)
                }
            }
            val vm = createViewModel()

            vm.loadMorePhotos()

            val content = vm.uiState.value.content as ExploreContent.Success
            assertEquals(30, content.photos.size)
        }

    @Test
    fun `loadMorePhotos is ignored while a refresh is in flight`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val page1 = (1..30).map { makePhoto("photo$it") }
        photoRepository.listAnswer = { page ->
            if (page == 1) page(page1, hasMore = true) else unexpectedListPage(page)
        }
        val vm = createViewModel()
        advanceUntilIdle()

        photoRepository.listAnswer = { page ->
            if (page == 1) {
                delay(1.seconds)
                page(page1, hasMore = true)
            } else {
                unexpectedListPage(page)
            }
        }
        vm.refresh()
        advanceTimeBy(1.milliseconds) // refresh is suspended mid-fetch
        photoRepository.requestedListPages.clear()

        vm.loadMorePhotos()
        advanceUntilIdle()

        // refresh() has already reset the page counter, so an unguarded load-more would refetch
        // page 1 and append it to the list refresh is replacing, duplicating photos.
        assertTrue(photoRepository.requestedListPages.isEmpty())
    }

    @Test
    fun `refresh cancels an in-flight load more instead of appending its page`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val page1 = (1..30).map { makePhoto("photo$it") }
        val refreshed = listOf(makePhoto("fresh"))
        photoRepository.listAnswer = { page ->
            when (page) {
                1 -> page(page1, hasMore = true)
                2 -> {
                    delay(1.seconds)
                    page((31..60).map { makePhoto("photo$it") }, hasMore = false)
                }

                else -> unexpectedListPage(page)
            }
        }
        val vm = createViewModel()
        advanceUntilIdle()

        vm.loadMorePhotos()
        advanceTimeBy(1.milliseconds) // page-2 fetch is suspended

        photoRepository.listAnswer = { page ->
            if (page == 1) page(refreshed, hasMore = false) else unexpectedListPage(page)
        }
        vm.refresh()
        advanceUntilIdle()

        val content = vm.uiState.value.content as ExploreContent.Success
        assertEquals(refreshed, content.photos)
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
        photoRepository.searchAnswer = { query, page ->
            if (query == "cats" && page == 1) page(results, hasMore = false)
            else unexpectedSearch(query, page)
        }

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
        photoRepository.searchAnswer = { query, page ->
            if (query == "cats" && page == 1) page(results, hasMore = false)
            else unexpectedSearch(query, page)
        }

        viewModel.toggleSearch()
        viewModel.updateSearchQuery("cats")
        viewModel.submitSearch()
        viewModel.toggleSearch() // close
        viewModel.toggleSearch() // reopen

        assertTrue(viewModel.uiState.value.isSearchActive)
        assertEquals("cats", viewModel.uiState.value.searchQuery)
        assertEquals(results, viewModel.uiState.value.searchPhotos)
        assertEquals(1, photoRepository.requestedSearches.size)
    }

    @Test
    fun `closing search does not re-invoke photoRepository listPhotos`() = runTest {
        viewModel.toggleSearch()
        viewModel.updateSearchQuery("dogs")
        viewModel.submitSearch()
        viewModel.toggleSearch()

        assertEquals(listOf(1), photoRepository.requestedListPages) // only the init load
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
        assertTrue(photoRepository.requestedSearches.isEmpty())
    }

    // -------------------------------------------------------------------------
    // Search — submit
    // -------------------------------------------------------------------------

    @Test
    fun `submitSearch with blank query is a no-op`() = runTest {
        viewModel.updateSearchQuery("   ")
        viewModel.submitSearch()

        assertTrue(photoRepository.requestedSearches.isEmpty())
        assertNull(viewModel.uiState.value.searchContent)
    }

    @Test
    fun `submitSearch calls searchPhotos with trimmed query and page 1`() = runTest {
        viewModel.updateSearchQuery("  cats  ")
        viewModel.submitSearch()

        assertEquals(listOf("cats" to 1), photoRepository.requestedSearches)
    }

    @Test
    fun `submitSearch emits Success with results`() = runTest {
        val results = listOf(makePhoto("s1"), makePhoto("s2"))
        photoRepository.searchAnswer = { query, page ->
            if (query == "cats" && page == 1) page(results, hasMore = false)
            else unexpectedSearch(query, page)
        }

        viewModel.updateSearchQuery("cats")
        viewModel.submitSearch()

        assertEquals(ExploreContent.Success(results), viewModel.uiState.value.searchContent)
    }

    @Test
    fun `submitSearch emits Error state on repository failure`() = runTest {
        photoRepository.searchResult = Result.failure(RuntimeException("search failed"))

        viewModel.updateSearchQuery("cats")
        viewModel.submitSearch()

        val content = viewModel.uiState.value.searchContent
        assertTrue(content is ExploreContent.Error)
        assertEquals(
            ErrorMessage.Unlocalized("search failed"),
            (content as ExploreContent.Error).message
        )
    }

    // -------------------------------------------------------------------------
    // Search — load more
    // -------------------------------------------------------------------------
    @Test
    fun `loadMoreSearchResults still fetches when a full page parsed to fewer photos`() =
        runTest {
            val shortPage = (1..29).map { makePhoto("s$it") }
            photoRepository.searchAnswer = { query, page ->
                when (query to page) {
                    "cats" to 1 -> page(shortPage, hasMore = true)
                    "cats" to 2 -> page(listOf(makePhoto("s30")), hasMore = false)
                    else -> unexpectedSearch(query, page)
                }
            }

            viewModel.updateSearchQuery("cats")
            viewModel.submitSearch()
            viewModel.loadMoreSearchResults()

            val content = viewModel.uiState.value.searchContent as ExploreContent.Success
            assertEquals(30, content.photos.size)
        }


    @Test
    fun `loadMoreSearchResults appends second page to existing search results`() = runTest {
        val page1 = (1..30).map { makePhoto("s$it") }
        val page2 = (31..35).map { makePhoto("s$it") }
        photoRepository.searchAnswer = { query, page ->
            when (query to page) {
                "cats" to 1 -> page(page1, hasMore = true)
                "cats" to 2 -> page(page2, hasMore = false)
                else -> unexpectedSearch(query, page)
            }
        }

        viewModel.updateSearchQuery("cats")
        viewModel.submitSearch()
        viewModel.loadMoreSearchResults()

        val content = viewModel.uiState.value.searchContent as ExploreContent.Success
        assertEquals(35, content.photos.size)
    }

    @Test
    fun `loadMoreSearchResults does nothing when first search page was smaller than pageSize`() =
        runTest {
            photoRepository.setSearchPhotos(listOf(makePhoto("s1")), hasMore = false)

            viewModel.updateSearchQuery("cats")
            viewModel.submitSearch()

            // Forget the requests so far, keeping the answers, so this measures only
            // loadMoreSearchResults.
            photoRepository.requestedSearches.clear()

            viewModel.loadMoreSearchResults() // searchHasMore = false → should be a no-op

            assertTrue(photoRepository.requestedSearches.isEmpty())
        }

    @Test
    fun `loadMoreSearchResults does nothing if called before any search was submitted`() = runTest {
        viewModel.loadMoreSearchResults()

        assertTrue(photoRepository.requestedSearches.isEmpty())
        assertNull(viewModel.uiState.value.searchContent)
    }

    @Test
    fun `isSearchLoadingMore is true while loadMoreSearchResults is in progress`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val page1 = (1..30).map { makePhoto("s$it") }
        photoRepository.searchAnswer = { query, page ->
            when (query to page) {
                "cats" to 1 -> page(page1, hasMore = true)
                "cats" to 2 -> {
                    delay(1.seconds)
                    page(emptyList(), hasMore = false)
                }

                else -> unexpectedSearch(query, page)
            }
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
        photoRepository.searchAnswer = { query, page ->
            when (query to page) {
                "cats" to 1 -> page(page1, hasMore = true)
                "cats" to 2 -> Result.failure(RuntimeException("page fail"))
                else -> unexpectedSearch(query, page)
            }
        }

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
        photoRepository.searchAnswer = { query, page ->
            when (query to page) {
                "cats" to 1 -> page(catsPage1, hasMore = true)
                "dogs" to 1 -> page(dogsPage1, hasMore = false)
                else -> unexpectedSearch(query, page)
            }
        }

        viewModel.updateSearchQuery("cats")
        viewModel.submitSearch()

        viewModel.updateSearchQuery("dogs")
        viewModel.submitSearch()

        assertEquals("dogs" to 1, photoRepository.requestedSearches.last())
        val content = viewModel.uiState.value.searchContent as ExploreContent.Success
        assertEquals(dogsPage1, content.photos)
    }

    @Test
    fun `loadMoreSearchResults is ignored while a search submission is in flight`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val page1 = (1..30).map { makePhoto("s$it") }
        photoRepository.searchAnswer = { query, page ->
            if (query == "cats" && page == 1) page(page1, hasMore = true)
            else unexpectedSearch(query, page)
        }
        val vm = createViewModel()
        advanceUntilIdle()

        vm.updateSearchQuery("cats")
        vm.submitSearch()
        advanceUntilIdle()

        photoRepository.searchAnswer = { query, page ->
            if (query == "dogs" && page == 1) {
                delay(1.seconds)
                page(listOf(makePhoto("d1")), hasMore = false)
            } else {
                unexpectedSearch(query, page)
            }
        }
        vm.updateSearchQuery("dogs")
        vm.submitSearch()
        advanceTimeBy(1.milliseconds) // the dogs query is suspended mid-fetch
        photoRepository.requestedSearches.clear()

        vm.loadMoreSearchResults()
        advanceUntilIdle()

        assertTrue(photoRepository.requestedSearches.isEmpty())
    }

    @Test
    fun `submitSearch cancels an in-flight load more instead of mixing the previous query`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val catsPage1 = (1..30).map { makePhoto("cat$it") }
            val dogsPage1 = listOf(makePhoto("dog1"))
            photoRepository.searchAnswer = { query, page ->
                when (query to page) {
                    "cats" to 1 -> page(catsPage1, hasMore = true)
                    "cats" to 2 -> {
                        delay(1.seconds)
                        page((31..60).map { makePhoto("cat$it") }, hasMore = false)
                    }

                    "dogs" to 1 -> page(dogsPage1, hasMore = false)
                    else -> unexpectedSearch(query, page)
                }
            }
            val vm = createViewModel()
            advanceUntilIdle()

            vm.updateSearchQuery("cats")
            vm.submitSearch()
            advanceUntilIdle()

            vm.loadMoreSearchResults()
            advanceTimeBy(1.milliseconds) // the cats page-2 fetch is suspended

            vm.updateSearchQuery("dogs")
            vm.submitSearch()
            advanceUntilIdle()

            // The cats page-2 result must not land in the dogs list
            val content = vm.uiState.value.searchContent as ExploreContent.Success
            assertEquals(dogsPage1, content.photos)
            assertFalse(vm.uiState.value.isSearchLoadingMore)
        }

    // -------------------------------------------------------------------------
    // logImageLoadError
    // -------------------------------------------------------------------------

    @Test
    fun `logImageLoadError logs through ErrorHandler with ImageLoad tag`() = runTest {
        val exception = RuntimeException("decode failed")

        viewModel.logImageLoadError(exception)

        assertEquals(listOf<Throwable>(exception), errorHandler.loggedErrors)
        assertEquals(listOf(ErrorTag.IMAGE_LOAD), errorHandler.loggedTags)
    }
}