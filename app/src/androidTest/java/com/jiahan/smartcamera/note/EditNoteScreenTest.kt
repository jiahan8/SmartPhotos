package com.jiahan.smartcamera.note

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.core.ui.R as UiR
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.fake.FakeAnalyticsRepository
import com.jiahan.smartcamera.fake.FakeErrorHandler
import com.jiahan.smartcamera.fake.FakeNoteRepository
import com.jiahan.smartcamera.fake.FakeResourceProvider
import com.jiahan.smartcamera.ui.theme.SmartCameraTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for [EditNoteScreen].
 *
 * A real [EditNoteViewModel] is built from in-memory fakes and a manually-constructed
 * [SavedStateHandle] (standing in for the `EditNoteRoute` nav route), so the screen renders
 * end-to-end with no Firebase, no network, and no real navigation graph. Media picking/upload is
 * out of scope here by design — per [EditNoteScreen]'s own doc comment, media is fixed at note
 * creation time and shown read-only on this screen (that's [NoteScreen]'s job).
 *
 * Tests that need a deterministic edited value clear the field first, then type — appending to
 * existing text would depend on where Compose places the cursor after initial composition, which
 * isn't worth relying on.
 *
 * Save-failure is asserted indirectly (screen stays put, edit preserved, Save re-enabled) rather
 * than via the resulting snackbar text: no other screen test in this codebase renders a
 * [androidx.compose.material3.SnackbarHost] to make snackbar content queryable, and this doesn't
 * either.
 */
class EditNoteScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val noteRepository = FakeNoteRepository()
    private val noteHandler = NoteHandler()

    private var navigatedBack = false
    private var navigatedToPhotoPreviewUrl: String? = null
    private var navigatedToVideoPreviewUrl: String? = null

    private fun note(noteId: String = "note1", text: String? = "Original text") = HomeNote(
        noteId = noteId,
        text = text,
        username = "tester",
    )

    private fun launchEditNoteScreen(noteId: String = "note1") {
        val errorHandler = FakeErrorHandler()
        val viewModel = EditNoteViewModel(
            savedStateHandle = SavedStateHandle(mapOf("noteId" to noteId)),
            noteRepository = noteRepository,
            analyticsRepository = FakeAnalyticsRepository(),
            noteHandler = noteHandler,
            resourceProvider = FakeResourceProvider(composeTestRule.activity),
            errorHandler = errorHandler,
        )
        composeTestRule.setContent {
            SmartCameraTheme {
                EditNoteScreen(
                    onBack = { navigatedBack = true },
                    onNavigateToPhotoPreview = { navigatedToPhotoPreviewUrl = it },
                    onNavigateToVideoPreview = { navigatedToVideoPreviewUrl = it },
                    viewModel = viewModel,
                    snackbarHostState = remember { SnackbarHostState() },
                )
            }
        }
    }

    private fun string(resId: Int) = composeTestRule.activity.getString(resId)

    private fun waitForText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Clears the text field and types [text], avoiding any assumption about cursor placement. */
    private fun replaceText(text: String) {
        composeTestRule.onNode(hasSetTextAction()).performTextClearance()
        composeTestRule.onNode(hasSetTextAction()).performTextInput(text)
    }

    @Test
    fun success_rendersNoteContent() {
        noteRepository.getNoteResult = Result.success(note(text = "Original text"))
        launchEditNoteScreen()

        waitForText("Original text")
        composeTestRule.onNodeWithText("Original text").assertIsDisplayed()
        composeTestRule.onNodeWithText("tester").assertIsDisplayed()
    }

    @Test
    fun loadFailure_showsErrorMessage() {
        // FakeNoteRepository.getNote() fails by default when getNoteResult is left null.
        launchEditNoteScreen(noteId = "missing-note")

        waitForText("No note for missing-note")
        composeTestRule.onNodeWithText("No note for missing-note").assertIsDisplayed()
    }

    @Test
    fun backWithoutChanges_leavesImmediately() {
        noteRepository.getNoteResult = Result.success(note())
        launchEditNoteScreen()
        waitForText("Original text")

        composeTestRule.onNodeWithContentDescription(string(R.string.cd_back)).performClick()

        assertTrue(navigatedBack)
        composeTestRule.onNodeWithText(string(R.string.discard_changes)).assertDoesNotExist()
    }

    @Test
    fun typingText_enablesSaveButton() {
        noteRepository.getNoteResult = Result.success(note())
        launchEditNoteScreen()
        waitForText("Original text")
        composeTestRule.onNodeWithText(string(R.string.save)).assertIsNotEnabled()

        replaceText("New text")

        composeTestRule.onNodeWithText(string(R.string.save)).assertIsEnabled()
    }

    @Test
    fun textExceedingMaxLength_showsValidationError_andDisablesSave() {
        noteRepository.getNoteResult = Result.success(note())
        launchEditNoteScreen()
        waitForText("Original text")

        replaceText("a".repeat(501))

        waitForText(string(R.string.note_validation))
        composeTestRule.onNodeWithText(string(R.string.note_validation)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.save)).assertIsNotEnabled()
    }

    @Test
    fun clearButton_clearsText_andDisablesSaveForMediaLessNote() {
        noteRepository.getNoteResult = Result.success(note())
        launchEditNoteScreen()
        waitForText("Original text")

        composeTestRule.onNodeWithContentDescription(string(UiR.string.cd_clear_field))
            .performClick()

        composeTestRule.onAllNodesWithText("Original text").assertCountEquals(0)
        // Clearing the only text of a note with no media would leave nothing behind, so saving a
        // blank result is disallowed (see EditNoteViewModel.saveButtonEnabled).
        composeTestRule.onNodeWithText(string(R.string.save)).assertIsNotEnabled()
    }

    @Test
    fun saveSuccess_updatesNoteAndNavigatesBack() {
        noteRepository.getNoteResult = Result.success(note(noteId = "note-to-save"))
        noteRepository.updateResult = Result.success(Unit)
        launchEditNoteScreen(noteId = "note-to-save")
        waitForText("Original text")

        replaceText("New text")
        composeTestRule.onNodeWithText(string(R.string.save)).performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) { navigatedBack }
        assertEquals(1, noteRepository.updateCallCount)
        assertEquals("New text", noteRepository.lastUpdatedNote?.text)
    }

    @Test
    fun saveFailure_staysOnScreenWithEditPreserved() {
        noteRepository.getNoteResult = Result.success(note())
        noteRepository.updateResult = Result.failure(RuntimeException("save failed"))
        launchEditNoteScreen()
        waitForText("Original text")

        replaceText("New text")
        composeTestRule.onNodeWithText(string(R.string.save)).performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) { noteRepository.updateCallCount == 1 }
        composeTestRule.waitForIdle()
        assertFalse(navigatedBack)
        composeTestRule.onNodeWithText(string(R.string.save)).assertIsEnabled()
        composeTestRule.onNodeWithText("New text").assertIsDisplayed()
    }

    @Test
    fun discardDialog_confirmDiscard_navigatesBack() {
        noteRepository.getNoteResult = Result.success(note())
        launchEditNoteScreen()
        waitForText("Original text")
        replaceText("New text")

        composeTestRule.onNodeWithContentDescription(string(R.string.cd_back)).performClick()

        waitForText(string(R.string.discard_changes))
        composeTestRule.onNodeWithText(string(R.string.discard)).performClick()

        assertTrue(navigatedBack)
    }

    @Test
    fun discardDialog_cancel_keepsEditingWithChangesPreserved() {
        noteRepository.getNoteResult = Result.success(note())
        launchEditNoteScreen()
        waitForText("Original text")
        replaceText("New text")

        composeTestRule.onNodeWithContentDescription(string(R.string.cd_back)).performClick()
        waitForText(string(R.string.discard_changes))
        // The screen's own inline Cancel button is still in the tree behind the dialog, so scope
        // the match to the dialog's dismiss button (same pattern as SettingsScreenTest).
        composeTestRule.onNode(
            hasText(string(UiR.string.cancel)) and hasClickAction() and hasAnyAncestor(isDialog())
        ).performClick()

        assertFalse(navigatedBack)
        composeTestRule.onNodeWithText("New text").assertIsDisplayed()
    }
}