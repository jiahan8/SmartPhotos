package com.jiahan.smartcamera.home

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.fake.FakeErrorHandler
import com.jiahan.smartcamera.fake.FakeMediaFileRepository
import com.jiahan.smartcamera.fake.FakeNoteRepository
import com.jiahan.smartcamera.fake.FakeResourceProvider
import com.jiahan.smartcamera.note.NoteActionsDelegate
import com.jiahan.smartcamera.note.NoteHandler
import com.jiahan.smartcamera.note.NoteShareDelegate
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

    private fun note(noteId: String, text: String) = HomeNote(
        noteId = noteId,
        text = text,
        username = "tester",
        favorite = false,
    )

    private fun launchHomeScreen() {
        val errorHandler = FakeErrorHandler()
        val noteActions = NoteActionsDelegate(noteRepository, noteHandler, errorHandler)
        val viewModel = HomeViewModel(
            noteRepository = noteRepository,
            noteHandler = noteHandler,
            noteActions = noteActions,
            noteShare = NoteShareDelegate(
                FakeMediaFileRepository(),
                noteActions,
                FakeResourceProvider(composeTestRule.activity)
            ),
            errorHandler = errorHandler,
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
    fun overflowMenu_exposesDeleteAction() {
        noteRepository.notesResult = Result.success(listOf(note("doc1", "Deletable note")))
        launchHomeScreen()
        waitForText("Deletable note")

        composeTestRule.onNodeWithContentDescription(string(R.string.cd_more_options))
            .performClick()
        waitForText(string(R.string.delete))
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