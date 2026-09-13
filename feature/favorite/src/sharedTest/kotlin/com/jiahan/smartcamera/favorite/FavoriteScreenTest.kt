package com.jiahan.smartcamera.favorite

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jiahan.smartcamera.feature.favorite.R
import com.jiahan.smartcamera.core.ui.R as UiR
import com.jiahan.smartcamera.domain.Note
import com.jiahan.smartcamera.fake.FakeAnalyticsRepository
import com.jiahan.smartcamera.fake.FakeErrorHandler
import com.jiahan.smartcamera.fake.FakeNoteRepository
import com.jiahan.smartcamera.fake.FakeMediaFileRepository
import com.jiahan.smartcamera.note.NoteErrorReporter
import com.jiahan.smartcamera.note.NoteShareDelegate
import com.jiahan.smartcamera.ui.theme.SmartPhotosTheme
import com.jiahan.smartcamera.uitest.BaseScreenTest
import com.jiahan.smartcamera.uitest.UI_TEST_TIMEOUT_MS
import org.junit.Assert.assertEquals
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for [FavoriteScreen].
 *
 * The favorites list is driven by the fake's reactive stream, so this verifies the debounced
 * `getFavoriteNotesStream` -> `stateIn` -> UI pipeline and the loading/empty/content branches with a
 * real [FavoriteViewModel] and no Firebase.
 *
 * Lives in `sharedTest`, so it runs on the JVM (Robolectric) in CI and on-device under the
 * instrumentation runner -- the :feature:auth arrangement.
 */
@RunWith(AndroidJUnit4::class)
class FavoriteScreenTest : BaseScreenTest() {

    private val noteRepository = FakeNoteRepository()
    private var navigatedToNotePreview: String? = null

    private fun note(noteId: String, text: String) = Note(
        noteId = noteId,
        text = text,
        username = "tester",
        isFavorite = true,
    )

    private fun launchFavoriteScreen() {
        val errorHandler = FakeErrorHandler()
        val noteErrorReporter = NoteErrorReporter(errorHandler)
        val viewModel = FavoriteViewModel(
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
                FavoriteScreen(
                    onNavigateToNotePreview = { navigatedToNotePreview = it },
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

    @Test
    fun favorites_areRendered() {
        noteRepository.setFavorites(listOf(note("doc1", "favorited note")))
        launchFavoriteScreen()

        waitForText("favorited note")
        composeTestRule.onNodeWithText("favorited note").assertIsDisplayed()
    }

    /**
     * With no query typed, an empty favorites list is a prompt rather than a "no results" -- the
     * screen picks between the two on `searchQuery.isBlank()`. All three empty-state cases in this
     * file asserted the searched-and-found-nothing copy, which none of them can reach.
     */
    @Test
    fun noFavorites_showsFavoriteNotePrompt() {
        noteRepository.setFavorites(emptyList())
        launchFavoriteScreen()

        waitForText(string(R.string.favorite_note_to_see_it_here))
        composeTestRule.onNodeWithText(string(R.string.favorite_note_to_see_it_here))
            .assertIsDisplayed()
    }

    @Test
    fun tappingNote_navigatesToNotePreview() {
        noteRepository.setFavorites(listOf(note("doc-nav", "Tap me")))
        launchFavoriteScreen()
        waitForText("Tap me")

        composeTestRule.onNodeWithText("Tap me").performClick()

        composeTestRule.waitUntil(timeoutMillis = UI_TEST_TIMEOUT_MS) { navigatedToNotePreview == "doc-nav" }
        assertEquals("doc-nav", navigatedToNotePreview)
    }

    @Test
    fun overflowMenu_deleteConfirmed_removesNoteFromList() {
        noteRepository.setFavorites(listOf(note("doc1", "Deletable favorite")))
        launchFavoriteScreen()
        waitForText("Deletable favorite")

        composeTestRule.onNodeWithContentDescription(string(UiR.string.cd_more_options))
            .performClick()
        waitForText(string(UiR.string.delete))
        composeTestRule.onNodeWithText(string(UiR.string.delete)).performClick()

        waitForText(string(UiR.string.delete_note))
        composeTestRule.onNodeWithText(string(UiR.string.delete)).performClick()

        waitForText(string(R.string.favorite_note_to_see_it_here))
        composeTestRule.onNodeWithText(string(R.string.favorite_note_to_see_it_here))
            .assertIsDisplayed()
    }

    @Test
    fun overflowMenu_unfavorite_removesNoteFromList() {
        noteRepository.setFavorites(listOf(note("doc1", "Unlike me")))
        launchFavoriteScreen()
        waitForText("Unlike me")

        composeTestRule.onNodeWithContentDescription(string(UiR.string.cd_more_options))
            .performClick()
        waitForText(string(UiR.string.remove_like))
        composeTestRule.onNodeWithText(string(UiR.string.remove_like)).performClick()

        waitForText(string(R.string.favorite_note_to_see_it_here))
        composeTestRule.onNodeWithText(string(R.string.favorite_note_to_see_it_here))
            .assertIsDisplayed()
    }
}