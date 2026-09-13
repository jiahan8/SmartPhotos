package com.jiahan.smartcamera.preview

import app.cash.turbine.test
import com.jiahan.smartcamera.domain.Note
import com.jiahan.smartcamera.fake.FakeErrorHandler
import com.jiahan.smartcamera.fake.FakeMediaCacheRepository
import com.jiahan.smartcamera.fake.FakeNoteRepository
import com.jiahan.smartcamera.note.NoteActionError
import com.jiahan.smartcamera.note.NoteErrorReporter
import com.jiahan.smartcamera.note.NoteShareDelegate
import com.jiahan.smartcamera.note.OutgoingShare
import com.jiahan.smartcamera.util.ErrorMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

/**
 * [NotePreviewViewModel]'s suite, in `commonTest` beside its subject, on :core:domain-testing's
 * fakes with [Dispatchers.setMain] called directly -- `ExploreViewModelTest` records why each of
 * those.
 *
 * It used to run under Robolectric for the reason `EditNoteViewModelTest` did: the ViewModel
 * decoded its route with `toRoute`, whose `RouteDecoder` builds a real `android.os.Bundle`. The
 * decode is `HiltNotePreviewViewModel`'s now, so the ViewModel takes a `noteId` and this suite
 * passes one. The row it renders is the fake's `notes` mirror, which `getNote` writes through as
 * the real repository does, and the [NoteShareDelegate] is a real one where a relaxed mock stood in
 * -- so the share case asserts what reaches the share sheet rather than that a call was made.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotePreviewViewModelTest {

    private val noteRepository = FakeNoteRepository()
    private val errorHandler = FakeErrorHandler()
    private val noteErrorReporter = NoteErrorReporter(errorHandler)
    private val noteShare = NoteShareDelegate(FakeMediaCacheRepository(), noteErrorReporter)

    private val noteId = "note1"

    /** Stands in for the `notes` table. The screen renders this note's row, not the fetch. */
    private val notesMirror = noteRepository.notes

    private val testNote = Note(
        text = "Test note",
        noteId = noteId,
        username = "testUser",
        isFavorite = false
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        noteRepository.getNoteResult = Result.success(testNote)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Builds the ViewModel and subscribes to [NotePreviewViewModel.content], which is shared
     * `WhileSubscribed` and so sits at its initial value with nobody collecting it.
     */
    private fun TestScope.createViewModel(): NotePreviewViewModel {
        val viewModel = NotePreviewViewModel(
            noteId = noteId,
            noteRepository = noteRepository,
            noteErrorReporter = noteErrorReporter,
            errorHandler = errorHandler,
            noteShare = noteShare
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.content.collect { }
        }
        return viewModel
    }

    // -------------------------------------------------------------------------
    // Init / load note
    // -------------------------------------------------------------------------

    @Test
    fun `init loads note and sets Success state`() = runTest {
        val viewModel = createViewModel()

        assertEquals(NotePreviewContent.Success(testNote), viewModel.content.value)
    }

    @Test
    fun `init fetches and observes the row for its own note id`() = runTest {
        val otherNote = testNote.copy(noteId = "note2", text = "Someone else's note")
        notesMirror.set(listOf(otherNote))

        val viewModel = createViewModel()
        // The old ViewModel collected every note's update event and filtered by id by hand. The
        // query is keyed, so an unrelated note's write cannot reach this screen at all.
        notesMirror.update { rows ->
            rows.map { if (it.noteId == "note2") it.copy(text = "Edited elsewhere") else it }
        }

        assertEquals(listOf(noteId), noteRepository.requestedNoteIds)
        assertEquals(NotePreviewContent.Success(testNote), viewModel.content.value)
    }

    @Test
    fun `an edit made on another screen reaches this one`() = runTest {
        val viewModel = createViewModel()

        notesMirror.update { rows ->
            rows.map { if (it.noteId == noteId) it.copy(text = "Edited text") else it }
        }

        val state = viewModel.content.value
        assertTrue(state is NotePreviewContent.Success)
        assertEquals("Edited text", state.note.text)
    }

    @Test
    fun `init failure sets Error state`() = runTest {
        noteRepository.getNoteResult = Result.failure(RuntimeException("not found"))

        val viewModel = createViewModel()

        assertEquals(
            NotePreviewContent.Error(ErrorMessage.Unlocalized("not found")),
            viewModel.content.value
        )
    }

    @Test
    fun `a cached row renders even when the fetch fails`() = runTest {
        notesMirror.set(listOf(testNote))
        noteRepository.getNoteResult = Result.failure(RuntimeException())

        val viewModel = createViewModel()

        assertEquals(NotePreviewContent.Success(testNote), viewModel.content.value)
    }

    // -------------------------------------------------------------------------
    // deleteNote
    // -------------------------------------------------------------------------

    @Test
    fun `deleteNote deletes through the repository`() = runTest {
        val viewModel = createViewModel()

        viewModel.deleteNote(noteId)

        // Was a NoteHandler emission; the row leaving the table is what other screens now see.
        assertEquals(noteId, noteRepository.lastDeletedNoteId)
    }

    @Test
    fun `a deleted note shows Loading rather than an error`() = runTest {
        val viewModel = createViewModel()

        viewModel.deleteNote(noteId)

        // The screen navigates back as it deletes, so a missing row must not flash a failure on
        // the way off the stack.
        assertEquals(NotePreviewContent.Loading, viewModel.content.value)
    }

    @Test
    fun `deleteNote failure emits action error`() = runTest {
        val viewModel = createViewModel()
        noteRepository.deleteResult = Result.failure(RuntimeException())

        viewModel.actionError.test {
            viewModel.deleteNote(noteId)
            assertEquals(NoteActionError.Failed(ErrorMessage.Generic), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // toggleFavorite
    // -------------------------------------------------------------------------

    @Test
    fun `toggleFavorite reaches the screen through the mirror`() = runTest {
        val viewModel = createViewModel()

        viewModel.toggleFavorite(testNote)

        // The ViewModel patches nothing itself: the repository upserts the flipped row and the
        // screen re-reads it.
        val state = viewModel.content.value as NotePreviewContent.Success
        assertTrue(state.note.isFavorite) // false → true
    }

    @Test
    fun `toggleFavorite failure leaves the note as it was`() = runTest {
        val viewModel = createViewModel()
        noteRepository.favoriteResult = Result.failure(RuntimeException())

        viewModel.actionError.test {
            viewModel.toggleFavorite(testNote)
            assertEquals(NoteActionError.Failed(ErrorMessage.Generic), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse((viewModel.content.value as NotePreviewContent.Success).note.isFavorite)
    }

    // -------------------------------------------------------------------------
    // setNoteToDelete
    // -------------------------------------------------------------------------

    @Test
    fun `setNoteToDelete stores the note`() = runTest {
        val viewModel = createViewModel()
        viewModel.setNoteToDelete(testNote)
        assertEquals(testNote, viewModel.uiState.value.noteToDelete)
    }

    @Test
    fun `setNoteToDelete null clears the note`() = runTest {
        val viewModel = createViewModel()
        viewModel.setNoteToDelete(testNote)
        viewModel.setNoteToDelete(null)
        assertNull(viewModel.uiState.value.noteToDelete)
    }

    // -------------------------------------------------------------------------
    // shareNote
    // -------------------------------------------------------------------------

    @Test
    fun `shareNote emits the note's share through NoteShareDelegate`() = runTest {
        val viewModel = createViewModel()

        viewModel.shareEvent.test {
            viewModel.shareNote(testNote)
            // A text-only note needs no download, so the delegate emits straight away.
            assertEquals(OutgoingShare(text = "Test note", uris = emptyList()), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `actionError surfaces errors reported through the shared NoteErrorReporter`() = runTest {
        // NoteShareDelegate reports share failures through the same NoteErrorReporter this
        // ViewModel exposes as its own actionError -- on Android, NoteDelegateModule's
        // ViewModelScoped providers are what make those the same instance, and so the same flow.
        val viewModel = createViewModel()

        viewModel.actionError.test {
            noteErrorReporter.reportShareFailure()
            assertEquals(NoteActionError.ShareFailed, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}