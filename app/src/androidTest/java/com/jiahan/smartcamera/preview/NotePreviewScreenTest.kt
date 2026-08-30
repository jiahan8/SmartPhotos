package com.jiahan.smartcamera.preview

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.core.ui.R as UiR
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.domain.MediaDetail
import com.jiahan.smartcamera.fake.FakeErrorHandler
import com.jiahan.smartcamera.fake.FakeMediaFileRepository
import com.jiahan.smartcamera.fake.FakeNoteRepository
import com.jiahan.smartcamera.fake.FakeResourceProvider
import com.jiahan.smartcamera.note.NoteActionsDelegate
import com.jiahan.smartcamera.note.NoteErrorReporter
import com.jiahan.smartcamera.note.NoteHandler
import com.jiahan.smartcamera.note.NoteShareDelegate
import com.jiahan.smartcamera.ui.theme.SmartCameraTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for [NotePreviewScreen].
 *
 * A real [NotePreviewViewModel] is built from in-memory fakes and a manually-constructed
 * [SavedStateHandle] (standing in for the `Screen.NotePreview` nav route), so the screen renders
 * end-to-end with no Firebase, no network, and no real navigation graph.
 *
 * Deliberately NOT covered here: the favorite-toggle icon (identified only by an accessibility
 * `onClickLabel`, not text/content-description — already thoroughly covered at the ViewModel level
 * in `NotePreviewViewModelTest`, in the `test` source set) and the share icon (fires a real system
 * share-sheet intent on device; no existing screen test in this codebase exercises share for the
 * same reason).
 */
class NotePreviewScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val noteRepository = FakeNoteRepository()
    private val noteHandler = NoteHandler()

    private var navigatedBack = false
    private var navigatedToEditNoteId: String? = null
    private var navigatedToPhotoPreviewUrl: String? = null
    private var navigatedToVideoPreviewUrl: String? = null

    private fun note(
        noteId: String = "note1",
        text: String? = "Note body",
        mediaList: List<MediaDetail>? = null,
    ) = HomeNote(
        noteId = noteId,
        text = text,
        username = "tester",
        mediaList = mediaList,
    )

    private fun launchNotePreviewScreen(noteId: String = "note1") {
        val errorHandler = FakeErrorHandler()
        val noteErrorReporter = NoteErrorReporter(errorHandler)
        val noteActions = NoteActionsDelegate(noteRepository, noteHandler, noteErrorReporter)
        val viewModel = NotePreviewViewModel(
            savedStateHandle = SavedStateHandle(mapOf("id" to noteId)),
            noteRepository = noteRepository,
            noteHandler = noteHandler,
            noteActions = noteActions,
            errorHandler = errorHandler,
            noteShare = NoteShareDelegate(
                FakeMediaFileRepository(),
                noteErrorReporter,
                FakeResourceProvider(composeTestRule.activity)
            ),
        )
        composeTestRule.setContent {
            SmartCameraTheme {
                NotePreviewScreen(
                    onBack = { navigatedBack = true },
                    onNavigateToPhotoPreview = { navigatedToPhotoPreviewUrl = it },
                    onNavigateToVideoPreview = { navigatedToVideoPreviewUrl = it },
                    onNavigateToEdit = { navigatedToEditNoteId = it },
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

    @Test
    fun success_rendersNoteContent() {
        noteRepository.getNoteResult = Result.success(note(text = "Hello preview"))
        launchNotePreviewScreen()

        waitForText("Hello preview")
        composeTestRule.onNodeWithText("Hello preview").assertIsDisplayed()
        composeTestRule.onNodeWithText("tester").assertIsDisplayed()
    }

    @Test
    fun noteLoadFailure_showsErrorMessage() {
        // FakeNoteRepository.getNote() fails by default when getNoteResult is left null.
        launchNotePreviewScreen(noteId = "missing-note")

        waitForText("No note for missing-note")
        composeTestRule.onNodeWithText("No note for missing-note").assertIsDisplayed()
    }

    @Test
    fun editIcon_navigatesToEditNoteWithNoteId() {
        noteRepository.getNoteResult = Result.success(note(noteId = "note-to-edit"))
        launchNotePreviewScreen(noteId = "note-to-edit")
        waitForText("Note body")

        composeTestRule.onNodeWithContentDescription(string(R.string.cd_edit_note)).performClick()

        assertEquals("note-to-edit", navigatedToEditNoteId)
    }

    @Test
    fun overflowMenu_deleteConfirmed_deletesNote_andNavigatesBack() {
        noteRepository.getNoteResult = Result.success(note(noteId = "note-to-delete"))
        launchNotePreviewScreen(noteId = "note-to-delete")
        waitForText("Note body")

        composeTestRule.onNodeWithContentDescription(string(UiR.string.cd_more_options))
            .performClick()
        waitForText(string(UiR.string.delete))
        composeTestRule.onNodeWithText(string(UiR.string.delete)).performClick()

        waitForText(string(UiR.string.delete_note))
        composeTestRule.onNodeWithText(string(UiR.string.delete)).performClick()

        assertTrue(navigatedBack)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            noteRepository.deleteCallCount == 1
        }
        assertEquals("note-to-delete", noteRepository.lastDeletedNoteId)
    }

    @Test
    fun photoMediaTap_navigatesToPhotoPreview() {
        noteRepository.getNoteResult = Result.success(
            note(mediaList = listOf(MediaDetail(photoUrl = "https://example.com/photo.jpg")))
        )
        launchNotePreviewScreen()
        waitForText("Note body")

        composeTestRule.onNodeWithContentDescription(string(UiR.string.cd_note_photo)).performClick()

        assertEquals("https://example.com/photo.jpg", navigatedToPhotoPreviewUrl)
    }

    @Test
    fun videoMediaTap_navigatesToVideoPreview() {
        noteRepository.getNoteResult = Result.success(
            note(
                mediaList = listOf(
                    MediaDetail(
                        videoUrl = "https://example.com/video.mp4",
                        thumbnailUrl = "https://example.com/thumb.jpg",
                        isVideo = true,
                    )
                )
            )
        )
        launchNotePreviewScreen()
        waitForText("Note body")

        composeTestRule.onNodeWithContentDescription(string(UiR.string.cd_note_photo)).performClick()

        assertEquals("https://example.com/video.mp4", navigatedToVideoPreviewUrl)
    }
}