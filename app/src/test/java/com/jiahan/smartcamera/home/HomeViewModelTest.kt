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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
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
        NoteActionsDelegate(noteRepository, NoteErrorReporter(errorHandler))
    }
    private val noteShare: NoteShareDelegate = mockk(relaxed = true)
    private val remoteConfigRepository = FakeRemoteConfigRepository()

    /**
     * Stands in for the `notes` table. The feed is a read of this, not of what `getNotes` returns,
     * so a test drives the UI by what lands here -- whether that is a mirrored page, a mutation
     * writing through, or another screen's write arriving underneath Home.
     */
    private val notesMirror = MutableStateFlow<List<HomeNote>>(emptyList())

    @Before
    fun setUp() {
        every { errorHandler.logError(any()) } just runs
        every { errorHandler.getErrorMessage(any()) } returns "An error occurred"
        every { noteRepository.getNotesStream(any()) } answers {
            val limit = firstArg<Int>()
            notesMirror.map { notes -> notes.take(limit) }
        }
        stubAnyPage(emptyList())
    }

    @After
    fun tearDown() = unmockkAll()

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private object TestCursor : NoteCursor

    private object OtherCursor : NoteCursor

    /**
     * The trailing digits of [id] become an age: `note1` is newer than `note20`, and an id with no
     * digits (`fresh`, `added`) is newest of all. That is what lets [mirror] sort the way the table
     * does, which matters once the feed reads a windowed `LIMIT` rather than everything.
     */
    private fun makeNote(id: String, favorite: Boolean = false) = HomeNote(
        noteId = id,
        text = "Note $id",
        username = "testUser",
        favorite = favorite,
        createdDate = Instant.fromEpochMilliseconds(
            BASE_TIME_MS - (id.takeLastWhile { it.isDigit() }.toLongOrNull() ?: 0L) * 1_000L
        )
    )

    /**
     * Upserts a page into [notesMirror] the way `cacheNotes` upserts it into Room: keyed by note
     * id, appended in page order. Note that nothing is ever removed -- the table is not reconciled
     * against the server, so a reload adds to the mirror rather than replacing it.
     */
    private fun mirror(page: List<HomeNote>) {
        notesMirror.update { existing ->
            val refreshed = existing.map { old ->
                page.firstOrNull { it.noteId == old.noteId } ?: old
            }
            (refreshed + page.filter { new -> existing.none { it.noteId == new.noteId } })
                .sortedByDescending { it.createdDate }
        }
    }

    /** Stubs the page fetched at [cursor], mirroring it on the way out as the real fetch does. */
    private fun stubPage(
        cursor: NoteCursor?,
        notes: List<HomeNote>,
        nextCursor: NoteCursor? = null
    ) {
        coEvery { noteRepository.getNotes(cursor, any()) } coAnswers {
            mirror(notes)
            Result.success(NotePage(notes, nextCursor))
        }
    }

    /** As [stubPage], for any cursor. */
    private fun stubAnyPage(notes: List<HomeNote>, nextCursor: NoteCursor? = null) {
        coEvery { noteRepository.getNotes(any(), any()) } coAnswers {
            mirror(notes)
            Result.success(NotePage(notes, nextCursor))
        }
    }

    /**
     * Builds the ViewModel and subscribes to [HomeViewModel.content], which is shared
     * `WhileSubscribed` and so sits at its initial value while nobody collects it. The screen is
     * that subscriber in production; a test asserting on the feed has to be it.
     */
    private fun TestScope.homeViewModel(): HomeViewModel {
        val viewModel = HomeViewModel(
            noteRepository,
            noteHandler,
            noteActions,
            noteShare,
            errorHandler,
            remoteConfigRepository
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.content.collect { }
        }
        return viewModel
    }

    private fun HomeViewModel.notes(): List<HomeNote> =
        (content.value as HomeContent.Success).notes

    private companion object {
        const val BASE_TIME_MS = 1_700_000_000_000L
    }

    // -------------------------------------------------------------------------
    // Initial load
    // -------------------------------------------------------------------------

    @Test
    fun `init emits Success with empty list when repository returns empty`() = runTest {
        val viewModel = homeViewModel()
        val content = viewModel.content.value
        assertTrue(content is HomeContent.Success)
        assertTrue((content as HomeContent.Success).notes.isEmpty())
    }

    @Test
    fun `init emits Success with notes when the fetched page reaches the mirror`() = runTest {
        val notes = listOf(makeNote("a"), makeNote("b"))
        stubPage(null, notes)
        val viewModel = homeViewModel()

        assertEquals(HomeContent.Success(notes), viewModel.content.value)
    }

    @Test
    fun `init renders the mirror before any fetch completes`() = runTest {
        // A cached feed is readable immediately on launch; the fetch only refreshes it.
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val cached = listOf(makeNote("cached"))
        notesMirror.value = cached
        coEvery { noteRepository.getNotes(any(), any()) } coAnswers {
            delay(1.seconds)
            Result.success(NotePage(emptyList()))
        }

        val viewModel = homeViewModel()
        advanceTimeBy(1.milliseconds) // the fetch is suspended, nothing has settled

        assertEquals(HomeContent.Success(cached), viewModel.content.value)
    }

    @Test
    fun `init stays Loading while the first fetch is in flight over an empty mirror`() = runTest {
        // The race the mirror introduces: Room answers (with nothing) long before Firestore does,
        // so a fresh install must not be told to create its first note while its notes are still
        // downloading.
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        coEvery { noteRepository.getNotes(any(), any()) } coAnswers {
            delay(1.seconds)
            mirror(listOf(makeNote("a")))
            Result.success(NotePage(listOf(makeNote("a"))))
        }

        val viewModel = homeViewModel()
        advanceTimeBy(1.milliseconds)

        assertEquals(HomeContent.Loading, viewModel.content.value)

        advanceUntilIdle()
        assertEquals(1, viewModel.notes().size)
    }

    @Test
    fun `init emits Error state when the fetch fails over an empty mirror`() = runTest {
        val exception = RuntimeException("network error")
        coEvery { noteRepository.getNotes(any(), any()) } returns Result.failure(exception)
        every { errorHandler.getErrorMessage(exception) } returns "network error"
        val viewModel = homeViewModel()

        val content = viewModel.content.value
        assertTrue(content is HomeContent.Error)
        assertEquals("network error", (content as HomeContent.Error).message)
    }

    @Test
    fun `init keeps a populated mirror on screen when the fetch fails`() = runTest {
        val cached = listOf(makeNote("cached"))
        notesMirror.value = cached
        val exception = RuntimeException("offline")
        coEvery { noteRepository.getNotes(any(), any()) } returns Result.failure(exception)
        every { errorHandler.getErrorMessage(exception) } returns "offline"

        val viewModel = homeViewModel()

        // Readable notes beat an error screen; the failure travels as a transient signal instead.
        assertEquals(HomeContent.Success(cached), viewModel.content.value)
    }

    // -------------------------------------------------------------------------
    // Refresh
    // -------------------------------------------------------------------------

    @Test
    fun `refresh reloads page 0 and updates state`() = runTest {
        val viewModel = homeViewModel()
        val refreshedNotes = listOf(makeNote("r1"), makeNote("r2"))
        stubPage(null, refreshedNotes)

        viewModel.refresh()

        assertEquals(HomeContent.Success(refreshedNotes), viewModel.content.value)
    }

    @Test
    fun `refresh always requests page 0`() = runTest {
        val viewModel = homeViewModel()
        viewModel.refresh()
        coVerify { noteRepository.getNotes(null, any()) }
    }

    @Test
    fun `isRefreshing is false after refresh completes`() = runTest {
        val viewModel = homeViewModel()
        viewModel.refresh()
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun `refresh failure keeps the cached feed instead of blanking it`() = runTest {
        val initialNotes = listOf(makeNote("a"), makeNote("b"))
        stubPage(null, initialNotes)
        val viewModel = homeViewModel()
        assertTrue(viewModel.content.value is HomeContent.Success)

        val exception = RuntimeException("refresh failed")
        coEvery { noteRepository.getNotes(null, any()) } returns Result.failure(exception)
        every { errorHandler.getErrorMessage(exception) } returns "refresh failed"

        viewModel.refresh()

        // Before the mirror existed this blanked the list for a full-screen error. The rows are
        // still in the table and still readable, so throwing them away over a failed refresh would
        // be a regression, not a safeguard.
        assertEquals(HomeContent.Success(initialNotes), viewModel.content.value)
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun `refresh failure over a populated mirror reports through actionError`() = runTest {
        stubPage(null, listOf(makeNote("a")))
        val viewModel = homeViewModel()

        val exception = RuntimeException("refresh failed")
        coEvery { noteRepository.getNotes(null, any()) } returns Result.failure(exception)
        every { errorHandler.getErrorMessage(exception) } returns "refresh failed"

        viewModel.actionError.test {
            viewModel.refresh()
            assertEquals("refresh failed", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // Load more
    // -------------------------------------------------------------------------

    @Test
    fun `loadMoreNotes appends second page to existing notes`() = runTest {
        val page0 = (1..10).map { makeNote("note$it") }
        val page1 = (11..15).map { makeNote("note$it") }
        stubPage(null, page0, TestCursor)
        stubPage(TestCursor, page1)
        val viewModel = homeViewModel()

        viewModel.loadMoreNotes()

        assertEquals(15, viewModel.notes().size)
    }

    @Test
    fun `loadMoreNotes does nothing when the page reports no more data`() = runTest {
        stubAnyPage(listOf(makeNote("a"), makeNote("b")))
        val viewModel = homeViewModel() // triggers the init fetch

        // Reset recorded calls (keep stubs) so we measure only what loadMoreNotes triggers
        clearMocks(noteRepository, answers = false)

        viewModel.loadMoreNotes() // hasMoreData = false → should be a no-op

        coVerify(exactly = 0) { noteRepository.getNotes(any(), any()) }
    }

    @Test
    fun `loadMoreNotes still fetches when a full page mapped to fewer notes than pageSize`() =
        runTest {
            // A note whose author lookup failed is dropped from the page, so notes.size <
            // DEFAULT_PAGE_SIZE even though the query had a full page of rows. hasMore, not the
            // mapped list's length, decides whether pagination continues.
            stubPage(null, (1..9).map { makeNote("note$it") }, TestCursor)
            stubPage(TestCursor, listOf(makeNote("note10")))
            val viewModel = homeViewModel()

            viewModel.loadMoreNotes()

            assertEquals(10, viewModel.notes().size)
        }

    @Test
    fun `isLoadingMore is false after loadMoreNotes completes`() = runTest {
        stubPage(null, (1..10).map { makeNote("note$it") }, TestCursor)
        stubPage(TestCursor, emptyList())
        val viewModel = homeViewModel()

        viewModel.loadMoreNotes()

        assertFalse(viewModel.uiState.value.isLoadingMore)
    }

    @Test
    fun `isLoadingMore is true while loadMoreNotes is in progress`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        stubPage(null, (1..10).map { makeNote("note$it") }, TestCursor)
        coEvery { noteRepository.getNotes(TestCursor, any()) } coAnswers {
            delay(1.seconds); Result.success(NotePage(emptyList()))
        }
        val viewModel = homeViewModel()
        advanceUntilIdle() // let init fetch complete

        viewModel.loadMoreNotes()
        advanceTimeBy(1.milliseconds) // let loadMoreNotes start; page-1 fetch suspends at delay(1s)
        assertTrue(viewModel.uiState.value.isLoadingMore)

        advanceUntilIdle() // complete the delay
        assertFalse(viewModel.uiState.value.isLoadingMore)
    }

    @Test
    fun `loadMoreNotes failure leaves the feed and the cursor untouched`() = runTest {
        stubPage(null, (1..10).map { makeNote("note$it") }, TestCursor)
        coEvery {
            noteRepository.getNotes(
                TestCursor,
                any()
            )
        } returns Result.failure(RuntimeException("page fail"))
        val viewModel = homeViewModel()

        viewModel.loadMoreNotes()

        // Existing notes are unchanged despite the page-1 failure
        assertEquals(10, viewModel.notes().size)
        assertFalse(viewModel.uiState.value.isLoadingMore)
    }

    @Test
    fun `loadMoreNotes failure stays silent`() = runTest {
        stubPage(null, (1..10).map { makeNote("note$it") }, TestCursor)
        coEvery { noteRepository.getNotes(TestCursor, any()) } coAnswers {
            delay(1.milliseconds); Result.failure(RuntimeException("page fail"))
        }
        val viewModel = homeViewModel()

        // A page that failed to append changes nothing the user can see, so unlike a failed
        // refresh it raises no snackbar.
        viewModel.actionError.test {
            viewModel.loadMoreNotes()
            advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun `loadMoreNotes is ignored while a refresh is in flight`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val page0 = (1..10).map { makeNote("note$it") }
        stubPage(null, page0, TestCursor)
        val viewModel = homeViewModel()
        advanceUntilIdle()

        coEvery { noteRepository.getNotes(null, any()) } coAnswers {
            delay(1.seconds); Result.success(NotePage(page0, TestCursor))
        }
        viewModel.refresh()
        advanceTimeBy(1.milliseconds) // refresh is suspended mid-fetch
        clearMocks(noteRepository, answers = false)

        viewModel.loadMoreNotes()
        advanceUntilIdle()

        // refresh() has already reset the cursor, so an unguarded load-more would refetch the
        // first page against a position the refresh is about to overwrite.
        coVerify(exactly = 0) { noteRepository.getNotes(any(), any()) }
    }

    @Test
    fun `refresh cancels an in-flight load more so its page never reaches the mirror`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val page0 = (1..10).map { makeNote("note$it") }
        stubPage(null, page0, TestCursor)
        coEvery { noteRepository.getNotes(TestCursor, any()) } coAnswers {
            delay(1.seconds)
            mirror((11..20).map { makeNote("note$it") })
            Result.success(NotePage((11..20).map { makeNote("note$it") }, OtherCursor))
        }
        val viewModel = homeViewModel()
        advanceUntilIdle()

        viewModel.loadMoreNotes()
        advanceTimeBy(1.milliseconds) // page-2 fetch is suspended

        stubPage(null, listOf(makeNote("fresh")))
        viewModel.refresh()
        advanceUntilIdle()

        // The cancelled page never landed, so it neither shows up in the feed nor advances the
        // cursor past a window nobody kept.
        val pageTwoIds = (11..20).map { "note$it" }
        assertTrue(viewModel.notes().none { it.noteId in pageTwoIds })
        assertTrue(viewModel.notes().any { it.noteId == "fresh" })
        assertFalse(viewModel.uiState.value.isLoadingMore)
    }

    @Test
    fun `noteAddedEvent cancels an in-flight load more and refetches the first page`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val page0 = (1..10).map { makeNote("note$it") }
        stubPage(null, page0, TestCursor)
        coEvery { noteRepository.getNotes(TestCursor, any()) } coAnswers {
            delay(1.seconds)
            mirror((11..20).map { makeNote("note$it") })
            Result.success(NotePage((11..20).map { makeNote("note$it") }))
        }
        val viewModel = homeViewModel()
        advanceUntilIdle()

        viewModel.loadMoreNotes()
        advanceTimeBy(1.milliseconds) // page-2 fetch is suspended

        // addNote never mirrors its own result, so the refetch is how the new note arrives.
        stubPage(null, listOf(makeNote("added")) + page0)
        noteHandler.notifyNoteAdded()
        advanceUntilIdle()

        assertTrue(viewModel.notes().any { it.noteId == "added" })
        assertTrue(viewModel.notes().none { it.noteId == "note11" })
    }

    // -------------------------------------------------------------------------
    // Delete note
    // -------------------------------------------------------------------------

    @Test
    fun `deleteNote success removes the note from the feed`() = runTest {
        stubPage(null, listOf(makeNote("doc1"), makeNote("doc2")))
        coEvery { noteRepository.deleteNote("doc1") } coAnswers {
            // The repository drops the row; the feed re-emits without it, with no list patch here.
            notesMirror.update { notes -> notes.filterNot { it.noteId == "doc1" } }
            Result.success(Unit)
        }
        val viewModel = homeViewModel()

        viewModel.deleteNote("doc1")

        assertEquals(1, viewModel.notes().size)
        assertEquals("doc2", viewModel.notes().first().noteId)
    }

    @Test
    fun `deleteNote failure emits action error message`() = runTest {
        coEvery { noteRepository.deleteNote(any()) } returns Result.failure(RuntimeException("fail"))
        every { errorHandler.getErrorMessage(any()) } returns "delete failed"
        val viewModel = homeViewModel()

        viewModel.actionError.test {
            viewModel.deleteNote("doc1")
            assertEquals("delete failed", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteNote failure does not change the feed`() = runTest {
        stubPage(null, listOf(makeNote("doc1"), makeNote("doc2")))
        coEvery { noteRepository.deleteNote(any()) } returns Result.failure(RuntimeException())
        val viewModel = homeViewModel()

        viewModel.deleteNote("doc1")

        assertEquals(2, viewModel.notes().size)
    }

    // -------------------------------------------------------------------------
    // Favorite note
    // -------------------------------------------------------------------------

    @Test
    fun `favoriteNote toggle reaches the feed through the mirror`() = runTest {
        stubPage(null, listOf(makeNote("doc1", favorite = false)))
        val note = makeNote("doc1", favorite = false)
        coEvery { noteRepository.favoriteNote(note) } coAnswers {
            // The repository upserts the flipped row; nothing here patches the list.
            notesMirror.update { notes ->
                notes.map { if (it.noteId == "doc1") it.copy(favorite = true) else it }
            }
            Result.success(Unit)
        }
        val viewModel = homeViewModel()

        viewModel.favoriteNote(note)

        assertTrue(viewModel.notes().single().favorite) // false → true
    }

    @Test
    fun `favoriteNote unfavoriting reaches the feed the same way`() = runTest {
        stubPage(null, listOf(makeNote("doc1", favorite = true)))
        val note = makeNote("doc1", favorite = true)
        coEvery { noteRepository.favoriteNote(note) } coAnswers {
            notesMirror.update { notes ->
                notes.map { if (it.noteId == "doc1") it.copy(favorite = false) else it }
            }
            Result.success(Unit)
        }
        val viewModel = homeViewModel()

        viewModel.favoriteNote(note)

        // The row stays in the table either way -- that is the distinction the feed's query draws
        // and the favorites query does not.
        assertFalse(viewModel.notes().single().favorite) // true → false
    }

    @Test
    fun `favoriteNote failure emits action error`() = runTest {
        coEvery { noteRepository.favoriteNote(any()) } returns Result.failure(RuntimeException())
        every { errorHandler.getErrorMessage(any()) } returns "fav error"
        val viewModel = homeViewModel()

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
        val viewModel = homeViewModel()
        val note = makeNote("doc1")
        viewModel.setNoteToDelete(note)
        assertEquals(note, viewModel.uiState.value.noteToDelete)
    }

    @Test
    fun `setNoteToDelete with null clears the note`() = runTest {
        val viewModel = homeViewModel()
        viewModel.setNoteToDelete(makeNote("doc1"))
        viewModel.setNoteToDelete(null)
        assertNull(viewModel.uiState.value.noteToDelete)
    }

    // -------------------------------------------------------------------------
    // Mutations made elsewhere
    //
    // These used to arrive as NoteHandler events Home collected and applied to its own list. They
    // are writes to the shared table now, so Home sees them by observing it and needs no
    // cross-feature wiring at all.
    // -------------------------------------------------------------------------

    @Test
    fun `a note deleted on another screen leaves the feed`() = runTest {
        stubPage(null, listOf(makeNote("doc1"), makeNote("doc2"), makeNote("doc3")))
        val viewModel = homeViewModel()

        notesMirror.update { notes -> notes.filterNot { it.noteId == "doc2" } }

        assertEquals(2, viewModel.notes().size)
        assertFalse(viewModel.notes().any { it.noteId == "doc2" })
    }

    @Test
    fun `a favorite toggled on another screen reaches the feed`() = runTest {
        stubPage(null, listOf(makeNote("doc1", favorite = false), makeNote("doc2")))
        val viewModel = homeViewModel()

        notesMirror.update { notes ->
            notes.map { if (it.noteId == "doc1") it.copy(favorite = true) else it }
        }

        assertTrue(viewModel.notes().first { it.noteId == "doc1" }.favorite)
        assertFalse(viewModel.notes().first { it.noteId == "doc2" }.favorite)
    }

    @Test
    fun `an edit made on another screen reaches the feed`() = runTest {
        stubPage(null, listOf(makeNote("doc1"), makeNote("doc2")))
        val viewModel = homeViewModel()

        notesMirror.update { notes ->
            notes.map { if (it.noteId == "doc1") it.copy(text = "edited") else it }
        }

        assertEquals("edited", viewModel.notes().first { it.noteId == "doc1" }.text)
    }

    // -------------------------------------------------------------------------
    // Explore icon visibility (Remote Config)
    // -------------------------------------------------------------------------

    @Test
    fun `isExploreIconVisible reflects Remote Config value at construction`() = runTest {
        remoteConfigRepository.setExploreIconVisible(false)
        val viewModel = homeViewModel()

        assertFalse(viewModel.uiState.value.isExploreIconVisible)
    }

    @Test
    fun `isExploreIconVisible updates live when Remote Config pushes a change`() = runTest {
        remoteConfigRepository.setExploreIconVisible(true)
        val viewModel = homeViewModel()
        assertTrue(viewModel.uiState.value.isExploreIconVisible)

        remoteConfigRepository.setExploreIconVisible(false)

        assertFalse(viewModel.uiState.value.isExploreIconVisible)
    }
}