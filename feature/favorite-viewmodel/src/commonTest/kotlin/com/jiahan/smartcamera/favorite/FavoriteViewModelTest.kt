package com.jiahan.smartcamera.favorite

import app.cash.turbine.test
import com.jiahan.smartcamera.domain.Note
import com.jiahan.smartcamera.fake.FakeAnalyticsRepository
import com.jiahan.smartcamera.fake.FakeErrorHandler
import com.jiahan.smartcamera.fake.FakeMediaCacheRepository
import com.jiahan.smartcamera.fake.FakeNoteRepository
import com.jiahan.smartcamera.note.NoteActionError
import com.jiahan.smartcamera.note.NoteErrorReporter
import com.jiahan.smartcamera.note.NoteShareDelegate
import com.jiahan.smartcamera.util.AppConstants
import com.jiahan.smartcamera.util.ErrorMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
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
import kotlin.time.Duration.Companion.milliseconds

/**
 * [FavoriteViewModel]'s suite, in `commonTest` beside its subject: :core:domain-testing's fakes
 * where mockk was and [Dispatchers.setMain] where `MainDispatcherRule` was -- `ExploreViewModelTest`
 * records why each of those.
 *
 * The favorites stream is the fake's, seeded with [FakeNoteRepository.setFavorites] and filtered
 * by query as the real one is, where each test used to stub a `flowOf` per query. That turned the
 * search case into a real filtering case: it seeds a match and a non-match, rather than stubbing
 * the answer for one query.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FavoriteViewModelTest {

    private val testDispatcher: TestDispatcher = StandardTestDispatcher()

    private val noteRepository = FakeNoteRepository()
    private val analyticsRepository = FakeAnalyticsRepository()
    private val errorHandler = FakeErrorHandler()
    private val noteErrorReporter = NoteErrorReporter(errorHandler)
    private val noteShare = NoteShareDelegate(FakeMediaCacheRepository(), noteErrorReporter)

    private lateinit var viewModel: FavoriteViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = buildViewModel()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Rebuilt per test rather than only in [setUp], because `init` runs the sync: a test that wants
     * to see a *failed* one has to set the result before the ViewModel exists.
     */
    private fun buildViewModel() = FavoriteViewModel(
        noteRepository,
        analyticsRepository,
        noteErrorReporter,
        noteShare,
        errorHandler
    )

    private fun makeNote(id: String, isFavorite: Boolean = true, text: String = "text $id") = Note(
        noteId = id, username = "user", isFavorite = isFavorite, text = text
    )

    // -------------------------------------------------------------------------
    // Init / sync
    // -------------------------------------------------------------------------

    @Test
    fun `init triggers syncFavoriteNotes`() = runTest(testDispatcher) {
        advanceUntilIdle()
        assertEquals(1, noteRepository.syncCallCount)
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
        runTest(testDispatcher) {
            noteRepository.syncResult = Result.failure(RuntimeException("sync"))
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
    fun `refresh calls syncFavoriteNotes again`() = runTest(testDispatcher) {
        advanceUntilIdle() // complete init sync
        noteRepository.syncCallCount = 0

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(1, noteRepository.syncCallCount)
    }

    @Test
    fun `isRefreshing is false after refresh completes`() = runTest(testDispatcher) {
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
    fun `sync failure over an empty mirror surfaces as an error`() = runTest(testDispatcher) {
        noteRepository.syncResult = Result.failure(RuntimeException("offline"))
        val viewModel = buildViewModel()

        viewModel.content.test {
            advanceUntilIdle()
            assertEquals(
                FavoriteContent.Error(ErrorMessage.Unlocalized("offline")),
                expectMostRecentItem()
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sync failure over a populated mirror keeps the favorites on screen`() =
        runTest(testDispatcher) {
            val cached = listOf(makeNote("doc1"))
            noteRepository.setFavorites(cached)
            noteRepository.syncResult = Result.failure(RuntimeException("offline"))
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
        runTest(testDispatcher) {
            noteRepository.setFavorites(listOf(makeNote("doc1")))
            noteRepository.syncResult = Result.failure(RuntimeException("offline"))
            val viewModel = buildViewModel()

            viewModel.actionError.test {
                advanceUntilIdle()
                assertEquals(
                    NoteActionError.Failed(ErrorMessage.Unlocalized("offline")),
                    awaitItem()
                )
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
    fun `content reflects search query after debounce`() = runTest(testDispatcher) {
        val catNap = makeNote("doc1", text = "cats nap")
        noteRepository.setFavorites(listOf(catNap, makeNote("doc2", text = "dogs bark")))

        viewModel.content.test {
            assertEquals(FavoriteContent.Loading, awaitItem()) // stateIn initialValue

            viewModel.updateSearchQuery("cats")
            advanceTimeBy((AppConstants.DEBOUNCE_MS + 1).milliseconds)
            advanceUntilIdle()

            // Settled state only -- the sync flag and the debounced query stream can interleave
            // their intermediate emissions, so assert the final value rather than exact order.
            assertEquals(FavoriteContent.Success(listOf(catNap)), expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // Delete note
    // -------------------------------------------------------------------------

    @Test
    fun `deleteNote deletes through the repository`() = runTest(testDispatcher) {
        viewModel.deleteNote("doc1")
        advanceUntilIdle()

        // This used to assert a NoteHandler emission. The delete is a write to the shared table
        // now, so what other screens see is the row leaving it, not an event.
        assertEquals("doc1", noteRepository.lastDeletedNoteId)
    }

    @Test
    fun `deleteNote failure emits action error`() = runTest(testDispatcher) {
        noteRepository.deleteResult = Result.failure(RuntimeException())

        viewModel.actionError.test {
            viewModel.deleteNote("doc1")
            advanceUntilIdle()
            assertEquals(NoteActionError.Failed(ErrorMessage.Generic), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // Favorite note
    // -------------------------------------------------------------------------

    @Test
    fun `toggleFavorite goes through the repository`() = runTest(testDispatcher) {
        val note = makeNote("doc1", isFavorite = true)

        viewModel.toggleFavorite(note)
        advanceUntilIdle()

        // The repository owns the toggle and upserts the flipped row; the delegate no longer
        // announces it, because every screen reads that row.
        assertEquals(note, noteRepository.lastFavoritedNote)
    }

    @Test
    fun `toggleFavorite failure emits action error`() = runTest(testDispatcher) {
        noteRepository.favoriteResult = Result.failure(RuntimeException())

        viewModel.actionError.test {
            viewModel.toggleFavorite(makeNote("doc1"))
            advanceUntilIdle()
            assertEquals(NoteActionError.Failed(ErrorMessage.Generic), awaitItem())
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