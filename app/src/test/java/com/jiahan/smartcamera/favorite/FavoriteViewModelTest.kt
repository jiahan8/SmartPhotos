package com.jiahan.smartcamera.favorite

import app.cash.turbine.test
import com.jiahan.smartcamera.MainDispatcherRule
import com.jiahan.smartcamera.data.repository.AnalyticsRepository
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.note.NoteActionsDelegate
import com.jiahan.smartcamera.note.NoteHandler
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
    private val noteHandler = NoteHandler()
    private val errorHandler: ErrorHandler = mockk()
    private val noteActions by lazy {
        NoteActionsDelegate(
            noteRepository,
            noteHandler,
            errorHandler
        )
    }

    private lateinit var viewModel: FavoriteViewModel

    @Before
    fun setUp() {
        every { analyticsRepository.logFavoriteSearchCustomEvent(any()) } just runs
        every { errorHandler.logError(any()) } just runs
        every { errorHandler.getErrorMessage(any()) } returns "Error"
        coEvery { noteRepository.syncFavoriteNotes() } returns Result.success(Unit)
        every { noteRepository.getFavoriteNotesStream(any()) } returns flowOf(emptyList())
        viewModel =
            FavoriteViewModel(noteRepository, analyticsRepository, noteActions, errorHandler)
    }

    @After
    fun tearDown() = unmockkAll()

    private fun makeNote(id: String, favorite: Boolean = true) = HomeNote(
        documentPath = id, username = "user", favorite = favorite, text = "text $id"
    )

    // -------------------------------------------------------------------------
    // Init / sync
    // -------------------------------------------------------------------------

    @Test
    fun `init triggers syncFavoriteNotes`() = runTest(mainDispatcherRule.testDispatcher) {
        advanceUntilIdle()
        coVerify { noteRepository.syncFavoriteNotes() }
    }

    @Test
    fun `init sync failure emits action error`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { noteRepository.syncFavoriteNotes() } returns Result.failure(RuntimeException("sync"))
        every { errorHandler.getErrorMessage(any()) } returns "sync error"
        val vm = FavoriteViewModel(noteRepository, analyticsRepository, noteActions, errorHandler)

        vm.actionError.test {
            advanceUntilIdle()
            assertEquals("sync error", awaitItem())
            cancelAndIgnoreRemainingEvents()
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
    // Search query
    // -------------------------------------------------------------------------

    @Test
    fun `updateSearchQuery updates searchQuery state`() {
        viewModel.updateSearchQuery("cats")
        assertEquals("cats", viewModel.uiState.value.searchQuery)
    }

    @Test
    fun `notes stream reflects search query after debounce`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val filteredNotes = listOf(makeNote("doc1"))
            every { noteRepository.getFavoriteNotesStream("cats") } returns flowOf(filteredNotes)

            viewModel.notes.test {
                assertEquals(emptyList<HomeNote>(), awaitItem()) // stateIn initialValue

                // Let the initial debounce on "" fire; produces the same emptyList so no re-emission
                advanceTimeBy((AppConstants.DEBOUNCE_MS + 1).milliseconds)

                viewModel.updateSearchQuery("cats")
                advanceTimeBy((AppConstants.DEBOUNCE_MS + 1).milliseconds)

                assertEquals(filteredNotes, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // -------------------------------------------------------------------------
    // Delete note
    // -------------------------------------------------------------------------

    @Test
    fun `deleteNote success notifies NoteHandler`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { noteRepository.deleteNote("doc1") } returns Result.success(Unit)

        noteHandler.noteDeletedEvent.test {
            viewModel.deleteNote("doc1")
            advanceUntilIdle()
            assertEquals("doc1", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
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
    fun `favoriteNote success notifies NoteHandler with toggled value`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val note = makeNote("doc1", favorite = true)
            coEvery { noteRepository.favoriteNote(note) } returns Result.success(Unit)

            noteHandler.noteFavoritedEvent.test {
                viewModel.favoriteNote(note)
                advanceUntilIdle()
                val emitted = awaitItem()
                assertEquals("doc1", emitted.documentPath)
                assertFalse(emitted.favorite) // true → false
                cancelAndIgnoreRemainingEvents()
            }
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