package com.jiahan.smartcamera.favorite

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.fake.FakeAnalyticsRepository
import com.jiahan.smartcamera.fake.FakeErrorHandler
import com.jiahan.smartcamera.fake.FakeNoteRepository
import com.jiahan.smartcamera.note.NoteHandler
import com.jiahan.smartcamera.ui.theme.SmartCameraTheme
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

    private fun note(documentPath: String, text: String) = HomeNote(
        text = text,
        documentPath = documentPath,
        username = "tester",
        favorite = true,
    )

    private fun launchFavoriteScreen() {
        val viewModel = FavoriteViewModel(
            noteRepository = noteRepository,
            analyticsRepository = FakeAnalyticsRepository(),
            noteHandler = NoteHandler(),
            errorHandler = FakeErrorHandler(),
        )
        composeTestRule.setContent {
            SmartCameraTheme {
                FavoriteScreen(
                    onNavigateToNotePreview = {},
                    onNavigateToPhotoPreview = {},
                    onNavigateToVideoPreview = {},
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
}