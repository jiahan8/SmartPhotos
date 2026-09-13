package com.jiahan.smartcamera.preview

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.jiahan.smartcamera.MainDispatcherRule
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.domain.Note
import com.jiahan.smartcamera.note.NoteActionError
import com.jiahan.smartcamera.note.NoteErrorReporter
import com.jiahan.smartcamera.note.NoteShareDelegate
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.ErrorMessage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * [NotePreviewViewModel] parses its typed nav route via [androidx.navigation.toRoute], whose
 * internal `RouteDecoder` constructs a real [android.os.Bundle] — that needs Robolectric's shadow
 * to work outside a real Android runtime, hence Robolectric here.
 *
 * A plain [Application] stands in for `MyApp` (as in `BaseScreenshotTest`): the real one installs
 * the Firebase App Check provider in `onCreate()`, which throws under Robolectric because no
 * default `FirebaseApp` is initialized there.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class NotePreviewViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val noteRepository: NoteRepository = mockk()
    private val errorHandler: ErrorHandler = mockk()
    private val noteErrorReporter by lazy { NoteErrorReporter(errorHandler) }
    private val noteShare: NoteShareDelegate = mockk(relaxed = true)

    private val noteId = "note1"

    /** Stands in for this note's row. The screen renders it, not the fetch that fills it. */
    private val noteMirror = MutableStateFlow<Note?>(null)

    private val testNote = Note(
        text = "Test note",
        noteId = noteId,
        username = "testUser",
        isFavorite = false
    )

    /**
     * Builds the ViewModel and subscribes to [NotePreviewViewModel.content], which is shared
     * `WhileSubscribed` and so sits at its initial value with nobody collecting it.
     */
    private fun TestScope.createViewModel(): NotePreviewViewModel {
        val viewModel = NotePreviewViewModel(
            savedStateHandle = SavedStateHandle(mapOf("noteId" to noteId)),
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

    @Before
    fun setUp() {
        every { errorHandler.logError(any()) } just runs
        every { noteRepository.getNoteStream(noteId) } returns noteMirror
        // getNote writes the note through on its way out, the way the real repository does.
        coEvery { noteRepository.getNote(noteId) } coAnswers {
            noteMirror.value = testNote
            Result.success(testNote)
        }
    }

    @After
    fun tearDown() = unmockkAll()

    // -------------------------------------------------------------------------
    // Init / load note
    // -------------------------------------------------------------------------

    @Test
    fun `init loads note and sets Success state`() = runTest {
        val viewModel = createViewModel()
        val state = viewModel.content.value
        assertTrue(state is NotePreviewContent.Success)
        assertEquals(testNote, (state as NotePreviewContent.Success).note)
    }

    @Test
    fun `init observes the row for its own note id`() = runTest {
        // Widened from setUp's keyed stub so a wrong id is answered and then caught by the
        // assertion. Captured rather than verified: `verify { getNoteStream(noteId) }` would call
        // the Flow-returning method and discard the result, which is a cold flow built and never
        // collected. Here the flow stays the stub's return value.
        val observedNoteId = slot<String>()
        every { noteRepository.getNoteStream(capture(observedNoteId)) } returns noteMirror

        createViewModel()

        // The old ViewModel collected every note's update event and filtered by id by hand. The
        // query is keyed, so an unrelated note's write cannot reach this screen at all.
        assertEquals(noteId, observedNoteId.captured)
    }

    @Test
    fun `an edit made on another screen reaches this one`() = runTest {
        val viewModel = createViewModel()

        noteMirror.value = testNote.copy(text = "Edited text")

        val state = viewModel.content.value
        assertTrue(state is NotePreviewContent.Success)
        assertEquals("Edited text", (state as NotePreviewContent.Success).note.text)
    }

    @Test
    fun `init failure sets Error state`() = runTest {
        val exception = RuntimeException("not found")
        coEvery { noteRepository.getNote(noteId) } returns Result.failure(exception)

        val viewModel = createViewModel()

        val state = viewModel.content.value
        assertTrue(state is NotePreviewContent.Error)
        assertEquals(
            ErrorMessage.Unlocalized("not found"),
            (state as NotePreviewContent.Error).message
        )
    }

    @Test
    fun `a cached row renders even when the fetch fails`() = runTest {
        noteMirror.value = testNote
        coEvery { noteRepository.getNote(noteId) } returns Result.failure(RuntimeException())

        val viewModel = createViewModel()

        assertEquals(NotePreviewContent.Success(testNote), viewModel.content.value)
    }

    // -------------------------------------------------------------------------
    // deleteNote
    // -------------------------------------------------------------------------

    @Test
    fun `deleteNote deletes through the repository`() = runTest {
        val viewModel = createViewModel()
        coEvery { noteRepository.deleteNote(noteId) } returns Result.success(Unit)

        viewModel.deleteNote(noteId)

        // Was a NoteHandler emission; the row leaving the table is what other screens now see.
        coVerify { noteRepository.deleteNote(noteId) }
    }

    @Test
    fun `a deleted note shows Loading rather than an error`() = runTest {
        val viewModel = createViewModel()
        coEvery { noteRepository.deleteNote(noteId) } coAnswers {
            noteMirror.value = null
            Result.success(Unit)
        }

        viewModel.deleteNote(noteId)

        // The screen navigates back as it deletes, so a missing row must not flash a failure on
        // the way off the stack.
        assertEquals(NotePreviewContent.Loading, viewModel.content.value)
    }

    @Test
    fun `deleteNote failure emits action error`() = runTest {
        val viewModel = createViewModel()
        coEvery { noteRepository.deleteNote(any()) } returns Result.failure(RuntimeException())

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
        coEvery { noteRepository.toggleFavorite(testNote) } coAnswers {
            noteMirror.value = testNote.copy(isFavorite = true)
            Result.success(Unit)
        }

        viewModel.toggleFavorite(testNote)

        // The ViewModel patches nothing itself: the repository upserts the flipped row and the
        // screen re-reads it.
        val state = viewModel.content.value as NotePreviewContent.Success
        assertTrue(state.note.isFavorite) // false → true
    }

    @Test
    fun `toggleFavorite failure leaves the note as it was`() = runTest {
        val viewModel = createViewModel()
        coEvery { noteRepository.toggleFavorite(any()) } returns Result.failure(RuntimeException())

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
    fun `shareNote delegates to NoteShareDelegate`() = runTest {
        val viewModel = createViewModel()
        coEvery { noteShare.shareNote(testNote) } just runs

        viewModel.shareNote(testNote)

        coVerify { noteShare.shareNote(testNote) }
    }

    @Test
    fun `actionError surfaces errors reported through the shared NoteErrorReporter`() = runTest {
        // NoteShareDelegate reports share failures through the same @ViewModelScoped
        // NoteErrorReporter this ViewModel exposes as its own actionError -- the scope is what
        // makes those the same instance, and so the same flow.
        val viewModel = createViewModel()

        viewModel.actionError.test {
            noteErrorReporter.reportShareFailure()
            assertEquals(NoteActionError.ShareFailed, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}