package com.jiahan.smartcamera.favorite

import app.cash.turbine.test
import com.jiahan.smartcamera.MainDispatcherRule
import com.jiahan.smartcamera.data.repository.AnalyticsRepository
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.note.NoteErrorReporter
import com.jiahan.smartcamera.note.NoteShareDelegate
import com.jiahan.smartcamera.util.AppConstants
import com.jiahan.smartcamera.util.ErrorHandler
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.milliseconds
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FavoriteViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val noteRepository: NoteRepository = mockk()
    private val analyticsRepository: AnalyticsRepository = mockk()
    private val errorHandler: ErrorHandler = mockk()
    private val noteErrorReporter by lazy { NoteErrorReporter(errorHandler) }
    private val noteShare: NoteShareDelegate = mockk(relaxed = true)

    private lateinit var viewModel: FavoriteViewModel

    @Before
    fun setUp() {
        every { analyticsRepository.logFavoriteSearchCustomEvent(any()) } just runs
        every { errorHandler.logError(any()) } just runs
        every { errorHandler.getErrorMessage(any()) } returns "Error"
        coEvery { noteRepository.syncFavoriteNotes() } returns Result.success(Unit)
        every { noteRepository.getFavoriteNotesStream(any()) } returns flowOf(emptyList())
        viewModel = buildViewModel()
    }

    /**
     * Rebuilt per test rather than only in [setUp], because `init` runs the sync: a test that wants
     * to see a *failed* one has to re-stub before the ViewModel exists.
     */
    private fun buildViewModel() = FavoriteViewModel(
        noteRepository,
        analyticsRepository,
        noteErrorReporter,
        noteShare,
        errorHandler
    )

    @After
    fun tearDown() = unmockkAll()

    private fun makeNote(id: String, favorite: Boolean = true) = HomeNote(
        noteId = id, username = "user", favorite = favorite, text = "text $id"
    )

    // -------------------------------------------------------------------------
    // Init / sync
    // -------------------------------------------------------------------------

    @Test
    fun `init triggers syncFavoriteNotes`() = runTest(mainDispatcherRule.testDispatcher) {
        advanceUntilIdle()
        coVerify { noteRepository.syncFavoriteNotes() }
    }

    /**
     * The negative half of the split, and this test used to assert the opposite.
     *
     * It expected a snackbar here, which was right while an empty mirror rendered
     * "favorite a note to see it here" -- the transient signal was the *only* way a failed sync
     * reached the user. Now the failure is the screen, so a snackbar repeating it would be the
     * same message twice. The emission moved to the populated-mirror case below, which is where
     * `content` cannot show the failure.
     */
    @Test
    fun `init sync failure over an empty mirror stays silent on actionError`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { noteRepository.syncFavoriteNotes() } returns
                    Result.failure(RuntimeException("sync"))
            every { errorHandler.getErrorMessage(any()) } returns "sync error"
            val viewModel = buildViewModel()

            viewModel.actionError.test {
                advanceUntilIdle()
                expectNoEvents()
            }
        }

    // -------------------------------------------------------------------------
    // Refresh
    // -------------------------------------------------------------------------

    @Test
    fun `refresh calls syncFavoriteNotes again`() = runTest(mainDispatcherRule.testDispatcher) {
        advanceUntilIdle() // complete init sync
        clearMocks(noteRepository, answers = false)

        viewModel.refresh()
        advanceUntilIdle()

        coVerify(exactly = 1) { noteRepository.syncFavoriteNotes() }
    }

    @Test
    fun `isRefreshing is false after refresh completes`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.refresh()
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.isRefreshing)
        }

    // -------------------------------------------------------------------------
    // Sync failure
    // -------------------------------------------------------------------------

    /**
     * The case a plain `isSyncing` boolean could not express.
     *
     * With no cached favorites and a sync that failed, the screen used to render
     * "favorite a note to see it here" -- an empty *result*, when the truth was an empty *mirror*
     * plus a failure. Those are different things and only one of them is the user's fault.
     */
    @Test
    fun `sync failure over an empty mirror surfaces as an error`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val exception = RuntimeException("offline")
            coEvery { noteRepository.syncFavoriteNotes() } returns Result.failure(exception)
            every { errorHandler.getErrorMessage(exception) } returns "offline"
            val viewModel = buildViewModel()

            viewModel.content.test {
                advanceUntilIdle()
                assertEquals(FavoriteContent.Error("offline"), expectMostRecentItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `sync failure over a populated mirror keeps the favorites on screen`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val cached = listOf(makeNote("doc1"))
            every { noteRepository.getFavoriteNotesStream(any()) } returns flowOf(cached)
            val exception = RuntimeException("offline")
            coEvery { noteRepository.syncFavoriteNotes() } returns Result.failure(exception)
            every { errorHandler.getErrorMessage(exception) } returns "offline"
            val viewModel = buildViewModel()

            viewModel.content.test {
                advanceUntilIdle()
                // Readable favorites beat an error screen; the failure travels transiently instead.
                assertEquals(FavoriteContent.Success(cached), expectMostRecentItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `sync failure over a populated mirror reports through actionError`() =
        runTest(mainDispatcherRule.testDispatcher) {
            every { noteRepository.getFavoriteNotesStream(any()) } returns
                    flowOf(listOf(makeNote("doc1")))
            val exception = RuntimeException("offline")
            coEvery { noteRepository.syncFavoriteNotes() } returns Result.failure(exception)
            every { errorHandler.getErrorMessage(exception) } returns "offline"
            val viewModel = buildViewModel()

            viewModel.actionError.test {
                advanceUntilIdle()
                assertEquals("offline", awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // -------------------------------------------------------------------------
    // Search query
    // -------------------------------------------------------------------------

    @Test
    fun `updateSearchQuery updates searchQuery state`() {
        viewModel.updateSearchQuery("cats")
        assertEquals("cats", viewModel.uiState.value.searchQuery)
    }

    @Test
    fun `content reflects search query after debounce`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val filteredNotes = listOf(makeNote("doc1"))
            every { noteRepository.getFavoriteNotesStream("cats") } returns flowOf(filteredNotes)

            viewModel.content.test {
                assertEquals(FavoriteContent.Loading, awaitItem()) // stateIn initialValue

                viewModel.updateSearchQuery("cats")
                advanceTimeBy((AppConstants.DEBOUNCE_MS + 1).milliseconds)
                advanceUntilIdle()

                // Settled state only -- the sync flag and the debounced query stream can interleave
                // their intermediate emissions, so assert the final value rather than exact order.
                assertEquals(FavoriteContent.Success(filteredNotes), expectMostRecentItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // -------------------------------------------------------------------------
    // Delete note
    // -------------------------------------------------------------------------

    @Test
    fun `deleteNote deletes through the repository`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { noteRepository.deleteNote("doc1") } returns Result.success(Unit)

        viewModel.deleteNote("doc1")
        advanceUntilIdle()

        // This used to assert a NoteHandler emission. The delete is a write to the shared table
        // now, so what other screens see is the row leaving it, not an event.
        coVerify { noteRepository.deleteNote("doc1") }
    }

    @Test
    fun `deleteNote failure emits action error`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { noteRepository.deleteNote(any()) } returns Result.failure(RuntimeException())
        every { errorHandler.getErrorMessage(any()) } returns "delete error"

        viewModel.actionError.test {
            viewModel.deleteNote("doc1")
            advanceUntilIdle()
            assertEquals("delete error", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // Favorite note
    // -------------------------------------------------------------------------

    @Test
    fun `favoriteNote toggles through the repository`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val note = makeNote("doc1", favorite = true)
            coEvery { noteRepository.favoriteNote(note) } returns Result.success(Unit)

            viewModel.favoriteNote(note)
            advanceUntilIdle()

            // The repository owns the toggle and upserts the flipped row; the delegate no longer
            // announces it, because every screen reads that row.
            coVerify { noteRepository.favoriteNote(note) }
        }

    @Test
    fun `favoriteNote failure emits action error`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { noteRepository.favoriteNote(any()) } returns Result.failure(RuntimeException())
        every { errorHandler.getErrorMessage(any()) } returns "fav error"

        viewModel.actionError.test {
            viewModel.favoriteNote(makeNote("doc1"))
            advanceUntilIdle()
            assertEquals("fav error", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // setNoteToDelete
    // -------------------------------------------------------------------------

    @Test
    fun `setNoteToDelete sets the note`() {
        val note = makeNote("doc1")
        viewModel.setNoteToDelete(note)
        assertEquals(note, viewModel.uiState.value.noteToDelete)
    }

    @Test
    fun `setNoteToDelete null clears the note`() {
        viewModel.setNoteToDelete(makeNote("doc1"))
        viewModel.setNoteToDelete(null)
        assertNull(viewModel.uiState.value.noteToDelete)
    }
}