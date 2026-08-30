package com.jiahan.smartcamera.home

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.core.ui.R as UiR
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.fake.FakeErrorHandler
import com.jiahan.smartcamera.fake.FakeMediaFileRepository
import com.jiahan.smartcamera.fake.FakeNoteRepository
import com.jiahan.smartcamera.fake.FakeRemoteConfigRepository
import com.jiahan.smartcamera.fake.FakeResourceProvider
import com.jiahan.smartcamera.note.NoteActionsDelegate
import com.jiahan.smartcamera.note.NoteErrorReporter
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
        val noteErrorReporter = NoteErrorReporter(errorHandler)
        val noteActions = NoteActionsDelegate(noteRepository, noteHandler, noteErrorReporter)
        val viewModel = HomeViewModel(
            noteRepository = noteRepository,
            noteHandler = noteHandler,
            noteActions = noteActions,
            noteShare = NoteShareDelegate(
                FakeMediaFileRepository(),
                noteErrorReporter,
                FakeResourceProvider(composeTestRule.activity)
            ),
            errorHandler = errorHandler,
            remoteConfigRepository = FakeRemoteConfigRepository(),
        )
        composeTestRule.setContent {
            SmartCameraTheme {
                HomeScreen(
                    onNavigateToNotePreview = { navigatedToNotePreview = it },
                    onNavigateToEditNote = {},
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
        noteRepository.setNotes(emptyList())
        launchHomeScreen()

        waitForText(string(R.string.create_first_note))
        composeTestRule.onNodeWithText(string(R.string.create_first_note)).assertIsDisplayed()
    }

    @Test
    fun successState_rendersNoteContent() {
        noteRepository.setNotes(listOf(note("doc1", "Hello world note")))
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
        noteRepository.setNotes(listOf(note("doc1", "Deletable note")))
        launchHomeScreen()
        waitForText("Deletable note")

        composeTestRule.onNodeWithContentDescription(string(UiR.string.cd_more_options))
            .performClick()
        waitForText(string(UiR.string.delete))
    }

    @Test
    fun overflowMenu_deleteConfirmed_removesNoteFromList() {
        noteRepository.setNotes(listOf(note("doc1", "Deletable note")))
        launchHomeScreen()
        waitForText("Deletable note")

        composeTestRule.onNodeWithContentDescription(string(UiR.string.cd_more_options))
            .performClick()
        waitForText(string(UiR.string.delete))
        composeTestRule.onNodeWithText(string(UiR.string.delete)).performClick()

        waitForText(string(UiR.string.delete_note))
        composeTestRule.onNodeWithText(string(UiR.string.delete)).performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Deletable note").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onNodeWithText(string(R.string.create_first_note)).assertIsDisplayed()
    }

    @Test
    fun overflowMenu_favoriteToggle_marksNoteAsFavorited() {
        noteRepository.setNotes(listOf(note("doc1", "Likeable note")))
        launchHomeScreen()
        waitForText("Likeable note")

        composeTestRule.onNodeWithContentDescription(string(UiR.string.cd_more_options))
            .performClick()
        waitForText(string(UiR.string.like))
        composeTestRule.onNodeWithText(string(UiR.string.like)).performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithContentDescription(string(UiR.string.cd_marked_as_favorite))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription(string(UiR.string.cd_marked_as_favorite))
            .assertIsDisplayed()
    }

    @Test
    fun tappingNote_navigatesToNotePreview() {
        noteRepository.setNotes(listOf(note("doc-nav", "Tap me")))
        launchHomeScreen()
        waitForText("Tap me")

        composeTestRule.onNodeWithText("Tap me").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) { navigatedToNotePreview == "doc-nav" }
        assertEquals("doc-nav", navigatedToNotePreview)
    }
}