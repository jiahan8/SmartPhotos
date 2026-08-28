package com.jiahan.smartcamera.profile

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.domain.User
import com.jiahan.smartcamera.fake.FakeAnalyticsRepository
import com.jiahan.smartcamera.fake.FakeAuthRepository
import com.jiahan.smartcamera.fake.FakeErrorHandler
import com.jiahan.smartcamera.fake.FakeMediaFileRepository
import com.jiahan.smartcamera.fake.FakeNoteRepository
import com.jiahan.smartcamera.fake.FakeResourceProvider
import com.jiahan.smartcamera.fake.FakeUserPreferencesRepository
import com.jiahan.smartcamera.fake.FakeUserRepository
import com.jiahan.smartcamera.ui.theme.SmartCameraTheme
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Instant

/**
 * Compose UI tests for [ProfileScreen].
 *
 * A real [ProfileViewModel] is built from fakes and preloaded with a [User], so the screen exercises
 * profile rendering, inline field validation, and the enable/submit behavior of the save button
 * without Firebase, storage, or the camera. The profile picture URL is left null so Coil performs no
 * network I/O.
 *
 * Device-only (`androidTest`): the bottom-anchored save button and inline validation text depend on
 * the real viewport/scroll behavior, which differs under Robolectric's fixed-size rendering.
 */
@RunWith(AndroidJUnit4::class)
class ProfileScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val userRepository = FakeUserRepository()
    private val authRepository = FakeAuthRepository()

    private fun launchProfileScreen() {
        val activity = composeTestRule.activity
        val viewModel = ProfileViewModel(
            userRepository = userRepository,
            authRepository = authRepository,
            userPreferencesRepository = FakeUserPreferencesRepository(),
            mediaFileRepository = FakeMediaFileRepository(),
            noteRepository = FakeNoteRepository(),
            analyticsRepository = FakeAnalyticsRepository(),
            resourceProvider = FakeResourceProvider(activity),
            errorHandler = FakeErrorHandler(),
        )
        composeTestRule.setContent {
            SmartCameraTheme {
                ProfileScreen(
                    onNavigateToSettings = {},
                    onNavigateToPhotoPreview = {},
                    viewModel = viewModel,
                    snackbarHostState = remember { SnackbarHostState() },
                )
            }
        }
    }

    private fun seedUser() {
        userRepository.user = User(
            userId = "john",
            email = "john@test.com",
            metadata = "meta",
            displayName = "John Doe",
            username = "johndoe",
            profilePicture = null,
            createdDate = Instant.fromEpochMilliseconds(0L),
        )
    }

    private fun string(resId: Int) = composeTestRule.activity.getString(resId)

    private fun waitForText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun loadedProfile_rendersUserFields() {
        seedUser()
        launchProfileScreen()

        waitForText("John Doe")
        composeTestRule.onNodeWithText("John Doe").assertIsDisplayed()
        composeTestRule.onNodeWithText("johndoe").assertIsDisplayed()
        composeTestRule.onNodeWithText("john@test.com").assertIsDisplayed()
    }

    @Test
    fun invalidUsername_showsValidationError() {
        seedUser()
        launchProfileScreen()
        waitForText("johndoe")

        composeTestRule.onNodeWithText("johndoe").performTextReplacement("bad username")

        waitForText(string(R.string.username_invalid_characters))
        composeTestRule.onNodeWithText(string(R.string.username_invalid_characters))
            .assertIsDisplayed()
    }

    @Test
    fun clearingName_showsEmptyValidationError() {
        seedUser()
        launchProfileScreen()
        waitForText("John Doe")

        composeTestRule.onNodeWithText("John Doe").performTextReplacement("")

        waitForText(string(R.string.name_empty))
        composeTestRule.onNodeWithText(string(R.string.name_empty)).assertIsDisplayed()
    }

    @Test
    fun saveButton_isDisabledUntilAValidChangeIsMade() {
        seedUser()
        launchProfileScreen()
        waitForText("John Doe")

        composeTestRule.onNodeWithText(string(R.string.save_changes)).assertIsNotEnabled()

        composeTestRule.onNodeWithText("John Doe").performTextReplacement("Jane Doe")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(string(R.string.save_changes)).assertIsEnabled()
    }

    @Test
    fun savingValidChange_invokesRepositoryUpdate() {
        seedUser()
        launchProfileScreen()
        waitForText("John Doe")

        composeTestRule.onNodeWithText("John Doe").performTextReplacement("Jane Doe")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.save_changes)).performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            userRepository.updateUserProfileCallCount == 1
        }
        assertEquals("Jane Doe", userRepository.lastUpdatedDisplayName)
    }
}