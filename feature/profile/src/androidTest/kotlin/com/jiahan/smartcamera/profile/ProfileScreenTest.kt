package com.jiahan.smartcamera.profile

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.jiahan.smartcamera.core.common.R as CommonR
import com.jiahan.smartcamera.domain.User
import com.jiahan.smartcamera.fake.FakeAnalyticsRepository
import com.jiahan.smartcamera.fake.FakeAuthRepository
import com.jiahan.smartcamera.fake.FakeErrorHandler
import com.jiahan.smartcamera.fake.FakeMediaFileRepository
import com.jiahan.smartcamera.fake.FakeMediaUploadRepository
import com.jiahan.smartcamera.fake.FakeUserPreferencesRepository
import com.jiahan.smartcamera.fake.FakeUserRepository
import com.jiahan.smartcamera.feature.profile.R
import com.jiahan.smartcamera.ui.theme.SmartPhotosTheme
import com.jiahan.smartcamera.uitest.BaseScreenTest
import com.jiahan.smartcamera.uitest.UI_TEST_TIMEOUT_MS
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
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
class ProfileScreenTest : BaseScreenTest() {

    private val userRepository = FakeUserRepository()
    private val authRepository = FakeAuthRepository()

    private fun launchProfileScreen() {
        val activity = composeTestRule.activity
        val viewModel = ProfileViewModel(
            userRepository = userRepository,
            authRepository = authRepository,
            userPreferencesRepository = FakeUserPreferencesRepository(),
            mediaFileRepository = FakeMediaFileRepository(),
            mediaUploadRepository = FakeMediaUploadRepository(),
            analyticsRepository = FakeAnalyticsRepository(),
            errorHandler = FakeErrorHandler(),
        )
        composeTestRule.setContent {
            SmartPhotosTheme {
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
            profilePictureUrl = null,
            createdDate = Instant.fromEpochMilliseconds(0L),
        )
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

        waitForText(string(CommonR.string.username_invalid_characters))
        composeTestRule.onNodeWithText(string(CommonR.string.username_invalid_characters))
            .assertIsDisplayed()
    }

    @Test
    fun clearingName_showsEmptyValidationError() {
        seedUser()
        launchProfileScreen()
        waitForText("John Doe")

        composeTestRule.onNodeWithText("John Doe").performTextReplacement("")

        waitForText(string(CommonR.string.name_empty))
        composeTestRule.onNodeWithText(string(CommonR.string.name_empty)).assertIsDisplayed()
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

        composeTestRule.waitUntil(timeoutMillis = UI_TEST_TIMEOUT_MS) {
            userRepository.updateUserProfileCallCount == 1
        }
        assertEquals("Jane Doe", userRepository.lastUpdatedDisplayName)
    }
}