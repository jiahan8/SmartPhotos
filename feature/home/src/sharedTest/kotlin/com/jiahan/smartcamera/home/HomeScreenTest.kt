package com.jiahan.smartcamera.home

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jiahan.smartcamera.feature.home.R
import com.jiahan.smartcamera.core.ui.R as UiR
import com.jiahan.smartcamera.domain.Note
import com.jiahan.smartcamera.fake.FakeErrorHandler
import com.jiahan.smartcamera.fake.FakeMediaFileRepository
import com.jiahan.smartcamera.fake.FakeNoteRepository
import com.jiahan.smartcamera.fake.FakeRemoteConfigRepository
import com.jiahan.smartcamera.note.NoteErrorReporter
import com.jiahan.smartcamera.note.NoteShareDelegate
import com.jiahan.smartcamera.ui.theme.SmartPhotosTheme
import com.jiahan.smartcamera.uitest.BaseScreenTest
import com.jiahan.smartcamera.uitest.UI_TEST_TIMEOUT_MS
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
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
class HomeScreenTest : BaseScreenTest() {

    private val noteRepository = FakeNoteRepository()
    private var navigatedToNotePreview: String? = null

    private fun note(noteId: String, text: String) = Note(
        noteId = noteId,
        text = text,
        username = "tester",
        isFavorite = false,
    )

    private fun launchHomeScreen() {
        val errorHandler = FakeErrorHandler()
        val noteErrorReporter = NoteErrorReporter(errorHandler)
        val viewModel = HomeViewModel(
            noteRepository = noteRepository,
            noteErrorReporter = noteErrorReporter,
            noteShare = NoteShareDelegate(
                FakeMediaFileRepository(),
                noteErrorReporter,
            ),
            errorHandler = errorHandler,
            remoteConfigRepository = FakeRemoteConfigRepository(),
        )
        composeTestRule.setContent {
            SmartPhotosTheme {
                HomeScreen(
                    title = "SmartPhotos",
                    onNavigateToNotePreview = { navigatedToNotePreview = it },
                    onNavigateToEditNote = {},
                    onNavigateToPhotoPreview = {},
                    onNavigateToVideoPreview = {},
                    onNavigateToExplore = {},
                    viewModel = viewModel,
                    scrollToTopRequestedAt = null,
                    onScrollToTopConsumed = {},
                    snackbarHostState = remember { SnackbarHostState() },
                )
            }
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

        waitForNoText("Deletable note")
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

        waitForContentDescription(string(UiR.string.cd_marked_as_favorite))
        composeTestRule.onNodeWithContentDescription(string(UiR.string.cd_marked_as_favorite))
            .assertIsDisplayed()
    }

    @Test
    fun tappingNote_navigatesToNotePreview() {
        noteRepository.setNotes(listOf(note("doc-nav", "Tap me")))
        launchHomeScreen()
        waitForText("Tap me")

        composeTestRule.onNodeWithText("Tap me").performClick()

        composeTestRule.waitUntil(timeoutMillis = UI_TEST_TIMEOUT_MS) { navigatedToNotePreview == "doc-nav" }
        assertEquals("doc-nav", navigatedToNotePreview)
    }
}