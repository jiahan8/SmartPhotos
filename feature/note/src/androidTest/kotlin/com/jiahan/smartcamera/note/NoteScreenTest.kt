package com.jiahan.smartcamera.note

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
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
import com.jiahan.smartcamera.fake.FakeNoteRepository
import com.jiahan.smartcamera.fake.FakeResourceProvider
import com.jiahan.smartcamera.fake.FakeUserPreferencesRepository
import com.jiahan.smartcamera.ui.theme.SmartCameraTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for [NoteScreen].
 *
 * A real [NoteViewModel] is built from in-memory fakes, so the screen renders end-to-end with no
 * Firebase, no network, and no real navigation graph.
 *
 * The camera/gallery picker icons (photo library, take photo, take video) are NOT exercised here:
 * they launch real system Activities via `rememberLauncherForActivityResult`, which would need
 * Espresso-Intents to stub — not used anywhere else in this codebase. Instead, media-carousel
 * behavior (display, tap-to-preview, remove) is tested by calling [NoteViewModel.updateUriList]
 * directly, which is exactly what each picker's result callback does on success — this exercises
 * the same downstream state and UI, just skipping the system picker UI itself.
 */
class NoteScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val noteRepository = FakeNoteRepository()

    private var navigatedBack = false
    private var navigatedToPhotoPreviewUri: String? = null
    private var navigatedToVideoPreviewUri: String? = null

    private lateinit var viewModel: NoteViewModel

    private fun launchNoteScreen() {
        viewModel = NoteViewModel(
            noteRepository = noteRepository,
            userPreferencesRepository = FakeUserPreferencesRepository(
                initial = UserPreferences(
                    isDarkTheme = false,
                    username = "tester",
                    profilePicture = null,
                )
            ),
            analyticsRepository = FakeAnalyticsRepository(),
            mediaFileRepository = FakeMediaFileRepository(),
            incomingShareHandler = IncomingShareHandler(),
            resourceProvider = FakeResourceProvider(composeTestRule.activity),
            errorHandler = FakeErrorHandler(),
        )
        composeTestRule.setContent {
            SmartCameraTheme {
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

    private fun string(resId: Int) = composeTestRule.activity.getString(resId)

    private fun waitForText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
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

        composeTestRule.onNodeWithContentDescription(string(UiR.string.cd_clear_field)).performClick()

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

    @Test
    fun mediaAttached_showsCarousel_andEnablesSaveWithoutText() {
        noteRepository.buildLocalMediaDetailsResult = Result.success(
            listOf(NoteMediaDetail(photoUri = MediaUri("https://example.com/photo.jpg")))
        )
        launchNoteScreen()
        waitForText("tester")

        viewModel.updateUriList(listOf(Uri.parse("content://fake/photo1")))

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithContentDescription(string(UiR.string.cd_note_photo))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(string(R.string.save)).assertIsEnabled()
    }

    @Test
    fun photoMediaTap_navigatesToPhotoPreview() {
        noteRepository.buildLocalMediaDetailsResult = Result.success(
            listOf(NoteMediaDetail(photoUri = MediaUri("https://example.com/photo.jpg")))
        )
        launchNoteScreen()
        waitForText("tester")
        viewModel.updateUriList(listOf(Uri.parse("content://fake/photo1")))
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithContentDescription(string(UiR.string.cd_note_photo))
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithContentDescription(string(UiR.string.cd_note_photo)).performClick()

        assertEquals("https://example.com/photo.jpg", navigatedToPhotoPreviewUri)
    }

    @Test
    fun removeMediaButton_removesItemFromCarousel_andDisablesSave() {
        noteRepository.buildLocalMediaDetailsResult = Result.success(
            listOf(NoteMediaDetail(photoUri = MediaUri("https://example.com/photo.jpg")))
        )
        launchNoteScreen()
        waitForText("tester")
        viewModel.updateUriList(listOf(Uri.parse("content://fake/photo1")))
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithContentDescription(string(UiR.string.cd_note_photo))
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithContentDescription(string(R.string.cd_remove_image))
            .performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithContentDescription(string(UiR.string.cd_note_photo))
                .fetchSemanticsNodes().isEmpty()
        }
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

        composeTestRule.waitUntil(timeoutMillis = 5_000) { navigatedBack }
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

        composeTestRule.waitUntil(timeoutMillis = 5_000) { noteRepository.addNoteCallCount == 1 }
        composeTestRule.waitForIdle()
        assertFalse(navigatedBack)
        composeTestRule.onNodeWithText(string(R.string.save)).assertIsEnabled()
        composeTestRule.onNodeWithText("My new note").assertIsDisplayed()
    }
}