package com.jiahan.smartcamera.search

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import com.jiahan.smartcamera.feature.search.R
import com.jiahan.smartcamera.core.ui.R as UiR
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.fake.FakeAnalyticsRepository
import com.jiahan.smartcamera.fake.FakeErrorHandler
import com.jiahan.smartcamera.fake.FakeNoteRepository
import com.jiahan.smartcamera.fake.FakeMediaFileRepository
import com.jiahan.smartcamera.fake.FakeResourceProvider
import com.jiahan.smartcamera.note.NoteErrorReporter
import com.jiahan.smartcamera.note.NoteShareDelegate
import com.jiahan.smartcamera.ui.theme.SmartCameraTheme
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for [SearchScreen].
 *
 * Exercises the debounced query flow end-to-end: typing into the search field drives the injected
 * [SearchViewModel] (built from fakes) through Idle -> Loading -> Success/Error and asserts the
 * rendered result. No Firebase, analytics, or network is involved.
 */
class SearchScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val noteRepository = FakeNoteRepository()

    private fun note(noteId: String, text: String) = HomeNote(
        noteId = noteId,
        text = text,
        username = "tester",
        favorite = false,
    )

    private fun launchSearchScreen() {
        val errorHandler = FakeErrorHandler()
        val noteErrorReporter = NoteErrorReporter(errorHandler)
        val viewModel = SearchViewModel(
            noteRepository = noteRepository,
            analyticsRepository = FakeAnalyticsRepository(),
            noteErrorReporter = noteErrorReporter,
            noteShare = NoteShareDelegate(
                FakeMediaFileRepository(),
                noteErrorReporter,
                FakeResourceProvider(composeTestRule.activity)
            ),
            errorHandler = errorHandler,
        )
        composeTestRule.setContent {
            SmartCameraTheme {
                SearchScreen(
                    onNavigateToNotePreview = {},
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

    private fun typeQuery(query: String) {
        composeTestRule.onNode(hasSetTextAction()).performTextInput(query)
    }

    @Test
    fun initialState_isIdle_showsNoResultsFound() {
        launchSearchScreen()

        composeTestRule.onNodeWithText(string(UiR.string.no_results_found)).assertIsDisplayed()
    }

    @Test
    fun typingQuery_withMatches_rendersResults() {
        noteRepository.searchResult = Result.success(listOf(note("doc1", "matching note")))
        launchSearchScreen()

        typeQuery("matching")

        // The query is debounced (300 ms) before searchNotes runs.
        waitForText("matching note")
        composeTestRule.onNodeWithText("matching note").assertIsDisplayed()
    }

    @Test
    fun typingQuery_withNoMatches_showsNoResultsFound() {
        noteRepository.searchResult = Result.success(emptyList())
        launchSearchScreen()

        typeQuery("nothing")

        waitForText(string(UiR.string.no_results_found))
        composeTestRule.onNodeWithText(string(UiR.string.no_results_found)).assertIsDisplayed()
    }

    @Test
    fun searchFailure_showsErrorMessage() {
        noteRepository.searchResult = Result.failure(RuntimeException("Search failed"))
        launchSearchScreen()

        typeQuery("boom")

        waitForText("Search failed")
        composeTestRule.onNodeWithText("Search failed").assertIsDisplayed()
    }
}