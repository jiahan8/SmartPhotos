package com.jiahan.smartcamera.note

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jiahan.smartcamera.MainDispatcherRule
import com.jiahan.smartcamera.data.repository.AnalyticsRepository
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.domain.MediaDetail
import com.jiahan.smartcamera.util.AppConstants.MAX_POST_TEXT_LENGTH
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.ResourceProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.awaitCancellation
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
 * [EditNoteViewModel] parses its typed nav route via [androidx.navigation.toRoute], whose internal
 * [androidx.navigation.serialization.RouteDecoder] constructs a real [android.os.Bundle] — that
 * needs Robolectric's shadow to work outside a real Android runtime, hence Robolectric here.
 *
 * A plain [Application] stands in for `MyApp` (as in `BaseScreenshotTest`): the real one installs
 * the Firebase App Check provider in `onCreate()`, which throws under Robolectric because no
 * default `FirebaseApp` is initialized there.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class EditNoteViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val noteRepository: NoteRepository = mockk()
    private val analyticsRepository: AnalyticsRepository = mockk()
    private val resourceProvider: ResourceProvider = mockk()
    private val errorHandler: ErrorHandler = mockk()

    private val noteId = "note1"

    private val testNote = HomeNote(
        noteId = noteId,
        text = "Original text",
        mediaList = listOf(MediaDetail(photoUrl = "http://photo")),
        username = "user1",
        favorite = true
    )

    private fun createViewModel() = EditNoteViewModel(
        savedStateHandle = SavedStateHandle(mapOf("noteId" to noteId)),
        noteRepository = noteRepository,
        analyticsRepository = analyticsRepository,
        resourceProvider = resourceProvider,
        errorHandler = errorHandler
    )

    @Before
    fun setUp() {
        every { errorHandler.logError(any()) } just runs
        every { errorHandler.getErrorMessage(any()) } returns "Error"
        every { resourceProvider.getString(any()) } returns "Text too long"
        every { analyticsRepository.logEditNoteCustomEvent(any()) } just runs
        coEvery { noteRepository.getNote(noteId) } returns Result.success(testNote)
    }

    @After
    fun tearDown() = unmockkAll()

    // -------------------------------------------------------------------------
    // Loading the note
    // -------------------------------------------------------------------------

    @Test
    fun `init loads the note and prefills its text`() = runTest {
        val vm = createViewModel()

        val content = vm.uiState.value.content
        assertTrue(content is EditNoteContent.Success)
        assertEquals(testNote, (content as EditNoteContent.Success).note)
        assertEquals("Original text", vm.uiState.value.noteText)
    }

    @Test
    fun `init failure sets Error content`() = runTest {
        val exception = RuntimeException("note gone")
        coEvery { noteRepository.getNote(noteId) } returns Result.failure(exception)
        every { errorHandler.getErrorMessage(exception) } returns "note gone"

        val vm = createViewModel()

        val content = vm.uiState.value.content
        assertTrue(content is EditNoteContent.Error)
        assertEquals("note gone", (content as EditNoteContent.Error).message)
        verify { errorHandler.logError(exception) }
    }

    // -------------------------------------------------------------------------
    // Text validation
    // -------------------------------------------------------------------------

    @Test
    fun `updateNoteText sets an error above the max length`() = runTest {
        val vm = createViewModel()

        vm.updateNoteText("a".repeat(MAX_POST_TEXT_LENGTH + 1))

        assertEquals("Text too long", vm.uiState.value.noteTextError)
        assertFalse(vm.saveButtonEnabled.value)
    }

    @Test
    fun `updateNoteText clears the error back within the max length`() = runTest {
        val vm = createViewModel()
        vm.updateNoteText("a".repeat(MAX_POST_TEXT_LENGTH + 1))

        vm.updateNoteText("Back within the limit")

        assertNull(vm.uiState.value.noteTextError)
        assertTrue(vm.saveButtonEnabled.value)
    }

    // -------------------------------------------------------------------------
    // saveButtonEnabled
    // -------------------------------------------------------------------------

    @Test
    fun `saveButtonEnabled stays false until the note loads`() = runTest {
        coEvery { noteRepository.getNote(noteId) } coAnswers { awaitCancellation() }
        val vm = createViewModel()

        vm.updateNoteText("Some text")

        assertFalse(vm.saveButtonEnabled.value)
    }

    @Test
    fun `saveButtonEnabled is false while the text still matches the loaded note`() = runTest {
        val vm = createViewModel()

        assertFalse(vm.saveButtonEnabled.value)
    }

    @Test
    fun `saveButtonEnabled is false when only surrounding whitespace was added`() = runTest {
        val vm = createViewModel()

        // updateNote would persist the trimmed text, so this is not a change.
        vm.updateNoteText("  Original text  ")

        assertFalse(vm.saveButtonEnabled.value)
    }

    @Test
    fun `saveButtonEnabled turns true once the text actually changes, and false again on undo`() =
        runTest {
            val vm = createViewModel()

            vm.updateNoteText("Original text edited")
            assertTrue(vm.saveButtonEnabled.value)

            vm.updateNoteText("Original text")
            assertFalse(vm.saveButtonEnabled.value)
        }

    @Test
    fun `saveButtonEnabled is false for a blank text note whose text was already blank`() =
        runTest {
            coEvery { noteRepository.getNote(noteId) } returns
                    Result.success(testNote.copy(text = null))
            val vm = createViewModel()

            assertFalse(vm.saveButtonEnabled.value)
        }

    @Test
    fun `saveButtonEnabled stays true when clearing the text of a note that has media`() =
        runTest {
            val vm = createViewModel()

            vm.updateNoteText("")

            assertTrue(vm.saveButtonEnabled.value)
        }

    @Test
    fun `saveButtonEnabled is false for blank text when the note has no media`() = runTest {
        coEvery { noteRepository.getNote(noteId) } returns
                Result.success(testNote.copy(mediaList = null))
        val vm = createViewModel()

        vm.updateNoteText("")

        assertFalse(vm.saveButtonEnabled.value)
    }

    // -------------------------------------------------------------------------
    // hasUnsavedChanges / discard dialog
    // -------------------------------------------------------------------------

    @Test
    fun `hasUnsavedChanges is false for an untouched or whitespace-only edit`() = runTest {
        val vm = createViewModel()
        assertFalse(vm.hasUnsavedChanges.value)

        vm.updateNoteText("  Original text  ")

        assertFalse(vm.hasUnsavedChanges.value)
    }

    @Test
    fun `hasUnsavedChanges is true once the text changes`() = runTest {
        val vm = createViewModel()

        vm.updateNoteText("Original text edited")

        assertTrue(vm.hasUnsavedChanges.value)
    }

    @Test
    fun `hasUnsavedChanges is true for an edit too long to save`() = runTest {
        val vm = createViewModel()

        vm.updateNoteText("a".repeat(MAX_POST_TEXT_LENGTH + 1))

        // Unsavable, but still an edit the user would lose -- hence not tied to saveButtonEnabled.
        assertFalse(vm.saveButtonEnabled.value)
        assertTrue(vm.hasUnsavedChanges.value)
    }

    @Test
    fun `setShowDiscardDialog toggles the dialog flag`() = runTest {
        val vm = createViewModel()
        assertFalse(vm.uiState.value.showDiscardDialog)

        vm.setShowDiscardDialog(true)
        assertTrue(vm.uiState.value.showDiscardDialog)

        vm.setShowDiscardDialog(false)
        assertFalse(vm.uiState.value.showDiscardDialog)
    }

    // -------------------------------------------------------------------------
    // saveNote
    // -------------------------------------------------------------------------

    @Test
    fun `saveNote sends the edited note through the repository`() = runTest {
        val vm = createViewModel()
        vm.updateNoteText("  Updated text  ")
        val saved = slot<HomeNote>()
        coEvery { noteRepository.updateNote(capture(saved)) } returns Result.success(Unit)

        vm.saveNote()

        // This used to assert a noteUpdatedEvent. updateNote writes the edit through to the
        // `notes` table, so the note handed to the repository *is* what other screens will read --
        // which makes these the same assertions, one layer down.
        assertEquals(noteId, saved.captured.noteId)
        assertEquals("Updated text", saved.captured.text) // trimmed
        // Untouched by an edit -- only the text is editable.
        assertEquals(testNote.mediaList, saved.captured.mediaList)
        assertTrue(saved.captured.favorite) // preserved from the loaded note, not reset
        assertEquals(testNote.username, saved.captured.username)
        assertTrue(vm.uiState.value.saveStatus is SaveStatus.Success)
        coVerify(exactly = 1) { noteRepository.updateNote(any()) }
    }

    @Test
    fun `saveNote sends null text when the field is blank`() = runTest {
        val vm = createViewModel()
        vm.updateNoteText("   ")
        coEvery { noteRepository.updateNote(any()) } returns Result.success(Unit)

        vm.saveNote()

        coVerify { noteRepository.updateNote(match { it.text == null }) }
    }

    @Test
    fun `saveNote failure sets an Error status`() = runTest {
        val vm = createViewModel()
        coEvery { noteRepository.updateNote(any()) } returns
                Result.failure(RuntimeException("save fail"))
        every { errorHandler.getErrorMessage(any()) } returns "save fail"

        vm.saveNote()

        val status = vm.uiState.value.saveStatus
        assertTrue(status is SaveStatus.Error)
        assertEquals("save fail", (status as SaveStatus.Error).message)
    }

    @Test
    fun `saveNote does nothing before the note loads`() = runTest {
        coEvery { noteRepository.getNote(noteId) } coAnswers { awaitCancellation() }
        val vm = createViewModel()

        vm.saveNote()

        assertTrue(vm.uiState.value.saveStatus is SaveStatus.Idle)
        coVerify(exactly = 0) { noteRepository.updateNote(any()) }
    }

    @Test
    fun `resetSaveStatus resets to Idle`() = runTest {
        val vm = createViewModel()
        coEvery { noteRepository.updateNote(any()) } returns Result.success(Unit)
        vm.saveNote()

        vm.resetSaveStatus()

        assertTrue(vm.uiState.value.saveStatus is SaveStatus.Idle)
    }
}