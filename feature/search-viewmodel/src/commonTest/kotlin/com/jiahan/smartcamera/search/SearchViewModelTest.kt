package com.jiahan.smartcamera.search

import app.cash.turbine.test
import com.jiahan.smartcamera.domain.Note
import com.jiahan.smartcamera.fake.FakeAnalyticsRepository
import com.jiahan.smartcamera.fake.FakeErrorHandler
import com.jiahan.smartcamera.fake.FakeMediaCacheRepository
import com.jiahan.smartcamera.fake.FakeNoteRepository
import com.jiahan.smartcamera.note.NoteActionError
import com.jiahan.smartcamera.note.NoteErrorReporter
import com.jiahan.smartcamera.note.NoteShareDelegate
import com.jiahan.smartcamera.util.AppConstants.DEBOUNCE_MS
import com.jiahan.smartcamera.util.ErrorMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
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
import kotlin.time.Duration.Companion.milliseconds

/**
 * [SearchViewModel]'s suite, in `commonTest` beside its subject: :core:domain-testing's fakes where
 * mockk was and [Dispatchers.setMain] where `MainDispatcherRule` was -- `ExploreViewModelTest`
 * records why each of those.
 *
 * What it leaned on mockk for was a search stubbed per query, one held in flight, and the calls
 * made. [FakeNoteRepository.searchAnswer] and [FakeNoteRepository.requestedSearches] cover those,
 * and the results are read from the fake's own `notes` mirror, which it writes each search's
 * results into the way `searchNotes` does. One behaviour needed a replacement rather than a
 * translation: an unstubbed mock failed the test when called, and a fake just answers, so the
 * rapid-typing case asserts the queries that were searched instead of relying on that.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val testDispatcher: TestDispatcher = StandardTestDispatcher()

    private val noteRepository = FakeNoteRepository()
    private val analyticsRepository = FakeAnalyticsRepository()
    private val errorHandler = FakeErrorHandler()
    private val noteErrorReporter = NoteErrorReporter(errorHandler)
    private val noteShare = NoteShareDelegate(FakeMediaCacheRepository(), noteErrorReporter)

    /**
     * Stands in for the `notes` table. Results are a filtered read of this, not of what
     * `searchNotes` returns -- which is what lets a mutation made on another screen show up here
     * with no `NoteHandler` event in between.
     */
    private val notesMirror = noteRepository.notes

    /** `searchNotes` answers per query, falling back to [anySearch]. */
    private val searchAnswers = mutableMapOf<String, suspend () -> Result<List<Note>>>()
    private var anySearch: suspend () -> Result<List<Note>> = { Result.success(emptyList()) }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        noteRepository.searchAnswer = { query -> (searchAnswers[query] ?: anySearch)() }
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun makeNote(id: String, isFavorite: Boolean = false, text: String = "text $id") =
        Note(noteId = id, username = "user", isFavorite = isFavorite, text = text)

    /**
     * Answers the remote search for [query], or for every query when it is null -- which replaces
     * the per-query answers, as a later `any()` stub would.
     */
    private fun answerSearch(query: String? = null, answer: suspend () -> Result<List<Note>>) {
        if (query == null) {
            searchAnswers.clear()
            anySearch = answer
        } else {
            searchAnswers[query] = answer
        }
    }

    /**
     * Stubs the remote search. The fake mirrors what it returns, the way the real `searchNotes`
     * writes its results through; a stub that only returned would render nothing.
     */
    private fun stubSearch(notes: List<Note>, query: String? = null) =
        answerSearch(query) { Result.success(notes) }

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

    private fun SearchViewModel.notes(): List<Note> =
        (content.value as SearchContent.Success).notes

    // -------------------------------------------------------------------------
    // Initial state
    // -------------------------------------------------------------------------

    @Test
    fun `initial content is Idle`() = runTest(testDispatcher) {
        val viewModel = searchViewModel()
        advanceTimeBy((DEBOUNCE_MS + 1).milliseconds) // let debounce fire for empty query
        assertEquals(SearchContent.Idle, viewModel.content.value)
    }

    @Test
    fun `initial searchQuery is empty`() = runTest(testDispatcher) {
        assertEquals("", searchViewModel().searchQuery.value)
    }

    // -------------------------------------------------------------------------
    // Debounced search
    // -------------------------------------------------------------------------

    @Test
    fun `blank query sets state to Idle after debounce`() = runTest(testDispatcher) {
        val viewModel = searchViewModel()
        viewModel.updateSearchQuery("  ")
        advanceTimeBy((DEBOUNCE_MS + 1).milliseconds)
        assertEquals(SearchContent.Idle, viewModel.content.value)
    }

    @Test
    fun `non-blank query searches and renders the mirrored results after debounce`() =
        runTest(testDispatcher) {
            val notes = listOf(makeNote("a", text = "cat food"), makeNote("b", text = "cat toy"))
            stubSearch(notes, query = "cat")
            val viewModel = searchViewModel()

            viewModel.updateSearchQuery("cat")
            advanceTimeBy((DEBOUNCE_MS + 1).milliseconds)

            assertEquals(notes, viewModel.notes())
        }

    @Test
    fun `search covers notes the feed never paged`() = runTest(testDispatcher) {
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
    fun `search failure over an empty mirror sets Error state`() = runTest(testDispatcher) {
        answerSearch { Result.failure(RuntimeException("search failed")) }
        val viewModel = searchViewModel()

        viewModel.updateSearchQuery("query")
        advanceTimeBy((DEBOUNCE_MS + 1).milliseconds)

        assertEquals(
            SearchContent.Error(ErrorMessage.Unlocalized("search failed")),
            viewModel.content.value
        )
    }

    @Test
    fun `search failure still shows matches already in the mirror`() = runTest(testDispatcher) {
        notesMirror.upsert(listOf(makeNote("a", text = "cat food")))
        answerSearch { Result.failure(RuntimeException("offline")) }
        val viewModel = searchViewModel()

        viewModel.updateSearchQuery("cat")
        advanceTimeBy((DEBOUNCE_MS + 1).milliseconds)

        // Cached matches beat an error screen, as on Home.
        assertEquals(1, viewModel.notes().size)
    }

    /**
     * The other half of the test above. `content` preferring cached matches is what keeps readable
     * results on screen, and it is also what makes the failure unrenderable there -- so it has to
     * leave through [SearchViewModel.actionError] or not at all.
     */
    @Test
    fun `search failure over a populated mirror reports through actionError`() =
        runTest(testDispatcher) {
            notesMirror.upsert(listOf(makeNote("a", text = "cat food")))
            answerSearch { Result.failure(RuntimeException("offline")) }
            val viewModel = searchViewModel()

            viewModel.actionError.test {
                viewModel.updateSearchQuery("cat")
                advanceTimeBy((DEBOUNCE_MS + 1).milliseconds)
                advanceUntilIdle()

                assertEquals(
                    NoteActionError.Failed(ErrorMessage.Unlocalized("offline")),
                    awaitItem()
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * The two halves of the [SearchViewModel.content] combine must not disagree about which query
     * they are describing.
     *
     * `results` and `searchStatus` are driven by one shared debounced query, so a settled query
     * wakes both from a single emission and the `init` collector -- which subscribes first -- has
     * written `Searching` before `results` switches streams. The pair this guards against is
     * (new query's empty results, previous query's `Settled`), which renders "no results found"
     * for a search that has not run. Back when the two halves each ran their own `debounce` timer,
     * nothing ruled that pair out.
     *
     * The stubbed search is slow on purpose: without it the whole chain settles inside one
     * `advanceTimeBy` and `StateFlow` conflation would hide the intermediate state either way.
     */
    @Test
    fun `switching query goes through Loading rather than a stale no-results`() =
        runTest(testDispatcher) {
            val cat = makeNote("a", text = "cat food")
            stubSearch(listOf(cat), query = "cat")
            val viewModel = searchViewModel()

            viewModel.updateSearchQuery("cat")
            advanceTimeBy((DEBOUNCE_MS + 1).milliseconds)
            advanceUntilIdle()

            viewModel.content.test {
                assertEquals(SearchContent.Success(listOf(cat)), awaitItem())

                answerSearch(query = "zzz") {
                    delay(50.milliseconds)
                    Result.success(emptyList())
                }
                viewModel.updateSearchQuery("zzz")
                advanceTimeBy((DEBOUNCE_MS + 1).milliseconds)

                assertEquals(SearchContent.Loading, awaitItem())

                advanceUntilIdle()
                assertEquals(SearchContent.Success(emptyList()), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `rapid query changes only trigger one search for the last value`() =
        runTest(testDispatcher) {
            val notes = listOf(makeNote("x", text = "final answer"))
            stubSearch(notes, query = "final")
            val viewModel = searchViewModel()

            // Type rapidly — debounce should only fire for the last value
            viewModel.updateSearchQuery("f")
            viewModel.updateSearchQuery("fi")
            viewModel.updateSearchQuery("fin")
            viewModel.updateSearchQuery("final")
            advanceTimeBy((DEBOUNCE_MS + 1).milliseconds)

            assertEquals(listOf("final"), noteRepository.requestedSearches)
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
    fun `refresh re-runs the current query`() = runTest(testDispatcher) {
        stubSearch(listOf(makeNote("r1", text = "cat")), query = "cat")
        val viewModel = searchViewModel()
        viewModel.updateSearchQuery("cat")
        advanceTimeBy((DEBOUNCE_MS + 1).milliseconds)

        viewModel.refresh()
        advanceTimeBy(1.milliseconds)

        assertEquals(listOf("cat", "cat"), noteRepository.requestedSearches)
    }

    @Test
    fun `isRefreshing is false after refresh completes`() = runTest(testDispatcher) {
        val viewModel = searchViewModel()
        viewModel.updateSearchQuery("cat")
        advanceTimeBy((DEBOUNCE_MS + 1).milliseconds)

        viewModel.refresh()
        advanceTimeBy(1.milliseconds)

        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    /**
     * `refresh()` is reachable only from a non-empty `Success`, so a refresh failure always has
     * matches on screen and always takes the snackbar branch -- it can never surface as
     * [SearchContent.Error].
     */
    @Test
    fun `refresh failure over a populated mirror reports through actionError`() =
        runTest(testDispatcher) {
            stubSearch(listOf(makeNote("a", text = "cat food")), query = "cat")
            val viewModel = searchViewModel()
            viewModel.updateSearchQuery("cat")
            advanceTimeBy((DEBOUNCE_MS + 1).milliseconds)

            answerSearch { Result.failure(RuntimeException("refresh failed")) }

            viewModel.actionError.test {
                viewModel.refresh()
                advanceUntilIdle()

                assertEquals(
                    NoteActionError.Failed(ErrorMessage.Unlocalized("refresh failed")),
                    awaitItem()
                )
                cancelAndIgnoreRemainingEvents()
            }
            // The failure did not blank the matches it reported over.
            assertEquals(1, viewModel.notes().size)
        }

    // -------------------------------------------------------------------------
    // Delete note
    // -------------------------------------------------------------------------

    @Test
    fun `deleteNote removes the note from the results`() = runTest(testDispatcher) {
        stubSearch(
            listOf(makeNote("doc1", text = "cat a"), makeNote("doc2", text = "cat b")),
            query = "cat"
        )
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
    fun `deleteNote failure emits action error`() = runTest(testDispatcher) {
        noteRepository.deleteResult = Result.failure(RuntimeException())
        val viewModel = searchViewModel()

        viewModel.actionError.test {
            viewModel.deleteNote("doc1")
            advanceTimeBy(1.milliseconds)
            assertEquals(NoteActionError.Failed(ErrorMessage.Generic), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // Favorite note
    // -------------------------------------------------------------------------

    @Test
    fun `toggleFavorite reaches the results through the mirror`() = runTest(testDispatcher) {
        val note = makeNote("doc1", isFavorite = false, text = "cat a")
        stubSearch(listOf(note), query = "cat")
        val viewModel = searchViewModel()
        viewModel.updateSearchQuery("cat")
        advanceTimeBy((DEBOUNCE_MS + 1).milliseconds)

        viewModel.toggleFavorite(note)
        advanceTimeBy(1.milliseconds)

        assertTrue(viewModel.notes().single().isFavorite) // false → true
    }

    @Test
    fun `toggleFavorite failure emits action error`() = runTest(testDispatcher) {
        noteRepository.favoriteResult = Result.failure(RuntimeException())
        val viewModel = searchViewModel()

        viewModel.actionError.test {
            viewModel.toggleFavorite(makeNote("doc1"))
            advanceTimeBy(1.milliseconds)
            assertEquals(NoteActionError.Failed(ErrorMessage.Generic), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // setNoteToDelete
    // -------------------------------------------------------------------------

    @Test
    fun `setNoteToDelete updates state`() = runTest(testDispatcher) {
        val viewModel = searchViewModel()
        val note = makeNote("doc1")
        viewModel.setNoteToDelete(note)
        assertEquals(note, viewModel.uiState.value.noteToDelete)
    }

    @Test
    fun `setNoteToDelete null clears state`() = runTest(testDispatcher) {
        val viewModel = searchViewModel()
        viewModel.setNoteToDelete(makeNote("doc1"))
        viewModel.setNoteToDelete(null)
        assertNull(viewModel.uiState.value.noteToDelete)
    }

    // -------------------------------------------------------------------------
    // Mutations made elsewhere
    //
    // These used to arrive as NoteHandler events Search collected and applied to its own list.
    // They are writes to the shared table now, so results reflect them by being a read of it.
    // -------------------------------------------------------------------------

    @Test
    fun `a note deleted on another screen leaves the results`() = runTest(testDispatcher) {
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
    fun `a favorite toggled on another screen reaches the results`() = runTest(testDispatcher) {
        stubSearch(
            listOf(
                makeNote("doc1", isFavorite = false, text = "cat a"),
                makeNote("doc2", text = "cat b")
            ),
            query = "cat"
        )
        val viewModel = searchViewModel()
        viewModel.updateSearchQuery("cat")
        advanceTimeBy((DEBOUNCE_MS + 1).milliseconds)

        notesMirror.update { notes ->
            notes.map { if (it.noteId == "doc1") it.copy(isFavorite = true) else it }
        }
        advanceTimeBy(1.milliseconds)

        assertTrue(viewModel.notes().first { it.noteId == "doc1" }.isFavorite)
    }
}