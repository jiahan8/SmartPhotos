package com.jiahan.smartcamera.note

import android.net.Uri
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.jiahan.smartcamera.feature.note.R
import com.jiahan.smartcamera.core.common.R as CommonR
import com.jiahan.smartcamera.core.ui.R as UiR
import com.jiahan.smartcamera.data.datastore.UserPreferences
import com.jiahan.smartcamera.domain.MediaUri
import com.jiahan.smartcamera.domain.NoteMediaDetail
import com.jiahan.smartcamera.fake.FakeAnalyticsRepository
import com.jiahan.smartcamera.fake.FakeErrorHandler
import com.jiahan.smartcamera.fake.FakeMediaFileRepository
import com.jiahan.smartcamera.fake.FakeMediaUploadRepository
import com.jiahan.smartcamera.fake.FakeNoteRepository
import com.jiahan.smartcamera.fake.FakeResourceProvider
import com.jiahan.smartcamera.fake.FakeUserPreferencesRepository
import com.jiahan.smartcamera.ui.theme.SmartPhotosTheme
import com.jiahan.smartcamera.uitest.BaseScreenTest
import com.jiahan.smartcamera.uitest.UI_TEST_TIMEOUT_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for [NoteScreen].
 *
 * A real [NoteViewModel] is built from in-memory fakes, so the screen renders end-to-end with no
 * Firebase, no network, and no real navigation graph.
 *
 * The camera/gallery picker icons (photo library, take photo, take video) are NOT exercised here:
 * they launch real system Activities via `rememberLauncherForActivityResult`, which would need
 * Espresso-Intents to stub — not used anywhere else in this codebase. Instead, media-carousel
 * behavior (display, tap-to-preview, remove) is tested by calling [NoteViewModel.addMedia]
 * directly, which is exactly what each picker's result callback does on success — this exercises
 * the same downstream state and UI, just skipping the system picker UI itself.
 */
@RunWith(AndroidJUnit4::class)
class NoteScreenTest : BaseScreenTest() {

    private val noteRepository = FakeNoteRepository()
    private val mediaUploadRepository = FakeMediaUploadRepository()

    private var navigatedBack = false
    private var navigatedToPhotoPreviewUri: String? = null
    private var navigatedToVideoPreviewUri: String? = null

    private lateinit var viewModel: NoteViewModel

    private fun launchNoteScreen() {
        viewModel = NoteViewModel(
            noteRepository = noteRepository,
            mediaUploadRepository = mediaUploadRepository,
            userPreferencesRepository = FakeUserPreferencesRepository(
                initial = UserPreferences(
                    isDarkTheme = false,
                    username = "tester",
                    profilePictureUrl = null,
                )
            ),
            analyticsRepository = FakeAnalyticsRepository(),
            mediaFileRepository = FakeMediaFileRepository(),
            incomingShareHandler = IncomingShareHandler(),
            resourceProvider = FakeResourceProvider(composeTestRule.activity),
            errorHandler = FakeErrorHandler(),
        )
        composeTestRule.setContent {
            SmartPhotosTheme {
                NoteScreen(
                    onBack = { navigatedBack = true },
                    onNavigateToPhotoPreview = { navigatedToPhotoPreviewUri = it },
                    onNavigateToVideoPreview = { navigatedToVideoPreviewUri = it },
                    viewModel = viewModel,
                    snackbarHostState = remember { SnackbarHostState() },
                )
            }
        }
    }

    @Test
    fun initialState_rendersUsername_andSaveDisabled() {
        launchNoteScreen()

        waitForText("tester")
        composeTestRule.onNodeWithText("tester").assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.save)).assertIsNotEnabled()
    }

    @Test
    fun typingText_enablesSaveButton() {
        launchNoteScreen()
        waitForText("tester")

        composeTestRule.onNode(hasSetTextAction()).performTextInput("My new note")

        waitForText("My new note")
        composeTestRule.onNodeWithText(string(R.string.save)).assertIsEnabled()
    }

    @Test
    fun clearButton_clearsText_andDisablesSave() {
        launchNoteScreen()
        waitForText("tester")
        composeTestRule.onNode(hasSetTextAction())
            .performTextInput("My new note")
        waitForText("My new note")

        composeTestRule.onNodeWithContentDescription(string(UiR.string.cd_clear_field))
            .performClick()

        composeTestRule.onNodeWithText(string(R.string.save)).assertIsNotEnabled()
    }

    @Test
    fun textExceedingMaxLength_showsValidationError_andDisablesSave() {
        launchNoteScreen()
        waitForText("tester")

        composeTestRule.onNode(hasSetTextAction())
            .performTextInput("a".repeat(501))

        waitForText(string(CommonR.string.note_validation))
        composeTestRule.onNodeWithText(string(CommonR.string.note_validation)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.save)).assertIsNotEnabled()
    }

    /*
     * Media URIs use a `test://` scheme rather than a plausible `https://` one. Coil resolves a
     * fetcher by scheme, and this module declares none for http -- `coil-network-okhttp` is :app's
     * -- so today either string merely errors and the carousel renders its placeholder. The day
     * that artifact reaches this module, an `https://` fixture would start making a real request
     * from a unit test. A scheme nothing can ever fetch keeps that from arriving silently, and the
     * navigation assertions below only need the string to survive round-tripping.
     */

    @Test
    fun mediaAttached_showsCarousel_andEnablesSaveWithoutText() {
        mediaUploadRepository.buildLocalMediaDetailsResult = Result.success(
            listOf(NoteMediaDetail(photoUri = MediaUri("test://photo-1")))
        )
        launchNoteScreen()
        waitForText("tester")

        viewModel.addMedia(listOf(Uri.parse("content://fake/photo1")))

        waitForContentDescription(string(UiR.string.cd_note_photo))
        composeTestRule.onNodeWithText(string(R.string.save)).assertIsEnabled()
    }

    @Test
    fun photoMediaTap_navigatesToPhotoPreview() {
        mediaUploadRepository.buildLocalMediaDetailsResult = Result.success(
            listOf(NoteMediaDetail(photoUri = MediaUri("test://photo-1")))
        )
        launchNoteScreen()
        waitForText("tester")
        viewModel.addMedia(listOf(Uri.parse("content://fake/photo1")))
        waitForContentDescription(string(UiR.string.cd_note_photo))

        composeTestRule.onNodeWithContentDescription(string(UiR.string.cd_note_photo))
            .performClick()

        assertEquals("test://photo-1", navigatedToPhotoPreviewUri)
    }

    @Test
    fun removeMediaButton_removesItemFromCarousel_andDisablesSave() {
        mediaUploadRepository.buildLocalMediaDetailsResult = Result.success(
            listOf(NoteMediaDetail(photoUri = MediaUri("test://photo-1")))
        )
        launchNoteScreen()
        waitForText("tester")
        viewModel.addMedia(listOf(Uri.parse("content://fake/photo1")))
        waitForContentDescription(string(UiR.string.cd_note_photo))

        composeTestRule.onNodeWithContentDescription(string(R.string.cd_remove_image))
            .performClick()

        waitForNoContentDescription(string(UiR.string.cd_note_photo))
        composeTestRule.onNodeWithText(string(R.string.save)).assertIsNotEnabled()
    }

    @Test
    fun saveSuccess_addsNoteAndNavigatesBack() {
        noteRepository.addNoteResult = Result.success(Unit)
        launchNoteScreen()
        waitForText("tester")
        composeTestRule.onNode(hasSetTextAction())
            .performTextInput("My new note")
        waitForText("My new note")

        composeTestRule.onNodeWithText(string(R.string.save)).performClick()

        composeTestRule.waitUntil(timeoutMillis = UI_TEST_TIMEOUT_MS) { navigatedBack }
        assertEquals(1, noteRepository.addNoteCallCount)
        assertEquals("My new note", noteRepository.lastAddedNote?.text)
    }

    @Test
    fun saveFailure_staysOnScreenWithTextPreserved() {
        noteRepository.addNoteResult = Result.failure(RuntimeException("save failed"))
        launchNoteScreen()
        waitForText("tester")
        composeTestRule.onNode(hasSetTextAction())
            .performTextInput("My new note")
        waitForText("My new note")

        composeTestRule.onNodeWithText(string(R.string.save)).performClick()

        composeTestRule.waitUntil(timeoutMillis = UI_TEST_TIMEOUT_MS) { noteRepository.addNoteCallCount == 1 }
        composeTestRule.waitForIdle()
        assertFalse(navigatedBack)
        composeTestRule.onNodeWithText(string(R.string.save)).assertIsEnabled()
        composeTestRule.onNodeWithText("My new note").assertIsDisplayed()
    }
}