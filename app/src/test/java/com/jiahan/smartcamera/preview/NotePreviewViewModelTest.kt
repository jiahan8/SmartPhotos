package com.jiahan.smartcamera.preview

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.jiahan.smartcamera.MainDispatcherRule
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.navigation.Screen
import com.jiahan.smartcamera.note.NoteHandler
import com.jiahan.smartcamera.util.ErrorHandler
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class NotePreviewViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val noteRepository: NoteRepository = mockk()
    private val noteHandler = NoteHandler()
    private val errorHandler: ErrorHandler = mockk()

    private val documentPath = "notes/abc123"

    private val testNote = HomeNote(
        text = "Test note",
        documentPath = documentPath,
        username = "testUser",
        favorite = false
    )

    private fun createViewModel() = NotePreviewViewModel(
        savedStateHandle = SavedStateHandle(mapOf(Screen.NotePreview.ID_ARG to documentPath)),
        noteRepository = noteRepository,
        noteHandler = noteHandler,
        errorHandler = errorHandler
    )

    @Before
    fun setUp() {
        every { errorHandler.logError(any()) } just runs
        every { errorHandler.getErrorMessage(any()) } returns "Error"
        coEvery { noteRepository.getNote(documentPath) } returns Result.success(testNote)
    }

    @After
    fun tearDown() = unmockkAll()

    // -------------------------------------------------------------------------
    // Init / load note
    // -------------------------------------------------------------------------

    @Test
    fun `init loads note and sets Success state`() = runTest {
        val vm = createViewModel()
        val state = vm.uiState.value
        assertTrue(state is NotePreviewUiState.Success)
        assertEquals(testNote, (state as NotePreviewUiState.Success).note)
    }

    @Test
    fun `init failure sets Error state`() = runTest {
        val exception = RuntimeException("not found")
        coEvery { noteRepository.getNote(documentPath) } returns Result.failure(exception)
        every { errorHandler.getErrorMessage(exception) } returns "not found"

        val vm = createViewModel()

        val state = vm.uiState.value
        assertTrue(state is NotePreviewUiState.Error)
        assertEquals("not found", (state as NotePreviewUiState.Error).message)
    }

    // -------------------------------------------------------------------------
    // deleteNote
    // -------------------------------------------------------------------------

    @Test
    fun `deleteNote success notifies NoteHandler`() = runTest {
        val vm = createViewModel()
        coEvery { noteRepository.deleteNote(documentPath) } returns Result.success(Unit)

        noteHandler.noteDeletedEvent.test {
            vm.deleteNote(documentPath)
            assertEquals(documentPath, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteNote failure emits action error`() = runTest {
        val vm = createViewModel()
        coEvery { noteRepository.deleteNote(any()) } returns Result.failure(RuntimeException())
        every { errorHandler.getErrorMessage(any()) } returns "delete failed"

        vm.actionError.test {
            vm.deleteNote(documentPath)
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

        val state = vm.uiState.value as NotePreviewUiState.Success
        assertTrue(state.note.favorite) // false → true
    }

    @Test
    fun `favoriteNote success notifies NoteHandler`() = runTest {
        val vm = createViewModel()
        coEvery { noteRepository.favoriteNote(testNote) } returns Result.success(Unit)

        noteHandler.noteFavoritedEvent.test {
            vm.favoriteNote(testNote)
            val emitted = awaitItem()
            assertEquals(documentPath, emitted.documentPath)
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
        assertEquals(testNote, vm.noteToDelete.value)
    }

    @Test
    fun `setNoteToDelete null clears the note`() = runTest {
        val vm = createViewModel()
        vm.setNoteToDelete(testNote)
        vm.setNoteToDelete(null)
        assertNull(vm.noteToDelete.value)
    }
}