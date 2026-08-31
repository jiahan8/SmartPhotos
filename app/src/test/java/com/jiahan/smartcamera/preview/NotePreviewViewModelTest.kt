package com.jiahan.smartcamera.preview

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.jiahan.smartcamera.MainDispatcherRule
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.note.NoteActionsDelegate
import com.jiahan.smartcamera.note.NoteErrorReporter
import com.jiahan.smartcamera.note.NoteShareDelegate
import com.jiahan.smartcamera.util.ErrorHandler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
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
 * internal [androidx.navigation.serialization.RouteDecoder] constructs a real [android.os.Bundle]
 * — that needs Robolectric's shadow to work outside a real Android runtime, hence Robolectric here.
 *
 * A plain [Application] stands in for [com.jiahan.smartcamera.MyApp] (as in
 * [com.jiahan.smartcamera.screenshot.BaseScreenshotTest]): the real one installs the Firebase App
 * Check provider in `onCreate()`, which throws under Robolectric because no default `FirebaseApp`
 * is initialized there.
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
    private val noteActions by lazy { NoteActionsDelegate(noteRepository, noteErrorReporter) }
    private val noteShare: NoteShareDelegate = mockk(relaxed = true)

    private val noteId = "note1"

    /** Stands in for this note's row. The screen renders it, not the fetch that fills it. */
    private val noteMirror = MutableStateFlow<HomeNote?>(null)

    private val testNote = HomeNote(
        text = "Test note",
        noteId = noteId,
        username = "testUser",
        favorite = false
    )

    /**
     * Builds the ViewModel and subscribes to [NotePreviewViewModel.content], which is shared
     * `WhileSubscribed` and so sits at its initial value with nobody collecting it.
     */
    private fun TestScope.createViewModel(): NotePreviewViewModel {
        val viewModel = NotePreviewViewModel(
            savedStateHandle = SavedStateHandle(mapOf("id" to noteId)),
            noteRepository = noteRepository,
            noteActions = noteActions,
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
        every { errorHandler.getErrorMessage(any()) } returns "Error"
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
        createViewModel()

        // The old ViewModel collected every note's update event and filtered by id by hand. The
        // query is keyed, so an unrelated note's write cannot reach this screen at all.
        verify { noteRepository.getNoteStream(noteId) }
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
        every { errorHandler.getErrorMessage(exception) } returns "not found"

        val viewModel = createViewModel()

        val state = viewModel.content.value
        assertTrue(state is NotePreviewContent.Error)
        assertEquals("not found", (state as NotePreviewContent.Error).message)
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
        every { errorHandler.getErrorMessage(any()) } returns "delete failed"

        viewModel.actionError.test {
            viewModel.deleteNote(noteId)
            assertEquals("delete failed", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // favoriteNote
    // -------------------------------------------------------------------------

    @Test
    fun `favoriteNote toggle reaches the screen through the mirror`() = runTest {
        val viewModel = createViewModel()
        coEvery { noteRepository.favoriteNote(testNote) } coAnswers {
            noteMirror.value = testNote.copy(favorite = true)
            Result.success(Unit)
        }

        viewModel.favoriteNote(testNote)

        // The ViewModel patches nothing itself: the repository upserts the flipped row and the
        // screen re-reads it.
        val state = viewModel.content.value as NotePreviewContent.Success
        assertTrue(state.note.favorite) // false → true
    }

    @Test
    fun `favoriteNote failure leaves the note as it was`() = runTest {
        val viewModel = createViewModel()
        coEvery { noteRepository.favoriteNote(any()) } returns Result.failure(RuntimeException())
        every { errorHandler.getErrorMessage(any()) } returns "fav failed"

        viewModel.actionError.test {
            viewModel.favoriteNote(testNote)
            assertEquals("fav failed", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse((viewModel.content.value as NotePreviewContent.Success).note.favorite)
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
        // NoteErrorReporter the actions delegate uses, which is the flow exposed here.
        val viewModel = createViewModel()

        viewModel.actionError.test {
            noteErrorReporter.reportError("share failed")
            assertEquals("share failed", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}