package com.jiahan.smartcamera.note

import com.jiahan.smartcamera.domain.MediaDetail
import com.jiahan.smartcamera.domain.Note
import com.jiahan.smartcamera.fake.FakeAnalyticsRepository
import com.jiahan.smartcamera.fake.FakeErrorHandler
import com.jiahan.smartcamera.fake.FakeNoteRepository
import com.jiahan.smartcamera.util.AppConstants.MAX_NOTE_TEXT_LENGTH
import com.jiahan.smartcamera.util.ErrorMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [EditNoteViewModel]'s suite, in `commonTest` beside its subject, on :core:domain-testing's fakes
 * with [Dispatchers.setMain] called directly -- `ExploreViewModelTest` records why each of those.
 *
 * It used to run under Robolectric, and not for anything it asserted: the ViewModel decoded its
 * route with `toRoute`, whose `RouteDecoder` builds a real `android.os.Bundle`. The decode is
 * `HiltEditNoteViewModel`'s now, so the ViewModel takes a `noteId` and this suite passes one.
 * What the decode used to prove here, `SmartPhotosNavigationTest` proves on a device.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditNoteViewModelTest {

    private val noteRepository = FakeNoteRepository()
    private val analyticsRepository = FakeAnalyticsRepository()
    private val errorHandler = FakeErrorHandler()

    private val noteId = "note1"

    private val testNote = Note(
        noteId = noteId,
        text = "Original text",
        mediaList = listOf(MediaDetail(photoUrl = "http://photo")),
        username = "user1",
        isFavorite = true
    )

    private fun createViewModel() = EditNoteViewModel(
        noteId = noteId,
        noteRepository = noteRepository,
        analyticsRepository = analyticsRepository,
        errorHandler = errorHandler
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

    // -------------------------------------------------------------------------
    // Loading the note
    // -------------------------------------------------------------------------

    @Test
    fun `init asks the repository for the note it was given`() = runTest {
        createViewModel()

        assertEquals(listOf(noteId), noteRepository.requestedNoteIds)
    }

    @Test
    fun `init loads the note and prefills its text`() = runTest {
        val vm = createViewModel()

        assertEquals(EditNoteContent.Success(testNote), vm.uiState.value.content)
        assertEquals("Original text", vm.uiState.value.noteText)
    }

    @Test
    fun `init failure sets Error content`() = runTest {
        val exception = RuntimeException("note gone")
        noteRepository.getNoteResult = Result.failure(exception)

        val vm = createViewModel()

        assertEquals(
            EditNoteContent.Error(ErrorMessage.Unlocalized("note gone")),
            vm.uiState.value.content
        )
        assertEquals(listOf<Throwable>(exception), errorHandler.loggedErrors)
    }

    // -------------------------------------------------------------------------
    // Text validation
    // -------------------------------------------------------------------------

    @Test
    fun `updateNoteText sets an error above the max length`() = runTest {
        val vm = createViewModel()

        vm.updateNoteText("a".repeat(MAX_NOTE_TEXT_LENGTH + 1))

        assertTrue(vm.uiState.value.isNoteTextTooLong)
        assertFalse(vm.saveButtonEnabled.value)
    }

    @Test
    fun `updateNoteText clears the error back within the max length`() = runTest {
        val vm = createViewModel()
        vm.updateNoteText("a".repeat(MAX_NOTE_TEXT_LENGTH + 1))

        vm.updateNoteText("Back within the limit")

        assertFalse(vm.uiState.value.isNoteTextTooLong)
        assertTrue(vm.saveButtonEnabled.value)
    }

    // -------------------------------------------------------------------------
    // saveButtonEnabled
    // -------------------------------------------------------------------------

    @Test
    fun `saveButtonEnabled stays false until the note loads`() = runTest {
        noteRepository.getNoteAnswer = { awaitCancellation() }
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

    // No comma in the name: Kotlin/Native rejects one in a test name, and only the iOS test task
    // would find out.
    @Test
    fun `saveButtonEnabled turns true once the text actually changes and false again on undo`() =
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
            noteRepository.getNoteResult = Result.success(testNote.copy(text = null))
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
        noteRepository.getNoteResult = Result.success(testNote.copy(mediaList = null))
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

        vm.updateNoteText("a".repeat(MAX_NOTE_TEXT_LENGTH + 1))

        // Unsavable, but still an edit the user would lose -- hence not tied to saveButtonEnabled.
        assertFalse(vm.saveButtonEnabled.value)
        assertTrue(vm.hasUnsavedChanges.value)
    }

    @Test
    fun `showDiscardDialog and dismissDiscardDialog toggle the dialog flag`() = runTest {
        val vm = createViewModel()
        assertFalse(vm.uiState.value.isDiscardDialogVisible)

        vm.showDiscardDialog()
        assertTrue(vm.uiState.value.isDiscardDialogVisible)

        vm.dismissDiscardDialog()
        assertFalse(vm.uiState.value.isDiscardDialogVisible)
    }

    // -------------------------------------------------------------------------
    // saveNote
    // -------------------------------------------------------------------------

    @Test
    fun `saveNote sends the edited note through the repository`() = runTest {
        val vm = createViewModel()
        vm.updateNoteText("  Updated text  ")

        vm.saveNote()

        // This used to assert a noteUpdatedEvent. updateNote writes the edit through to the
        // `notes` table, so the note handed to the repository *is* what other screens will read --
        // which makes these the same assertions, one layer down.
        val saved = assertNotNull(noteRepository.lastUpdatedNote)
        assertEquals(noteId, saved.noteId)
        assertEquals("Updated text", saved.text) // trimmed
        // Untouched by an edit -- only the text is editable.
        assertEquals(testNote.mediaList, saved.mediaList)
        assertTrue(saved.isFavorite) // preserved from the loaded note, not reset
        assertEquals(testNote.username, saved.username)
        assertEquals(SaveStatus.Success, vm.uiState.value.saveStatus)
        assertEquals(1, noteRepository.updateCallCount)
    }

    @Test
    fun `saveNote sends null text when the field is blank`() = runTest {
        val vm = createViewModel()
        vm.updateNoteText("   ")

        vm.saveNote()

        val saved = assertNotNull(noteRepository.lastUpdatedNote)
        assertNull(saved.text)
    }

    @Test
    fun `saveNote failure sets an Error status`() = runTest {
        val vm = createViewModel()
        vm.updateNoteText("Updated text")
        noteRepository.updateResult = Result.failure(RuntimeException("save fail"))

        vm.saveNote()

        assertEquals(
            SaveStatus.Error(ErrorMessage.Unlocalized("save fail")),
            vm.uiState.value.saveStatus
        )
    }

    @Test
    fun `saveNote does nothing before the note loads`() = runTest {
        noteRepository.getNoteAnswer = { awaitCancellation() }
        val vm = createViewModel()

        vm.saveNote()

        assertEquals(SaveStatus.Idle, vm.uiState.value.saveStatus)
        assertEquals(0, noteRepository.updateCallCount)
    }

    @Test
    fun `resetSaveStatus resets to Idle`() = runTest {
        val vm = createViewModel()
        vm.saveNote()

        vm.resetSaveStatus()

        assertEquals(SaveStatus.Idle, vm.uiState.value.saveStatus)
    }
}