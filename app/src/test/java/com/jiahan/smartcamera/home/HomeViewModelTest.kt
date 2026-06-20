package com.jiahan.smartcamera.home

import app.cash.turbine.test
import com.jiahan.smartcamera.MainDispatcherRule
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.note.NoteHandler
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

    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        every { errorHandler.logError(any()) } just runs
        every { errorHandler.getErrorMessage(any()) } returns "An error occurred"
        coEvery { noteRepository.getNotes(any(), any()) } returns Result.success(emptyList())
        viewModel = HomeViewModel(noteRepository, noteHandler, errorHandler)
    }

    @After
    fun tearDown() = unmockkAll()

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun makeNote(id: String, favorite: Boolean = false) = HomeNote(
        text = "Note $id",
        documentPath = id,
        username = "testUser",
        favorite = favorite
    )

    private fun createViewModel() = HomeViewModel(noteRepository, noteHandler, errorHandler)

    // -------------------------------------------------------------------------
    // Initial load
    // -------------------------------------------------------------------------

    @Test
    fun `init emits Success with empty list when repository returns empty`() = runTest {
        val state = viewModel.uiState.value
        assertTrue(state is HomeUiState.Success)
        assertTrue((state as HomeUiState.Success).notes.isEmpty())
    }

    @Test
    fun `init emits Success with notes when repository returns data`() = runTest {
        val notes = listOf(makeNote("a"), makeNote("b"))
        coEvery { noteRepository.getNotes(0, any()) } returns Result.success(notes)
        val vm = createViewModel()
        assertEquals(HomeUiState.Success(notes), vm.uiState.value)
    }

    @Test
    fun `init emits Error state when repository fails`() = runTest {
        val exception = RuntimeException("network error")
        coEvery { noteRepository.getNotes(any(), any()) } returns Result.failure(exception)
        every { errorHandler.getErrorMessage(exception) } returns "network error"
        val vm = createViewModel()

        val state = vm.uiState.value
        assertTrue(state is HomeUiState.Error)
        assertEquals("network error", (state as HomeUiState.Error).message)
    }

    // -------------------------------------------------------------------------
    // Refresh
    // -------------------------------------------------------------------------

    @Test
    fun `refresh reloads page 0 and updates state`() = runTest {
        val refreshedNotes = listOf(makeNote("r1"), makeNote("r2"))
        coEvery { noteRepository.getNotes(0, any()) } returns Result.success(refreshedNotes)

        viewModel.refresh()

        assertEquals(HomeUiState.Success(refreshedNotes), viewModel.uiState.value)
    }

    @Test
    fun `refresh always requests page 0`() = runTest {
        viewModel.refresh()
        coVerify { noteRepository.getNotes(0, any()) }
    }

    @Test
    fun `isRefreshing is false after refresh completes`() = runTest {
        viewModel.refresh()
        assertFalse(viewModel.isRefreshing.value)
    }

    // -------------------------------------------------------------------------
    // Load more
    // -------------------------------------------------------------------------

    @Test
    fun `loadMoreNotes appends second page to existing notes`() = runTest {
        val page0 = (1..10).map { makeNote("note$it") }
        val page1 = (11..15).map { makeNote("note$it") }
        coEvery { noteRepository.getNotes(0, any()) } returns Result.success(page0)
        coEvery { noteRepository.getNotes(1, any()) } returns Result.success(page1)
        val vm = createViewModel()

        vm.loadMoreNotes()

        val state = vm.uiState.value as HomeUiState.Success
        assertEquals(15, state.notes.size)
    }

    @Test
    fun `loadMoreNotes does nothing when first page was smaller than pageSize`() = runTest {
        // 2 items < DEFAULT_PAGE_SIZE(10) → hasMoreData = false
        val twoNotes = listOf(makeNote("a"), makeNote("b"))
        coEvery { noteRepository.getNotes(any(), any()) } returns Result.success(twoNotes)
        val vm = createViewModel() // triggers the init fetch

        // Reset recorded calls (keep stubs) so we measure only what loadMoreNotes triggers
        clearMocks(noteRepository, answers = false)

        vm.loadMoreNotes() // hasMoreData = false → should be a no-op

        coVerify(exactly = 0) { noteRepository.getNotes(any(), any()) }
    }

    @Test
    fun `isLoadingMore is false after loadMoreNotes completes`() = runTest {
        val page0 = (1..10).map { makeNote("note$it") }
        coEvery { noteRepository.getNotes(0, any()) } returns Result.success(page0)
        coEvery { noteRepository.getNotes(1, any()) } returns Result.success(emptyList())
        val vm = createViewModel()

        vm.loadMoreNotes()

        assertFalse(vm.isLoadingMore.value)
    }

    @Test
    fun `isLoadingMore is true while loadMoreNotes is in progress`() = runTest {
        val paused = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(paused)
        val page0 = (1..10).map { makeNote("note$it") }
        coEvery { noteRepository.getNotes(0, any()) } returns Result.success(page0)
        coEvery { noteRepository.getNotes(1, any()) } coAnswers {
            delay(1.seconds); Result.success(
            emptyList()
        )
        }
        val vm = createViewModel()
        advanceUntilIdle() // let init fetch complete

        vm.loadMoreNotes()
        advanceTimeBy(1.milliseconds) // let loadMoreNotes start; page-1 fetch suspends at delay(1s)
        assertTrue(vm.isLoadingMore.value)

        advanceUntilIdle() // complete the delay
        assertFalse(vm.isLoadingMore.value)
    }

    @Test
    fun `loadMoreNotes failure preserves existing Success state`() = runTest {
        val page0 = (1..10).map { makeNote("note$it") }
        coEvery { noteRepository.getNotes(0, any()) } returns Result.success(page0)
        coEvery {
            noteRepository.getNotes(
                1,
                any()
            )
        } returns Result.failure(RuntimeException("page fail"))
        val vm = createViewModel()

        vm.loadMoreNotes()

        // Existing notes are unchanged despite the page-1 failure
        val state = vm.uiState.value as HomeUiState.Success
        assertEquals(10, state.notes.size)
        assertFalse(vm.isLoadingMore.value)
    }

    // -------------------------------------------------------------------------
    // Delete note
    // -------------------------------------------------------------------------

    @Test
    fun `deleteNote success removes the note from Success state`() = runTest {
        val notes = listOf(makeNote("doc1"), makeNote("doc2"))
        coEvery { noteRepository.getNotes(0, any()) } returns Result.success(notes)
        coEvery { noteRepository.deleteNote("doc1") } returns Result.success(Unit)
        val vm = createViewModel()

        vm.deleteNote("doc1")

        val state = vm.uiState.value as HomeUiState.Success
        assertEquals(1, state.notes.size)
        assertEquals("doc2", state.notes.first().documentPath)
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
        coEvery { noteRepository.getNotes(0, any()) } returns Result.success(notes)
        coEvery { noteRepository.deleteNote(any()) } returns Result.failure(RuntimeException())
        val vm = createViewModel()

        vm.deleteNote("doc1")

        assertEquals(2, (vm.uiState.value as HomeUiState.Success).notes.size)
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
            assertEquals("doc1", emitted.documentPath)
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
        assertEquals(note, viewModel.noteToDelete.value)
    }

    @Test
    fun `setNoteToDelete with null clears the note`() = runTest {
        viewModel.setNoteToDelete(makeNote("doc1"))
        viewModel.setNoteToDelete(null)
        assertNull(viewModel.noteToDelete.value)
    }

    // -------------------------------------------------------------------------
    // NoteHandler events
    // -------------------------------------------------------------------------

    @Test
    fun `noteDeletedEvent removes matching note from Success state`() = runTest {
        val notes = listOf(makeNote("doc1"), makeNote("doc2"), makeNote("doc3"))
        coEvery { noteRepository.getNotes(0, any()) } returns Result.success(notes)
        val vm = createViewModel()

        noteHandler.notifyNoteDeleted("doc2")

        val state = vm.uiState.value as HomeUiState.Success
        assertEquals(2, state.notes.size)
        assertFalse(state.notes.any { it.documentPath == "doc2" })
    }

    @Test
    fun `noteFavoritedEvent updates favorite flag for matching note`() = runTest {
        val notes = listOf(makeNote("doc1", favorite = false), makeNote("doc2"))
        coEvery { noteRepository.getNotes(0, any()) } returns Result.success(notes)
        val vm = createViewModel()

        noteHandler.notifyNoteFavorited(makeNote("doc1", favorite = true))

        val state = vm.uiState.value as HomeUiState.Success
        assertTrue(state.notes.first { it.documentPath == "doc1" }.favorite)
    }

    @Test
    fun `noteFavoritedEvent does not affect other notes`() = runTest {
        val notes = listOf(makeNote("doc1", favorite = false), makeNote("doc2", favorite = false))
        coEvery { noteRepository.getNotes(0, any()) } returns Result.success(notes)
        val vm = createViewModel()

        noteHandler.notifyNoteFavorited(makeNote("doc1", favorite = true))

        val state = vm.uiState.value as HomeUiState.Success
        assertFalse(state.notes.first { it.documentPath == "doc2" }.favorite)
    }
}