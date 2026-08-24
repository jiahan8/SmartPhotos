package com.jiahan.smartcamera.favorite

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
import com.jiahan.smartcamera.fake.FakeAnalyticsRepository
import com.jiahan.smartcamera.fake.FakeErrorHandler
import com.jiahan.smartcamera.fake.FakeNoteRepository
import com.jiahan.smartcamera.fake.FakeMediaFileRepository
import com.jiahan.smartcamera.fake.FakeResourceProvider
import com.jiahan.smartcamera.note.NoteActionsDelegate
import com.jiahan.smartcamera.note.NoteHandler
import com.jiahan.smartcamera.note.NoteShareDelegate
import com.jiahan.smartcamera.ui.theme.SmartCameraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for [FavoriteScreen].
 *
 * The favorites list is driven by the fake's reactive stream, so this verifies the debounced
 * `getFavoriteNotesStream` -> `stateIn` -> UI pipeline and the loading/empty/content branches with a
 * real [FavoriteViewModel] and no Firebase.
 */
class FavoriteScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val noteRepository = FakeNoteRepository()
    private var navigatedToNotePreview: String? = null

    private fun note(noteId: String, text: String) = HomeNote(
        noteId = noteId,
        text = text,
        username = "tester",
        favorite = true,
    )

    private fun launchFavoriteScreen() {
        val errorHandler = FakeErrorHandler()
        val noteActions = NoteActionsDelegate(noteRepository, NoteHandler(), errorHandler)
        val viewModel = FavoriteViewModel(
            noteRepository = noteRepository,
            analyticsRepository = FakeAnalyticsRepository(),
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
                FavoriteScreen(
                    onNavigateToNotePreview = { navigatedToNotePreview = it },
                    onNavigateToEditNote = {},
                    onNavigateToPhotoPreview = {},
                    onNavigateToVideoPreview = {},
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
    fun favorites_areRendered() {
        noteRepository.setFavorites(listOf(note("doc1", "favorited note")))
        launchFavoriteScreen()

        waitForText("favorited note")
        composeTestRule.onNodeWithText("favorited note").assertIsDisplayed()
    }

    @Test
    fun noFavorites_showsNoResultsFound() {
        noteRepository.setFavorites(emptyList())
        launchFavoriteScreen()

        waitForText(string(R.string.no_results_found))
        composeTestRule.onNodeWithText(string(R.string.no_results_found)).assertIsDisplayed()
    }

    @Test
    fun tappingNote_navigatesToNotePreview() {
        noteRepository.setFavorites(listOf(note("doc-nav", "Tap me")))
        launchFavoriteScreen()
        waitForText("Tap me")

        composeTestRule.onNodeWithText("Tap me").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) { navigatedToNotePreview == "doc-nav" }
        assertEquals("doc-nav", navigatedToNotePreview)
    }

    @Test
    fun overflowMenu_deleteConfirmed_removesNoteFromList() {
        noteRepository.setFavorites(listOf(note("doc1", "Deletable favorite")))
        launchFavoriteScreen()
        waitForText("Deletable favorite")

        composeTestRule.onNodeWithContentDescription(string(R.string.cd_more_options))
            .performClick()
        waitForText(string(R.string.delete))
        composeTestRule.onNodeWithText(string(R.string.delete)).performClick()

        waitForText(string(R.string.delete_note))
        composeTestRule.onNodeWithText(string(R.string.delete)).performClick()

        waitForText(string(R.string.no_results_found))
        composeTestRule.onNodeWithText(string(R.string.no_results_found)).assertIsDisplayed()
    }

    @Test
    fun overflowMenu_unfavorite_removesNoteFromList() {
        noteRepository.setFavorites(listOf(note("doc1", "Unlike me")))
        launchFavoriteScreen()
        waitForText("Unlike me")

        composeTestRule.onNodeWithContentDescription(string(R.string.cd_more_options))
            .performClick()
        waitForText(string(R.string.remove_like))
        composeTestRule.onNodeWithText(string(R.string.remove_like)).performClick()

        waitForText(string(R.string.no_results_found))
        composeTestRule.onNodeWithText(string(R.string.no_results_found)).assertIsDisplayed()
    }
}