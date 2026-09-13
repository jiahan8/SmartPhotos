package com.jiahan.smartcamera.search

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import com.jiahan.smartcamera.domain.Note
import com.jiahan.smartcamera.fake.FakeAnalyticsRepository
import com.jiahan.smartcamera.fake.FakeErrorHandler
import com.jiahan.smartcamera.fake.FakeMediaFileRepository
import com.jiahan.smartcamera.fake.FakeNoteRepository
import com.jiahan.smartcamera.note.NoteErrorReporter
import com.jiahan.smartcamera.note.NoteShareDelegate
import com.jiahan.smartcamera.ui.theme.SmartPhotosTheme
import com.jiahan.smartcamera.uitest.BaseScreenTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import com.jiahan.smartcamera.feature.search.R
import com.jiahan.smartcamera.core.ui.R as UiR

/**
 * Compose UI tests for [SearchScreen].
 *
 * Exercises the debounced query flow end-to-end: typing into the search field drives the injected
 * [SearchViewModel] (built from fakes) through Idle -> Loading -> Success/Error and asserts the
 * rendered result. No Firebase, analytics, or network is involved.
 *
 * Lives in `sharedTest`, so it runs on the JVM (Robolectric) in CI and on-device under the
 * instrumentation runner. It was androidTest-only, which meant CI compiled it and never ran it --
 * and `initialState_isIdle` sat asserting the wrong screen's copy the whole time.
 */
@RunWith(AndroidJUnit4::class)
class SearchScreenTest : BaseScreenTest() {

    private val noteRepository = FakeNoteRepository()

    private fun note(noteId: String, text: String) = Note(
        noteId = noteId,
        text = text,
        username = "tester",
        isFavorite = false,
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
            ),
            errorHandler = errorHandler,
        )
        composeTestRule.setContent {
            SmartPhotosTheme {
                SearchScreen(
                    onNavigateToNotePreview = {},
                    onNavigateToEditNote = {},
                    onNavigateToPhotoPreview = {},
                    onNavigateToVideoPreview = {},
                    viewModel = viewModel,
                    scrollToTopRequestedAt = null,
                    onScrollToTopConsumed = {},
                    snackbarHostState = remember { SnackbarHostState() },
                )
            }
        }
    }

    private fun typeQuery(query: String) {
        composeTestRule.onNode(hasSetTextAction()).performTextInput(query)
    }

    /**
     * Idle and an empty result set are different states with different copy, and only this one is
     * reachable before a query runs. Asserting `no_results_found` here -- the *other* branch's
     * string -- is what this test used to do, and it could never have passed.
     */
    @Test
    fun initialState_isIdle_showsSearchPrompt() {
        launchSearchScreen()

        composeTestRule.onNodeWithText(string(R.string.search_your_notes)).assertIsDisplayed()
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