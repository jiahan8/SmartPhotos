package com.jiahan.smartcamera.home

import app.cash.turbine.test
import com.jiahan.smartcamera.MainDispatcherRule
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.domain.NoteCursor
import com.jiahan.smartcamera.domain.NotePage
import com.jiahan.smartcamera.fake.FakeRemoteConfigRepository
import com.jiahan.smartcamera.note.NoteActionsDelegate
import com.jiahan.smartcamera.note.NoteErrorReporter
import com.jiahan.smartcamera.note.NoteHandler
import com.jiahan.smartcamera.note.NoteShareDelegate
import com.jiahan.smartcamera.util.ErrorHandler
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.unmockkAll
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
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val noteRepository: NoteRepository = mockk()
    private val noteHandler = NoteHandler()
    private val errorHandler: ErrorHandler = mockk()
    private val noteActions by lazy {
        NoteActionsDelegate(
            noteRepository,
            noteHandler,
            NoteErrorReporter(errorHandler)
        )
    }
    private val noteShare: NoteShareDelegate = mockk(relaxed = true)
    private val remoteConfigRepository = FakeRemoteConfigRepository()

    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        every { errorHandler.logError(any()) } just runs
        every { errorHandler.getErrorMessage(any()) } returns "An error occurred"
        coEvery {
            noteRepository.getNotes(
                any(),
                any()
            )
        } returns Result.success(NotePage(emptyList()))
        viewModel = HomeViewModel(
            noteRepository,
            noteHandler,
            noteActions,
            noteShare,
            errorHandler,
            remoteConfigRepository
        )
    }

    @After
    fun tearDown() = unmockkAll()

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private object TestCursor : NoteCursor

    private fun makeNote(id: String, favorite: Boolean = false) = HomeNote(
        noteId = id,
        text = "Note $id",
        username = "testUser",
        favorite = favorite
    )

    private fun createViewModel() =
        HomeViewModel(
            noteRepository,
            noteHandler,
            noteActions,
            noteShare,
            errorHandler,
            remoteConfigRepository
        )

    // -------------------------------------------------------------------------
    // Initial load
    // -------------------------------------------------------------------------

    @Test
    fun `init emits Success with empty list when repository returns empty`() = runTest {
        val content = viewModel.uiState.value.content
        assertTrue(content is HomeContent.Success)
        assertTrue((content as HomeContent.Success).notes.isEmpty())
    }

    @Test
    fun `init emits Success with notes when repository returns data`() = runTest {
        val notes = listOf(makeNote("a"), makeNote("b"))
        coEvery { noteRepository.getNotes(null, any()) } returns Result.success(NotePage(notes))
        val vm = createViewModel()
        assertEquals(HomeContent.Success(notes), vm.uiState.value.content)
    }

    @Test
    fun `init emits Error state when repository fails`() = runTest {
        val exception = RuntimeException("network error")
        coEvery { noteRepository.getNotes(any(), any()) } returns Result.failure(exception)
        every { errorHandler.getErrorMessage(exception) } returns "network error"
        val vm = createViewModel()

        val content = vm.uiState.value.content
        assertTrue(content is HomeContent.Error)
        assertEquals("network error", (content as HomeContent.Error).message)
    }

    // -------------------------------------------------------------------------
    // Refresh
    // -------------------------------------------------------------------------

    @Test
    fun `refresh reloads page 0 and updates state`() = runTest {
        val refreshedNotes = listOf(makeNote("r1"), makeNote("r2"))
        coEvery { noteRepository.getNotes(null, any()) } returns Result.success(
            NotePage(
                refreshedNotes
            )
        )

        viewModel.refresh()

        assertEquals(HomeContent.Success(refreshedNotes), viewModel.uiState.value.content)
    }

    @Test
    fun `refresh always requests page 0`() = runTest {
        viewModel.refresh()
        coVerify { noteRepository.getNotes(null, any()) }
    }

    @Test
    fun `isRefreshing is false after refresh completes`() = runTest {
        viewModel.refresh()
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun `refresh failure replaces existing notes with Error state`() = runTest {
        val initialNotes = listOf(makeNote("a"), makeNote("b"))
        coEvery { noteRepository.getNotes(null, any()) } returns Result.success(
            NotePage(
                initialNotes
            )
        )
        val vm = createViewModel()
        assertTrue(vm.uiState.value.content is HomeContent.Success)

        val exception = RuntimeException("refresh failed")
        coEvery { noteRepository.getNotes(null, any()) } returns Result.failure(exception)
        every { errorHandler.getErrorMessage(exception) } returns "refresh failed"

        vm.refresh()

        val content = vm.uiState.value.content
        assertTrue(content is HomeContent.Error)
        assertEquals("refresh failed", (content as HomeContent.Error).message)
        assertFalse(vm.uiState.value.isRefreshing)
    }

    // -------------------------------------------------------------------------
    // Load more
    // -------------------------------------------------------------------------

    @Test
    fun `loadMoreNotes appends second page to existing notes`() = runTest {
        val page0 = (1..10).map { makeNote("note$it") }
        val page1 = (11..15).map { makeNote("note$it") }
        coEvery { noteRepository.getNotes(null, any()) } returns Result.success(
            NotePage(
                page0,
                TestCursor
            )
        )
        coEvery {
            noteRepository.getNotes(
                TestCursor,
                any()
            )
        } returns Result.success(NotePage(page1))
        val vm = createViewModel()

        vm.loadMoreNotes()

        val content = vm.uiState.value.content as HomeContent.Success
        assertEquals(15, content.notes.size)
    }

    @Test
    fun `loadMoreNotes does nothing when the page reports no more data`() = runTest {
        val twoNotes = listOf(makeNote("a"), makeNote("b"))
        coEvery { noteRepository.getNotes(any(), any()) } returns Result.success(NotePage(twoNotes))
        val vm = createViewModel() // triggers the init fetch

        // Reset recorded calls (keep stubs) so we measure only what loadMoreNotes triggers
        clearMocks(noteRepository, answers = false)

        vm.loadMoreNotes() // hasMoreData = false → should be a no-op

        coVerify(exactly = 0) { noteRepository.getNotes(any(), any()) }
    }

    @Test
    fun `loadMoreNotes still fetches when a full page mapped to fewer notes than pageSize`() =
        runTest {
            // A note whose author lookup failed is dropped from the page, so notes.size <
            // DEFAULT_PAGE_SIZE even though the query had a full page of rows. hasMore, not the
            // mapped list's length, decides whether pagination continues.
            val shortPage = (1..9).map { makeNote("note$it") }
            coEvery { noteRepository.getNotes(null, any()) } returns
                    Result.success(NotePage(shortPage, TestCursor))
            coEvery { noteRepository.getNotes(TestCursor, any()) } returns
                    Result.success(NotePage(listOf(makeNote("note10"))))
            val vm = createViewModel()

            vm.loadMoreNotes()

            val content = vm.uiState.value.content as HomeContent.Success
            assertEquals(10, content.notes.size)
        }

    @Test
    fun `isLoadingMore is false after loadMoreNotes completes`() = runTest {
        val page0 = (1..10).map { makeNote("note$it") }
        coEvery { noteRepository.getNotes(null, any()) } returns Result.success(
            NotePage(
                page0,
                TestCursor
            )
        )
        coEvery { noteRepository.getNotes(TestCursor, any()) } returns Result.success(
            NotePage(
                emptyList()
            )
        )
        val vm = createViewModel()

        vm.loadMoreNotes()

        assertFalse(vm.uiState.value.isLoadingMore)
    }

    @Test
    fun `isLoadingMore is true while loadMoreNotes is in progress`() = runTest {
        val paused = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(paused)
        val page0 = (1..10).map { makeNote("note$it") }
        coEvery { noteRepository.getNotes(null, any()) } returns Result.success(
            NotePage(
                page0,
                TestCursor
            )
        )
        coEvery { noteRepository.getNotes(TestCursor, any()) } coAnswers {
            delay(1.seconds); Result.success(NotePage(emptyList()))
        }
        val vm = createViewModel()
        advanceUntilIdle() // let init fetch complete

        vm.loadMoreNotes()
        advanceTimeBy(1.milliseconds) // let loadMoreNotes start; page-1 fetch suspends at delay(1s)
        assertTrue(vm.uiState.value.isLoadingMore)

        advanceUntilIdle() // complete the delay
        assertFalse(vm.uiState.value.isLoadingMore)
    }

    @Test
    fun `loadMoreNotes failure preserves existing Success state`() = runTest {
        val page0 = (1..10).map { makeNote("note$it") }
        coEvery { noteRepository.getNotes(null, any()) } returns Result.success(
            NotePage(
                page0,
                TestCursor
            )
        )
        coEvery {
            noteRepository.getNotes(
                TestCursor,
                any()
            )
        } returns Result.failure(RuntimeException("page fail"))
        val vm = createViewModel()

        vm.loadMoreNotes()

        // Existing notes are unchanged despite the page-1 failure
        val content = vm.uiState.value.content as HomeContent.Success
        assertEquals(10, content.notes.size)
        assertFalse(vm.uiState.value.isLoadingMore)
    }

    @Test
    fun `loadMoreNotes is ignored while a refresh is in flight`() = runTest {
        val paused = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(paused)
        val page0 = (1..10).map { makeNote("note$it") }
        coEvery { noteRepository.getNotes(null, any()) } returns
                Result.success(NotePage(page0, TestCursor))
        val vm = createViewModel()
        advanceUntilIdle()

        coEvery { noteRepository.getNotes(null, any()) } coAnswers {
            delay(1.seconds); Result.success(NotePage(page0, TestCursor))
        }
        vm.refresh()
        advanceTimeBy(1.milliseconds) // refresh is suspended mid-fetch
        clearMocks(noteRepository, answers = false)

        vm.loadMoreNotes()
        advanceUntilIdle()

        // refresh() has already reset the cursor, so an unguarded load-more would refetch the
        // first page and append it to the list refresh is replacing, duplicating notes.
        coVerify(exactly = 0) { noteRepository.getNotes(any(), any()) }
    }

    @Test
    fun `refresh cancels an in-flight load more instead of appending its page`() = runTest {
        val paused = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(paused)
        val page0 = (1..10).map { makeNote("note$it") }
        val refreshed = listOf(makeNote("fresh"))
        coEvery { noteRepository.getNotes(null, any()) } returns
                Result.success(NotePage(page0, TestCursor))
        coEvery { noteRepository.getNotes(TestCursor, any()) } coAnswers {
            delay(1.seconds); Result.success(NotePage((11..20).map { makeNote("note$it") }))
        }
        val vm = createViewModel()
        advanceUntilIdle()

        vm.loadMoreNotes()
        advanceTimeBy(1.milliseconds) // page-2 fetch is suspended

        coEvery { noteRepository.getNotes(null, any()) } returns
                Result.success(NotePage(refreshed))
        vm.refresh()
        advanceUntilIdle()

        val content = vm.uiState.value.content as HomeContent.Success
        assertEquals(refreshed, content.notes)
        assertFalse(vm.uiState.value.isLoadingMore)
    }

    @Test
    fun `noteAddedEvent cancels an in-flight load more instead of splicing its page`() = runTest {
        val paused = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(paused)
        val page0 = (1..10).map { makeNote("note$it") }
        coEvery { noteRepository.getNotes(null, any()) } returns
                Result.success(NotePage(page0, TestCursor))
        coEvery { noteRepository.getNotes(TestCursor, any()) } coAnswers {
            delay(1.seconds); Result.success(NotePage((11..20).map { makeNote("note$it") }))
        }
        val vm = createViewModel()
        advanceUntilIdle()

        vm.loadMoreNotes()
        advanceTimeBy(1.milliseconds) // page-2 fetch is suspended

        // The added note shifts the feed, so the in-flight page-2 window is now stale
        val reloaded = listOf(makeNote("added")) + page0
        coEvery { noteRepository.getNotes(null, any()) } returns Result.success(NotePage(reloaded))
        noteHandler.notifyNoteAdded()
        advanceUntilIdle()

        val content = vm.uiState.value.content as HomeContent.Success
        assertEquals(reloaded, content.notes)
    }

    // -------------------------------------------------------------------------
    // Delete note
    // -------------------------------------------------------------------------

    @Test
    fun `deleteNote success removes the note from Success state`() = runTest {
        val notes = listOf(makeNote("doc1"), makeNote("doc2"))
        coEvery { noteRepository.getNotes(null, any()) } returns Result.success(NotePage(notes))
        coEvery { noteRepository.deleteNote("doc1") } returns Result.success(Unit)
        val vm = createViewModel()

        vm.deleteNote("doc1")

        val content = vm.uiState.value.content as HomeContent.Success
        assertEquals(1, content.notes.size)
        assertEquals("doc2", content.notes.first().noteId)
    }

    @Test
    fun `deleteNote failure emits action error message`() = runTest {
        coEvery { noteRepository.deleteNote(any()) } returns Result.failure(RuntimeException("fail"))
        every { errorHandler.getErrorMessage(any()) } returns "delete failed"

        viewModel.actionError.test {
            viewModel.deleteNote("doc1")
            assertEquals("delete failed", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteNote failure does not change Success state`() = runTest {
        val notes = listOf(makeNote("doc1"), makeNote("doc2"))
        coEvery { noteRepository.getNotes(null, any()) } returns Result.success(NotePage(notes))
        coEvery { noteRepository.deleteNote(any()) } returns Result.failure(RuntimeException())
        val vm = createViewModel()

        vm.deleteNote("doc1")

        assertEquals(2, (vm.uiState.value.content as HomeContent.Success).notes.size)
    }

    // -------------------------------------------------------------------------
    // Favorite note
    // -------------------------------------------------------------------------

    @Test
    fun `favoriteNote success notifies NoteHandler with toggled favorite`() = runTest {
        val note = makeNote("doc1", favorite = false)
        coEvery { noteRepository.favoriteNote(note) } returns Result.success(Unit)

        noteHandler.noteFavoritedEvent.test {
            viewModel.favoriteNote(note)
            val emitted = awaitItem()
            assertEquals("doc1", emitted.noteId)
            assertTrue(emitted.favorite) // false → true
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `favoriteNote unfavoriting notifies NoteHandler with false`() = runTest {
        val note = makeNote("doc1", favorite = true)
        coEvery { noteRepository.favoriteNote(note) } returns Result.success(Unit)

        noteHandler.noteFavoritedEvent.test {
            viewModel.favoriteNote(note)
            assertFalse(awaitItem().favorite) // true → false
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `favoriteNote failure emits action error`() = runTest {
        coEvery { noteRepository.favoriteNote(any()) } returns Result.failure(RuntimeException())
        every { errorHandler.getErrorMessage(any()) } returns "fav error"

        viewModel.actionError.test {
            viewModel.favoriteNote(makeNote("doc1"))
            assertEquals("fav error", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // setNoteToDelete
    // -------------------------------------------------------------------------

    @Test
    fun `setNoteToDelete sets the note`() = runTest {
        val note = makeNote("doc1")
        viewModel.setNoteToDelete(note)
        assertEquals(note, viewModel.uiState.value.noteToDelete)
    }

    @Test
    fun `setNoteToDelete with null clears the note`() = runTest {
        viewModel.setNoteToDelete(makeNote("doc1"))
        viewModel.setNoteToDelete(null)
        assertNull(viewModel.uiState.value.noteToDelete)
    }

    // -------------------------------------------------------------------------
    // NoteHandler events
    // -------------------------------------------------------------------------

    @Test
    fun `noteDeletedEvent removes matching note from Success state`() = runTest {
        val notes = listOf(makeNote("doc1"), makeNote("doc2"), makeNote("doc3"))
        coEvery { noteRepository.getNotes(null, any()) } returns Result.success(NotePage(notes))
        val vm = createViewModel()

        noteHandler.notifyNoteDeleted("doc2")

        val content = vm.uiState.value.content as HomeContent.Success
        assertEquals(2, content.notes.size)
        assertFalse(content.notes.any { it.noteId == "doc2" })
    }

    @Test
    fun `noteFavoritedEvent updates favorite flag for matching note`() = runTest {
        val notes = listOf(makeNote("doc1", favorite = false), makeNote("doc2"))
        coEvery { noteRepository.getNotes(null, any()) } returns Result.success(NotePage(notes))
        val vm = createViewModel()

        noteHandler.notifyNoteFavorited(makeNote("doc1", favorite = true))

        val content = vm.uiState.value.content as HomeContent.Success
        assertTrue(content.notes.first { it.noteId == "doc1" }.favorite)
    }

    @Test
    fun `noteFavoritedEvent does not affect other notes`() = runTest {
        val notes = listOf(makeNote("doc1", favorite = false), makeNote("doc2", favorite = false))
        coEvery { noteRepository.getNotes(null, any()) } returns Result.success(NotePage(notes))
        val vm = createViewModel()

        noteHandler.notifyNoteFavorited(makeNote("doc1", favorite = true))

        val content = vm.uiState.value.content as HomeContent.Success
        assertFalse(content.notes.first { it.noteId == "doc2" }.favorite)
    }

    // -------------------------------------------------------------------------
    // Explore icon visibility (Remote Config)
    // -------------------------------------------------------------------------

    @Test
    fun `isExploreIconVisible reflects Remote Config value at construction`() = runTest {
        remoteConfigRepository.setExploreIconVisible(false)
        val vm = createViewModel()

        assertFalse(vm.uiState.value.isExploreIconVisible)
    }

    @Test
    fun `isExploreIconVisible updates live when Remote Config pushes a change`() = runTest {
        remoteConfigRepository.setExploreIconVisible(true)
        val vm = createViewModel()
        assertTrue(vm.uiState.value.isExploreIconVisible)

        remoteConfigRepository.setExploreIconVisible(false)

        assertFalse(vm.uiState.value.isExploreIconVisible)
    }
}