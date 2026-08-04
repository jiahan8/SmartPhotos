package com.jiahan.smartcamera.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.fake.FakeErrorHandler
import com.jiahan.smartcamera.fake.FakeNoteRepository
import com.jiahan.smartcamera.note.NoteHandler
import com.jiahan.smartcamera.ui.theme.SmartCameraTheme
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for [HomeScreen].
 *
 * A real [HomeViewModel] is built from in-memory fakes and injected, so the feed renders end-to-end
 * (paged load -> state -> recomposition) with no Firebase or network. Notes are created without media
 * or profile-picture URLs so Coil never performs I/O during the test.
 *
 * Lives in `sharedTest`: runs on the JVM (Robolectric) and on-device via the same source.
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val noteRepository = FakeNoteRepository()
    private val noteHandler = NoteHandler()
    private var navigatedToNotePreview: String? = null

    private fun note(documentPath: String, text: String) = HomeNote(
        text = text,
        documentPath = documentPath,
        username = "tester",
        favorite = false,
    )

    private fun launchHomeScreen() {
        val viewModel = HomeViewModel(
            noteRepository = noteRepository,
            noteHandler = noteHandler,
            errorHandler = FakeErrorHandler(),
        )
        composeTestRule.setContent {
            SmartCameraTheme {
                HomeScreen(
                    onNavigateToNotePreview = { navigatedToNotePreview = it },
                    onNavigateToPhotoPreview = {},
                    onNavigateToVideoPreview = {},
                    onNavigateToExplore = {},
                    viewModel = viewModel,
                    scrollToTop = null,
                    onScrollToTopConsumed = {},
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

    @Test
    fun emptyFeed_showsNoNotesFoundMessage() {
        noteRepository.notesResult = Result.success(emptyList())
        launchHomeScreen()

        waitForText(string(R.string.no_notes_found))
        composeTestRule.onNodeWithText(string(R.string.no_notes_found)).assertIsDisplayed()
    }

    @Test
    fun successState_rendersNoteContent() {
        noteRepository.notesResult = Result.success(listOf(note("doc1", "Hello world note")))
        launchHomeScreen()

        waitForText("Hello world note")
        composeTestRule.onNodeWithText("Hello world note").assertIsDisplayed()
        composeTestRule.onNodeWithText("tester").assertIsDisplayed()
    }

    @Test
    fun repositoryFailure_showsErrorMessage() {
        noteRepository.notesResult = Result.failure(RuntimeException("Something went wrong"))
        launchHomeScreen()

        waitForText("Something went wrong")
        composeTestRule.onNodeWithText("Something went wrong").assertIsDisplayed()
    }

    @Test
    fun longPressNote_showsDeleteDialog_andConfirmDeletesNote() {
        noteRepository.notesResult = Result.success(listOf(note("doc1", "Deletable note")))
        launchHomeScreen()
        waitForText("Deletable note")

        composeTestRule.onNodeWithText("Deletable note").performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.delete_note_desc)).assertIsDisplayed()

        composeTestRule.onNodeWithText(string(R.string.delete)).performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) { noteRepository.deleteCallCount == 1 }
        assertEquals("doc1", noteRepository.lastDeletedPath)
    }

    @Test
    fun tappingNote_navigatesToNotePreview() {
        noteRepository.notesResult = Result.success(listOf(note("doc-nav", "Tap me")))
        launchHomeScreen()
        waitForText("Tap me")

        composeTestRule.onNodeWithText("Tap me").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) { navigatedToNotePreview == "doc-nav" }
        assertEquals("doc-nav", navigatedToNotePreview)
    }
}