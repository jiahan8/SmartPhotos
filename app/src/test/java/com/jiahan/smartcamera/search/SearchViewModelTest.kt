package com.jiahan.smartcamera.search

import app.cash.turbine.test
import com.jiahan.smartcamera.MainDispatcherRule
import com.jiahan.smartcamera.data.repository.AnalyticsRepository
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.note.NoteHandler
import com.jiahan.smartcamera.util.AppConstants.DEBOUNCE_MS
import com.jiahan.smartcamera.util.ErrorHandler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.unmockkAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.milliseconds
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val noteRepository: NoteRepository = mockk()
    private val analyticsRepository: AnalyticsRepository = mockk()
    private val noteHandler = NoteHandler()
    private val errorHandler: ErrorHandler = mockk()

    private lateinit var viewModel: SearchViewModel

    @Before
    fun setUp() {
        every { analyticsRepository.logSearchEvent(any()) } just runs
        every { analyticsRepository.logSearchCustomEvent(any()) } just runs
        every { errorHandler.logError(any()) } just runs
        every { errorHandler.getErrorMessage(any()) } returns "Error"
        viewModel = SearchViewModel(noteRepository, analyticsRepository, noteHandler, errorHandler)
    }

    @After
    fun tearDown() = unmockkAll()

    private fun makeNote(id: String, favorite: Boolean = false) = HomeNote(
        documentPath = id,
        username = "user",
        favorite = favorite,
        text = "text $id"
    )

    /** Puts the ViewModel into a Success state by running a refresh (searchNotes with empty query). */
    private fun TestScope.loadNotesViaRefresh(notes: List<HomeNote>) {
        coEvery { noteRepository.searchNotes("") } returns Result.success(notes)
        viewModel.refresh()
        advanceTimeBy(1.milliseconds) // let launch complete
    }

    // -------------------------------------------------------------------------
    // Initial state
    // -------------------------------------------------------------------------

    @Test
    fun `initial uiState is Idle`() = runTest(mainDispatcherRule.testDispatcher) {
        advanceTimeBy((DEBOUNCE_MS + 1).milliseconds) // let debounce fire for empty query
        assertEquals(SearchContent.Idle, viewModel.uiState.value.content)
    }

    @Test
    fun `initial searchQuery is empty`() {
        assertEquals("", viewModel.searchQuery.value)
    }

    // -------------------------------------------------------------------------
    // Debounced search
    // -------------------------------------------------------------------------

    @Test
    fun `blank query sets state to Idle after debounce`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.updateSearchQuery("  ")
            advanceTimeBy((DEBOUNCE_MS + 1).milliseconds)
            assertEquals(SearchContent.Idle, viewModel.uiState.value.content)
        }

    @Test
    fun `non-blank query triggers search and sets Success state after debounce`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val notes = listOf(makeNote("a"), makeNote("b"))
            coEvery { noteRepository.searchNotes("cat") } returns Result.success(notes)

            viewModel.updateSearchQuery("cat")
            advanceTimeBy((DEBOUNCE_MS + 1).milliseconds)

            val state = viewModel.uiState.value.content
            assertTrue(state is SearchContent.Success)
            assertEquals(notes, (state as SearchContent.Success).notes)
        }

    @Test
    fun `search failure sets Error state after debounce`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val exception = RuntimeException("search failed")
            coEvery { noteRepository.searchNotes(any()) } returns Result.failure(exception)
            every { errorHandler.getErrorMessage(exception) } returns "search failed"

            viewModel.updateSearchQuery("query")
            advanceTimeBy((DEBOUNCE_MS + 1).milliseconds)

            val state = viewModel.uiState.value.content
            assertTrue(state is SearchContent.Error)
            assertEquals("search failed", (state as SearchContent.Error).message)
        }

    @Test
    fun `rapid query changes only trigger one search for the last value`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val notes = listOf(makeNote("x"))
            // Only stub the expected final query; any other call will fail the test via MockK
            coEvery { noteRepository.searchNotes("final") } returns Result.success(notes)

            // Type rapidly — debounce should only fire for the last value
            viewModel.updateSearchQuery("f")
            viewModel.updateSearchQuery("fi")
            viewModel.updateSearchQuery("fin")
            viewModel.updateSearchQuery("final")
            advanceTimeBy((DEBOUNCE_MS + 1).milliseconds)

            // Exactly one repository call, and the state reflects the debounced query result
            coVerify(exactly = 1) { noteRepository.searchNotes(any()) }
            val state = viewModel.uiState.value.content as SearchContent.Success
            assertEquals(notes, state.notes)
        }

    // -------------------------------------------------------------------------
    // Refresh
    // -------------------------------------------------------------------------

    @Test
    fun `refresh triggers search with current query`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val notes = listOf(makeNote("r1"))
            coEvery { noteRepository.searchNotes("") } returns Result.success(notes)

            viewModel.refresh()
            advanceTimeBy(1.milliseconds)

            val state = viewModel.uiState.value.content
            assertTrue(state is SearchContent.Success)
            assertEquals(notes, (state as SearchContent.Success).notes)
        }

    @Test
    fun `isRefreshing is false after refresh completes`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { noteRepository.searchNotes(any()) } returns Result.success(emptyList())
            viewModel.refresh()
            advanceTimeBy(1.milliseconds)
            assertFalse(viewModel.uiState.value.isRefreshing)
        }

    @Test
    fun `refresh with blank query still produces Success unlike debounce which produces Idle`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val notes = listOf(makeNote("r1"), makeNote("r2"))
            coEvery { noteRepository.searchNotes("") } returns Result.success(notes)

            // Debounce path: blank query → Idle
            advanceTimeBy((DEBOUNCE_MS + 1).milliseconds)
            assertEquals(SearchContent.Idle, viewModel.uiState.value.content)

            // Refresh path: blank query still fetches → Success
            viewModel.refresh()
            advanceTimeBy(1.milliseconds)

            val state = viewModel.uiState.value.content
            assertTrue(
                "refresh() with a blank query should yield Success, not Idle",
                state is SearchContent.Success
            )
            assertEquals(notes, (state as SearchContent.Success).notes)
        }

    // -------------------------------------------------------------------------
    // Delete note
    // -------------------------------------------------------------------------

    @Test
    fun `deleteNote success removes note from Success state`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val notes = listOf(makeNote("doc1"), makeNote("doc2"))
            coEvery { noteRepository.deleteNote("doc1") } returns Result.success(Unit)
            loadNotesViaRefresh(notes)

            viewModel.deleteNote("doc1")
            advanceTimeBy(1.milliseconds)

            val state = viewModel.uiState.value.content as SearchContent.Success
            assertEquals(1, state.notes.size)
            assertEquals("doc2", state.notes.first().documentPath)
        }

    @Test
    fun `deleteNote failure emits action error`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { noteRepository.deleteNote(any()) } returns Result.failure(RuntimeException())
        every { errorHandler.getErrorMessage(any()) } returns "delete error"

        viewModel.actionError.test {
            viewModel.deleteNote("doc1")
            advanceTimeBy(1.milliseconds)
            assertEquals("delete error", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // Favorite note
    // -------------------------------------------------------------------------

    @Test
    fun `favoriteNote success notifies NoteHandler with toggled favorite`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val note = makeNote("doc1", favorite = false)
            coEvery { noteRepository.favoriteNote(note) } returns Result.success(Unit)

            noteHandler.noteFavoritedEvent.test {
                viewModel.favoriteNote(note)
                advanceTimeBy(1.milliseconds)
                val event = awaitItem()
                assertEquals("doc1", event.documentPath)
                assertTrue(event.favorite) // false → true
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `favoriteNote failure emits action error`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { noteRepository.favoriteNote(any()) } returns Result.failure(RuntimeException())
        every { errorHandler.getErrorMessage(any()) } returns "fav error"

        viewModel.actionError.test {
            viewModel.favoriteNote(makeNote("doc1"))
            advanceTimeBy(1.milliseconds)
            assertEquals("fav error", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // setNoteToDelete
    // -------------------------------------------------------------------------

    @Test
    fun `setNoteToDelete updates state`() {
        val note = makeNote("doc1")
        viewModel.setNoteToDelete(note)
        assertEquals(note, viewModel.uiState.value.noteToDelete)
    }

    @Test
    fun `setNoteToDelete null clears state`() {
        viewModel.setNoteToDelete(makeNote("doc1"))
        viewModel.setNoteToDelete(null)
        assertEquals(null, viewModel.uiState.value.noteToDelete)
    }

    // -------------------------------------------------------------------------
    // NoteHandler events
    // -------------------------------------------------------------------------

    @Test
    fun `noteDeletedEvent removes note from Success state`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val notes = listOf(makeNote("doc1"), makeNote("doc2"))
            loadNotesViaRefresh(notes)

            noteHandler.notifyNoteDeleted("doc1")
            advanceTimeBy(1.milliseconds)

            val state = viewModel.uiState.value.content as SearchContent.Success
            assertEquals(1, state.notes.size)
            assertFalse(state.notes.any { it.documentPath == "doc1" })
        }

    @Test
    fun `noteFavoritedEvent updates favorite flag in Success state`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val notes = listOf(makeNote("doc1", favorite = false), makeNote("doc2"))
            loadNotesViaRefresh(notes)

            noteHandler.notifyNoteFavorited(makeNote("doc1", favorite = true))
            advanceTimeBy(1.milliseconds)

            val state = viewModel.uiState.value.content as SearchContent.Success
            assertTrue(state.notes.first { it.documentPath == "doc1" }.favorite)
        }
}