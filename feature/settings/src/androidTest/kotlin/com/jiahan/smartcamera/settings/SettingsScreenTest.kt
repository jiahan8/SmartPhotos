package com.jiahan.smartcamera.settings

import androidx.activity.ComponentActivity
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
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.jiahan.smartcamera.feature.settings.R
import com.jiahan.smartcamera.core.ui.R as UiR
import com.jiahan.smartcamera.fake.FakeAnalyticsRepository
import com.jiahan.smartcamera.fake.FakeAuthRepository
import com.jiahan.smartcamera.fake.FakeErrorHandler
import com.jiahan.smartcamera.fake.FakeResourceProvider
import com.jiahan.smartcamera.fake.FakeUserPreferencesRepository
import com.jiahan.smartcamera.ui.theme.SmartCameraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for [SettingsScreen] using injected test doubles for the [SettingsViewModel].
 *
 * Covers both rendering and the behavioral side-effects: toggling persists via the preferences
 * repository, and confirming the logout/delete dialogs invokes the auth repository and triggers
 * navigation. Navigation is verified through the captured [navigatedToAuth] callback.
 */
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val authRepository = FakeAuthRepository()
    private val preferencesRepository = FakeUserPreferencesRepository()
    private var navigatedToAuth = false

    private fun launchSettingsScreen() {
        val viewModel = SettingsViewModel(
            authRepository = authRepository,
            analyticsRepository = FakeAnalyticsRepository(),
            userPreferencesRepository = preferencesRepository,
            resourceProvider = FakeResourceProvider(composeTestRule.activity),
            errorHandler = FakeErrorHandler(),
        )
        composeTestRule.setContent {
            SmartCameraTheme {
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

    private fun string(resId: Int) = composeTestRule.activity.getString(resId)

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

    @Test
    fun confirmingLogout_signsOut_andNavigatesToAuth() {
        launchSettingsScreen()

        composeTestRule.onNodeWithText(string(R.string.log_out)).performClick()
        composeTestRule.waitForIdle()

        // The confirm button is the dialog's clickable node carrying the label; the list row also
        // carries the label and a click action, so scope the match to inside the dialog.
        composeTestRule.onNode(
            hasText(string(R.string.log_out)) and hasClickAction() and hasAnyAncestor(isDialog())
        ).performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) { navigatedToAuth }
        assertEquals(1, authRepository.signOutCallCount)
    }

    @Test
    fun tappingDeleteAccount_showsConfirmationDialog() {
        launchSettingsScreen()

        composeTestRule.onNodeWithText(string(R.string.delete_account)).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(string(R.string.delete_account_desc)).assertIsDisplayed()
    }

    @Test
    fun confirmingDeleteAccount_deletesAccount_andNavigatesToAuth() {
        launchSettingsScreen()

        composeTestRule.onNodeWithText(string(R.string.delete_account)).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNode(
            hasText(string(R.string.delete_account)) and hasClickAction() and hasAnyAncestor(
                isDialog()
            )
        ).performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) { navigatedToAuth }
        assertEquals(1, authRepository.deleteAccountCallCount)
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

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
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

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            authRepository.changePasswordCallCount == 1
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.current_password)).assertIsDisplayed()
    }
}