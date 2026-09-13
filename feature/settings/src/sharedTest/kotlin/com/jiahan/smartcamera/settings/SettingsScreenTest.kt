package com.jiahan.smartcamera.settings

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.jiahan.smartcamera.feature.settings.R
import com.jiahan.smartcamera.core.ui.R as UiR
import com.jiahan.smartcamera.fake.FakeAnalyticsRepository
import com.jiahan.smartcamera.fake.FakeAuthRepository
import com.jiahan.smartcamera.fake.FakeErrorHandler
import com.jiahan.smartcamera.fake.FakeUserPreferencesRepository
import com.jiahan.smartcamera.ui.theme.SmartPhotosTheme
import com.jiahan.smartcamera.uitest.BaseScreenTest
import com.jiahan.smartcamera.uitest.UI_TEST_TIMEOUT_MS
import org.junit.Assert.assertEquals
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for [SettingsScreen] using injected test doubles for the [SettingsViewModel].
 *
 * Covers both rendering and the behavioral side-effects: toggling persists via the preferences
 * repository, and confirming the logout/delete dialogs invokes the auth repository and triggers
 * navigation. Navigation is verified through the captured [navigatedToAuth] callback.
 *
 * Lives in `sharedTest`, so it runs on the JVM (Robolectric) in CI and on-device under the
 * instrumentation runner. :feature:auth's note used to call this file out by name as the weaker,
 * androidTest-only arrangement; it is no longer the counter-example.
 *
 * The two navigation cases are the exception and live in [SettingsScreenNavigationTest], which is
 * device-only for a reason stated there.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest : BaseScreenTest() {

    private val authRepository = FakeAuthRepository()
    private val preferencesRepository = FakeUserPreferencesRepository()
    private var navigatedToAuth = false

    private fun launchSettingsScreen() {
        val viewModel = SettingsViewModel(
            authRepository = authRepository,
            analyticsRepository = FakeAnalyticsRepository(),
            userPreferencesRepository = preferencesRepository,
            errorHandler = FakeErrorHandler(),
        )
        composeTestRule.setContent {
            SmartPhotosTheme {
                SettingsScreen(
                    onBack = {},
                    onNavigateToAuth = { navigatedToAuth = true },
                    versionName = "1.0.0",
                    viewModel = viewModel,
                    snackbarHostState = remember { SnackbarHostState() },
                )
            }
        }
    }

    @Test
    fun darkThemeSwitch_startsOff_andTurnsOnWhenToggled() {
        launchSettingsScreen()

        composeTestRule.onNode(isToggleable()).assertIsOff()

        composeTestRule.onNode(isToggleable()).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNode(isToggleable()).assertIsOn()
    }

    @Test
    fun tappingLogout_showsConfirmationDialog_andCancelDismissesIt() {
        launchSettingsScreen()

        composeTestRule.onNodeWithText(string(R.string.log_out)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.log_out_desc)).assertIsDisplayed()

        composeTestRule.onNodeWithText(string(UiR.string.cancel)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.log_out_desc)).assertDoesNotExist()

        // Dismissing must not sign out or navigate.
        assertEquals(0, authRepository.signOutCallCount)
        assertEquals(false, navigatedToAuth)
    }

    /**
     * The cancel arm matters more here than on the logout dialog: this is the irreversible action,
     * and the confirm arm lives in [SettingsScreenNavigationTest], which CI never runs. Without
     * this, a delete-account dismiss button wired to the wrong callback would be caught by nothing
     * until someone ran the device suite by hand.
     */
    @Test
    fun tappingDeleteAccount_showsConfirmationDialog_andCancelDismissesIt() {
        launchSettingsScreen()

        composeTestRule.onNodeWithText(string(R.string.delete_account)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.delete_account_desc)).assertIsDisplayed()

        composeTestRule.onNodeWithText(string(UiR.string.cancel)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.delete_account_desc)).assertDoesNotExist()

        // Dismissing must not delete the account or navigate.
        assertEquals(0, authRepository.deleteAccountCallCount)
        assertEquals(false, navigatedToAuth)
    }

    @Test
    fun tappingChangePassword_showsDialog_andCancelDismissesIt() {
        launchSettingsScreen()

        composeTestRule.onNodeWithText(string(R.string.change_password)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.current_password)).assertIsDisplayed()

        composeTestRule.onNodeWithText(string(UiR.string.cancel)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.current_password)).assertDoesNotExist()

        assertEquals(0, authRepository.changePasswordCallCount)
    }

    @Test
    fun fillingValidForm_confirmingChangesPassword_dismissesDialog() {
        launchSettingsScreen()

        composeTestRule.onNodeWithText(string(R.string.change_password)).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(string(R.string.current_password))
            .performTextInput("oldPass1")
        composeTestRule.onNodeWithText(string(R.string.new_password)).performTextInput("newPass1")
        composeTestRule.onNodeWithText(string(R.string.confirm_new_password))
            .performTextInput("newPass1")

        composeTestRule.onNode(
            hasText(string(R.string.change_password)) and hasClickAction() and hasAnyAncestor(
                isDialog()
            )
        ).performClick()

        composeTestRule.waitUntil(timeoutMillis = UI_TEST_TIMEOUT_MS) {
            authRepository.changePasswordCallCount == 1
        }
        assertEquals("oldPass1" to "newPass1", authRepository.lastChangePasswordArgs)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.current_password)).assertDoesNotExist()
    }

    @Test
    fun mismatchedConfirmPassword_blocksSubmission_dialogStaysOpen() {
        launchSettingsScreen()

        composeTestRule.onNodeWithText(string(R.string.change_password)).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(string(R.string.current_password))
            .performTextInput("oldPass1")
        composeTestRule.onNodeWithText(string(R.string.new_password)).performTextInput("newPass1")
        composeTestRule.onNodeWithText(string(R.string.confirm_new_password))
            .performTextInput("different")

        composeTestRule.onNode(
            hasText(string(R.string.change_password)) and hasClickAction() and hasAnyAncestor(
                isDialog()
            )
        ).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(string(R.string.current_password)).assertIsDisplayed()
        assertEquals(0, authRepository.changePasswordCallCount)
    }

    @Test
    fun changePasswordFailure_keepsDialogOpen() {
        authRepository.changePasswordResult = Result.failure(RuntimeException("wrong password"))
        launchSettingsScreen()

        composeTestRule.onNodeWithText(string(R.string.change_password)).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(string(R.string.current_password))
            .performTextInput("wrongPass1")
        composeTestRule.onNodeWithText(string(R.string.new_password)).performTextInput("newPass1")
        composeTestRule.onNodeWithText(string(R.string.confirm_new_password))
            .performTextInput("newPass1")

        composeTestRule.onNode(
            hasText(string(R.string.change_password)) and hasClickAction() and hasAnyAncestor(
                isDialog()
            )
        ).performClick()

        composeTestRule.waitUntil(timeoutMillis = UI_TEST_TIMEOUT_MS) {
            authRepository.changePasswordCallCount == 1
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.current_password)).assertIsDisplayed()
    }
}