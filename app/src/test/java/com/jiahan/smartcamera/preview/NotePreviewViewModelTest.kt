package com.jiahan.smartcamera.preview

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.jiahan.smartcamera.MainDispatcherRule
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.note.NoteActionsDelegate
import com.jiahan.smartcamera.note.NoteHandler
import com.jiahan.smartcamera.note.NoteShareDelegate
import com.jiahan.smartcamera.util.ErrorHandler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class NotePreviewViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val noteRepository: NoteRepository = mockk()
    private val noteHandler = NoteHandler()
    private val noteActions: NoteActionsDelegate = mockk()
    private val errorHandler: ErrorHandler = mockk()
    private val noteShare: NoteShareDelegate = mockk(relaxed = true)

    private val noteId = "note1"

    private val testNote = HomeNote(
        text = "Test note",
        noteId = noteId,
        username = "testUser",
        favorite = false
    )

    private fun createViewModel() = NotePreviewViewModel(
        savedStateHandle = SavedStateHandle(mapOf("id" to noteId)),
        noteRepository = noteRepository,
        noteHandler = noteHandler,
        noteActions = noteActions,
        errorHandler = errorHandler,
        noteShare = noteShare
    )

    @Before
    fun setUp() {
        every { errorHandler.logError(any()) } just runs
        every { errorHandler.getErrorMessage(any()) } returns "Error"
        every { noteActions.actionError } returns MutableSharedFlow<String>().asSharedFlow()
        coEvery { noteRepository.getNote(noteId) } returns Result.success(testNote)
    }

    @After
    fun tearDown() = unmockkAll()

    // -------------------------------------------------------------------------
    // Init / load note
    // -------------------------------------------------------------------------

    @Test
    fun `init loads note and sets Success state`() = runTest {
        val vm = createViewModel()
        val state = vm.uiState.value.content
        assertTrue(state is NotePreviewContent.Success)
        assertEquals(testNote, (state as NotePreviewContent.Success).note)
    }

    @Test
    fun `noteUpdatedEvent for this note refreshes Success state`() = runTest {
        val vm = createViewModel()
        val editedNote = testNote.copy(text = "Edited text")

        noteHandler.notifyNoteUpdated(editedNote)

        val state = vm.uiState.value.content
        assertTrue(state is NotePreviewContent.Success)
        assertEquals(editedNote, (state as NotePreviewContent.Success).note)
    }

    @Test
    fun `noteUpdatedEvent for a different note is ignored`() = runTest {
        val vm = createViewModel()
        val otherNote = HomeNote(noteId = "other-note", text = "Not this one", username = "someone")

        noteHandler.notifyNoteUpdated(otherNote)

        val state = vm.uiState.value.content
        assertTrue(state is NotePreviewContent.Success)
        assertEquals(testNote, (state as NotePreviewContent.Success).note)
    }

    @Test
    fun `init failure sets Error state`() = runTest {
        val exception = RuntimeException("not found")
        coEvery { noteRepository.getNote(noteId) } returns Result.failure(exception)
        every { errorHandler.getErrorMessage(exception) } returns "not found"

        val vm = createViewModel()

        val state = vm.uiState.value.content
        assertTrue(state is NotePreviewContent.Error)
        assertEquals("not found", (state as NotePreviewContent.Error).message)
    }

    // -------------------------------------------------------------------------
    // deleteNote
    // -------------------------------------------------------------------------

    @Test
    fun `deleteNote success notifies NoteHandler`() = runTest {
        val vm = createViewModel()
        coEvery { noteRepository.deleteNote(noteId) } returns Result.success(Unit)

        noteHandler.noteDeletedEvent.test {
            vm.deleteNote(noteId)
            assertEquals(noteId, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteNote failure emits action error`() = runTest {
        val vm = createViewModel()
        coEvery { noteRepository.deleteNote(any()) } returns Result.failure(RuntimeException())
        every { errorHandler.getErrorMessage(any()) } returns "delete failed"

        vm.actionError.test {
            vm.deleteNote(noteId)
            assertEquals("delete failed", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // favoriteNote
    // -------------------------------------------------------------------------

    @Test
    fun `favoriteNote success toggles favorite in Success state`() = runTest {
        val vm = createViewModel()
        coEvery { noteRepository.favoriteNote(testNote) } returns Result.success(Unit)

        vm.favoriteNote(testNote)

        val state = vm.uiState.value.content as NotePreviewContent.Success
        assertTrue(state.note.favorite) // false → true
    }

    @Test
    fun `favoriteNote success notifies NoteHandler`() = runTest {
        val vm = createViewModel()
        coEvery { noteRepository.favoriteNote(testNote) } returns Result.success(Unit)

        noteHandler.noteFavoritedEvent.test {
            vm.favoriteNote(testNote)
            val emitted = awaitItem()
            assertEquals(noteId, emitted.noteId)
            assertTrue(emitted.favorite) // toggled from false to true
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `favoriteNote failure emits action error`() = runTest {
        val vm = createViewModel()
        coEvery { noteRepository.favoriteNote(any()) } returns Result.failure(RuntimeException())
        every { errorHandler.getErrorMessage(any()) } returns "fav failed"

        vm.actionError.test {
            vm.favoriteNote(testNote)
            assertEquals("fav failed", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // setNoteToDelete
    // -------------------------------------------------------------------------

    @Test
    fun `setNoteToDelete stores the note`() = runTest {
        val vm = createViewModel()
        vm.setNoteToDelete(testNote)
        assertEquals(testNote, vm.uiState.value.noteToDelete)
    }

    @Test
    fun `setNoteToDelete null clears the note`() = runTest {
        val vm = createViewModel()
        vm.setNoteToDelete(testNote)
        vm.setNoteToDelete(null)
        assertNull(vm.uiState.value.noteToDelete)
    }

    // -------------------------------------------------------------------------
    // shareNote
    // -------------------------------------------------------------------------

    @Test
    fun `shareNote delegates to NoteShareDelegate`() = runTest {
        val vm = createViewModel()
        coEvery { noteShare.shareNote(testNote) } just runs

        vm.shareNote(testNote)

        coVerify { noteShare.shareNote(testNote) }
    }

    @Test
    fun `actionError surfaces errors reported through the shared NoteActionsDelegate`() = runTest {
        // NoteShareDelegate reports share failures via the same @ViewModelScoped
        // NoteActionsDelegate instance injected here, not via this ViewModel's own _actionError.
        val sharedActionError = MutableSharedFlow<String>(extraBufferCapacity = 1)
        every { noteActions.actionError } returns sharedActionError.asSharedFlow()
        val vm = createViewModel()

        vm.actionError.test {
            sharedActionError.tryEmit("share failed")
            assertEquals("share failed", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}