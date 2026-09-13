package com.jiahan.smartcamera.preview

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import com.jiahan.smartcamera.feature.preview.R
import com.jiahan.smartcamera.core.ui.R as UiR
import com.jiahan.smartcamera.domain.MediaDetail
import com.jiahan.smartcamera.domain.Note
import com.jiahan.smartcamera.fake.FakeErrorHandler
import com.jiahan.smartcamera.fake.FakeMediaFileRepository
import com.jiahan.smartcamera.fake.FakeNoteRepository
import com.jiahan.smartcamera.note.NoteErrorReporter
import com.jiahan.smartcamera.note.NoteShareDelegate
import com.jiahan.smartcamera.ui.theme.SmartPhotosTheme
import com.jiahan.smartcamera.uitest.BaseScreenTest
import com.jiahan.smartcamera.uitest.UI_TEST_TIMEOUT_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for [NotePreviewScreen].
 *
 * A real [NotePreviewViewModel] is built from in-memory fakes and a manually-constructed
 * [SavedStateHandle] (standing in for the `NotePreviewRoute` nav route), so the screen renders
 * end-to-end with no Firebase, no network, and no real navigation graph.
 *
 * Deliberately NOT covered here: the favorite-toggle icon (identified only by an accessibility
 * `onClickLabel`, not text/content-description — already thoroughly covered at the ViewModel level
 * in `NotePreviewViewModelTest`, in the `test` source set) and the share icon (fires a real system
 * share-sheet intent on device; no existing screen test in this codebase exercises share for the
 * same reason).
 */
@RunWith(AndroidJUnit4::class)
class NotePreviewScreenTest : BaseScreenTest() {

    private val noteRepository = FakeNoteRepository()

    private var navigatedBack = false
    private var navigatedToEditNoteId: String? = null
    private var navigatedToPhotoPreviewUrl: String? = null
    private var navigatedToVideoPreviewUrl: String? = null

    private fun note(
        noteId: String = "note1",
        text: String? = "Note body",
        mediaList: List<MediaDetail>? = null,
    ) = Note(
        noteId = noteId,
        text = text,
        username = "tester",
        mediaList = mediaList,
    )

    private fun launchNotePreviewScreen(noteId: String = "note1") {
        val errorHandler = FakeErrorHandler()
        val noteErrorReporter = NoteErrorReporter(errorHandler)
        val viewModel = NotePreviewViewModel(
            savedStateHandle = SavedStateHandle(mapOf("noteId" to noteId)),
            noteRepository = noteRepository,
            noteErrorReporter = noteErrorReporter,
            errorHandler = errorHandler,
            noteShare = NoteShareDelegate(
                FakeMediaFileRepository(),
                noteErrorReporter,
            ),
        )
        composeTestRule.setContent {
            SmartPhotosTheme {
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
        composeTestRule.waitUntil(timeoutMillis = UI_TEST_TIMEOUT_MS) {
            noteRepository.deleteCallCount == 1
        }
        assertEquals("note-to-delete", noteRepository.lastDeletedNoteId)
    }

    /*
     * Media URLs use a `test://` scheme rather than a plausible `https://` one -- see the note in
     * NoteScreenTest. Coil picks a fetcher by scheme and this module declares none for http, so an
     * https fixture is inert here *today* and would start making real requests the day
     * `coil-network-okhttp` reached this module. These assertions only need the string back.
     */

    @Test
    fun photoMediaTap_navigatesToPhotoPreview() {
        noteRepository.getNoteResult = Result.success(
            note(mediaList = listOf(MediaDetail(photoUrl = "test://photo-1")))
        )
        launchNotePreviewScreen()
        waitForText("Note body")

        composeTestRule.onNodeWithContentDescription(string(UiR.string.cd_note_photo))
            .performClick()

        assertEquals("test://photo-1", navigatedToPhotoPreviewUrl)
    }

    @Test
    fun videoMediaTap_navigatesToVideoPreview() {
        noteRepository.getNoteResult = Result.success(
            note(
                mediaList = listOf(
                    MediaDetail(
                        videoUrl = "test://video-1",
                        thumbnailUrl = "test://thumb-1",
                        isVideo = true,
                    )
                )
            )
        )
        launchNotePreviewScreen()
        waitForText("Note body")

        composeTestRule.onNodeWithContentDescription(string(UiR.string.cd_note_photo))
            .performClick()

        assertEquals("test://video-1", navigatedToVideoPreviewUrl)
    }
}