package com.jiahan.smartcamera.search

import app.cash.turbine.test
import com.jiahan.smartcamera.MainDispatcherRule
import com.jiahan.smartcamera.data.repository.AnalyticsRepository
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.note.NoteErrorReporter
import com.jiahan.smartcamera.note.NoteShareDelegate
import com.jiahan.smartcamera.util.AppConstants.DEBOUNCE_MS
import com.jiahan.smartcamera.util.ErrorHandler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val noteRepository: NoteRepository = mockk()
    private val analyticsRepository: AnalyticsRepository = mockk()
    private val errorHandler: ErrorHandler = mockk()
    private val noteErrorReporter by lazy { NoteErrorReporter(errorHandler) }
    private val noteShare: NoteShareDelegate = mockk(relaxed = true)

    /**
     * Stands in for the `notes` table. Results are a filtered read of this, not of what
     * `searchNotes` returns -- which is what lets a mutation made on another screen show up here
     * with no `NoteHandler` event in between.
     */
    private val notesMirror = MutableStateFlow<List<HomeNote>>(emptyList())

    @Before
    fun setUp() {
        every { analyticsRepository.logSearchEvent(any()) } just runs
        every { analyticsRepository.logSearchCustomEvent(any()) } just runs
        every { errorHandler.logError(any()) } just runs
        every { errorHandler.getErrorMessage(any()) } returns "Error"
        every { noteRepository.searchNotesStream(any()) } answers {
            val query = firstArg<String>()
            notesMirror.map { notes -> notes.filter { matchesQuery(it, query) } }
        }
        stubSearch(emptyList())
    }

    @After
    fun tearDown() = unmockkAll()

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun matchesQuery(note: HomeNote, query: String) =
        query.isBlank() || note.text?.contains(query, ignoreCase = true) == true

    private fun makeNote(id: String, favorite: Boolean = false, text: String = "text $id") =
        HomeNote(noteId = id, username = "user", favorite = favorite, text = text)

    private fun mirror(notes: List<HomeNote>) {
        notesMirror.update { existing ->
            val refreshed = existing.map { old ->
                notes.firstOrNull { it.noteId == old.noteId } ?: old
            }
            refreshed + notes.filter { new -> existing.none { it.noteId == new.noteId } }
        }
    }

    /**
     * Stubs the remote search and mirrors what it returns, the way the real `searchNotes` writes
     * its results through. A stub that only returns is a stub that renders nothing.
     */
    private fun stubSearch(notes: List<HomeNote>, query: String? = null) {
        if (query == null) {
            coEvery { noteRepository.searchNotes(any()) } coAnswers {
                mirror(notes)
                Result.success(notes)
            }
        } else {
            coEvery { noteRepository.searchNotes(query) } coAnswers {
                mirror(notes)
                Result.success(notes)
            }
        }
    }

    /**
     * Builds the ViewModel and subscribes to [SearchViewModel.content], which is shared
     * `WhileSubscribed` and so sits at its initial value with nobody collecting it.
     */
    private fun TestScope.searchViewModel(): SearchViewModel {
        val viewModel = SearchViewModel(
            noteRepository,
            analyticsRepository,
            noteErrorReporter,
            noteShare,
            errorHandler
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.content.collect { }
        }
        return viewModel
    }

    private fun SearchViewModel.notes(): List<HomeNote> =
        (content.value as SearchContent.Success).notes

    // -------------------------------------------------------------------------
    // Initial state
    // -------------------------------------------------------------------------

    @Test
    fun `initial content is Idle`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = searchViewModel()
        advanceTimeBy((DEBOUNCE_MS + 1).milliseconds) // let debounce fire for empty query
        assertEquals(SearchContent.Idle, viewModel.content.value)
    }

    @Test
    fun `initial searchQuery is empty`() = runTest(mainDispatcherRule.testDispatcher) {
        assertEquals("", searchViewModel().searchQuery.value)
    }

    // -------------------------------------------------------------------------
    // Debounced search
    // -------------------------------------------------------------------------

    @Test
    fun `blank query sets state to Idle after debounce`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = searchViewModel()
            viewModel.updateSearchQuery("  ")
            advanceTimeBy((DEBOUNCE_MS + 1).milliseconds)
            assertEquals(SearchContent.Idle, viewModel.content.value)
        }

    @Test
    fun `non-blank query searches and renders the mirrored results after debounce`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val notes = listOf(makeNote("a", text = "cat food"), makeNote("b", text = "cat toy"))
            stubSearch(notes, query = "cat")
            val viewModel = searchViewModel()

            viewModel.updateSearchQuery("cat")
            advanceTimeBy((DEBOUNCE_MS + 1).milliseconds)

            assertEquals(notes, viewModel.notes())
        }

    @Test
    fun `search covers notes the feed never paged`() =
        runTest(mainDispatcherRule.testDispatcher) {
            // The mirror starts empty -- nothing has been paged. The remote search still finds the
            // note and writes it through, so pointing Search at the table did not narrow it.
            val note = makeNote("old", text = "cat from years ago")
            stubSearch(listOf(note), query = "cat")
            val viewModel = searchViewModel()

            viewModel.updateSearchQuery("cat")
            advanceTimeBy((DEBOUNCE_MS + 1).milliseconds)

            assertEquals(listOf(note), viewModel.notes())
        }

    @Test
    fun `search failure over an empty mirror sets Error state`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val exception = RuntimeException("search failed")
            coEvery { noteRepository.searchNotes(any()) } returns Result.failure(exception)
            every { errorHandler.getErrorMessage(exception) } returns "search failed"
            val viewModel = searchViewModel()

            viewModel.updateSearchQuery("query")
            advanceTimeBy((DEBOUNCE_MS + 1).milliseconds)

            val state = viewModel.content.value
            assertTrue(state is SearchContent.Error)
            assertEquals("search failed", (state as SearchContent.Error).message)
        }

    @Test
    fun `search failure still shows matches already in the mirror`() =
        runTest(mainDispatcherRule.testDispatcher) {
            mirror(listOf(makeNote("a", text = "cat food")))
            coEvery { noteRepository.searchNotes(any()) } returns
                    Result.failure(RuntimeException("offline"))
            val viewModel = searchViewModel()

            viewModel.updateSearchQuery("cat")
            advanceTimeBy((DEBOUNCE_MS + 1).milliseconds)

            // Cached matches beat an error screen, as on Home.
            assertEquals(1, viewModel.notes().size)
        }

    @Test
    fun `rapid query changes only trigger one search for the last value`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val notes = listOf(makeNote("x", text = "final answer"))
            // Only stub the expected final query; any other call will fail the test via MockK
            stubSearch(notes, query = "final")
            val viewModel = searchViewModel()

            // Type rapidly — debounce should only fire for the last value
            viewModel.updateSearchQuery("f")
            viewModel.updateSearchQuery("fi")
            viewModel.updateSearchQuery("fin")
            viewModel.updateSearchQuery("final")
            advanceTimeBy((DEBOUNCE_MS + 1).milliseconds)

            coVerify(exactly = 1) { noteRepository.searchNotes(any()) }
            assertEquals(notes, viewModel.notes())
        }

    // -------------------------------------------------------------------------
    // Refresh
    //
    // `refresh()` is reachable only from inside a non-empty Success state -- PullToRefreshBox is
    // rendered in that branch alone -- so it always has a non-blank query to re-run. The old
    // "refresh with a blank query yields Success" case asserted a path the UI cannot take.
    // -------------------------------------------------------------------------

    @Test
    fun `refresh re-runs the current query`() = runTest(mainDispatcherRule.testDispatcher) {
        stubSearch(listOf(makeNote("r1", text = "cat")), query = "cat")
        val viewModel = searchViewModel()
        viewModel.updateSearchQuery("cat")
        advanceTimeBy((DEBOUNCE_MS + 1).milliseconds)

        viewModel.refresh()
        advanceTimeBy(1.milliseconds)

        coVerify(atLeast = 2) { noteRepository.searchNotes("cat") }
    }

    @Test
    fun `isRefreshing is false after refresh completes`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = searchViewModel()
            viewModel.updateSearchQuery("cat")
            advanceTimeBy((DEBOUNCE_MS + 1).milliseconds)

            viewModel.refresh()
            advanceTimeBy(1.milliseconds)

            assertFalse(viewModel.uiState.value.isRefreshing)
        }

    // -------------------------------------------------------------------------
    // Delete note
    // -------------------------------------------------------------------------

    @Test
    fun `deleteNote removes the note from the results`() =
        runTest(mainDispatcherRule.testDispatcher) {
            stubSearch(
                listOf(makeNote("doc1", text = "cat a"), makeNote("doc2", text = "cat b")),
                query = "cat"
            )
            coEvery { noteRepository.deleteNote("doc1") } coAnswers {
                notesMirror.update { notes -> notes.filterNot { it.noteId == "doc1" } }
                Result.success(Unit)
            }
            val viewModel = searchViewModel()
            viewModel.updateSearchQuery("cat")
            advanceTimeBy((DEBOUNCE_MS + 1).milliseconds)

            viewModel.deleteNote("doc1")
            advanceTimeBy(1.milliseconds)

            // No list transform in the ViewModel: the row leaves the table and the query re-emits.
            assertEquals(1, viewModel.notes().size)
            assertEquals("doc2", viewModel.notes().first().noteId)
        }

    @Test
    fun `deleteNote failure emits action error`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { noteRepository.deleteNote(any()) } returns Result.failure(RuntimeException())
        every { errorHandler.getErrorMessage(any()) } returns "delete error"
        val viewModel = searchViewModel()

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
    fun `favoriteNote toggle reaches the results through the mirror`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val note = makeNote("doc1", favorite = false, text = "cat a")
            stubSearch(listOf(note), query = "cat")
            coEvery { noteRepository.favoriteNote(note) } coAnswers {
                notesMirror.update { notes ->
                    notes.map { if (it.noteId == "doc1") it.copy(favorite = true) else it }
                }
                Result.success(Unit)
            }
            val viewModel = searchViewModel()
            viewModel.updateSearchQuery("cat")
            advanceTimeBy((DEBOUNCE_MS + 1).milliseconds)

            viewModel.favoriteNote(note)
            advanceTimeBy(1.milliseconds)

            assertTrue(viewModel.notes().single().favorite) // false → true
        }

    @Test
    fun `favoriteNote failure emits action error`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { noteRepository.favoriteNote(any()) } returns Result.failure(RuntimeException())
        every { errorHandler.getErrorMessage(any()) } returns "fav error"
        val viewModel = searchViewModel()

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
    fun `setNoteToDelete updates state`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = searchViewModel()
        val note = makeNote("doc1")
        viewModel.setNoteToDelete(note)
        assertEquals(note, viewModel.uiState.value.noteToDelete)
    }

    @Test
    fun `setNoteToDelete null clears state`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = searchViewModel()
        viewModel.setNoteToDelete(makeNote("doc1"))
        viewModel.setNoteToDelete(null)
        assertEquals(null, viewModel.uiState.value.noteToDelete)
    }

    // -------------------------------------------------------------------------
    // Mutations made elsewhere
    //
    // These used to arrive as NoteHandler events Search collected and applied to its own list.
    // They are writes to the shared table now, so results reflect them by being a read of it.
    // -------------------------------------------------------------------------

    @Test
    fun `a note deleted on another screen leaves the results`() =
        runTest(mainDispatcherRule.testDispatcher) {
            stubSearch(
                listOf(makeNote("doc1", text = "cat a"), makeNote("doc2", text = "cat b")),
                query = "cat"
            )
            val viewModel = searchViewModel()
            viewModel.updateSearchQuery("cat")
            advanceTimeBy((DEBOUNCE_MS + 1).milliseconds)

            notesMirror.update { notes -> notes.filterNot { it.noteId == "doc1" } }
            advanceTimeBy(1.milliseconds)

            assertEquals(1, viewModel.notes().size)
            assertFalse(viewModel.notes().any { it.noteId == "doc1" })
        }

    @Test
    fun `a favorite toggled on another screen reaches the results`() =
        runTest(mainDispatcherRule.testDispatcher) {
            stubSearch(
                listOf(
                    makeNote("doc1", favorite = false, text = "cat a"),
                    makeNote("doc2", text = "cat b")
                ),
                query = "cat"
            )
            val viewModel = searchViewModel()
            viewModel.updateSearchQuery("cat")
            advanceTimeBy((DEBOUNCE_MS + 1).milliseconds)

            notesMirror.update { notes ->
                notes.map { if (it.noteId == "doc1") it.copy(favorite = true) else it }
            }
            advanceTimeBy(1.milliseconds)

            assertTrue(viewModel.notes().first { it.noteId == "doc1" }.favorite)
        }
}