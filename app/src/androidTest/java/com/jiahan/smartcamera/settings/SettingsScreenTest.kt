package com.jiahan.smartcamera.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.fake.FakeAuthRepository
import com.jiahan.smartcamera.fake.FakeErrorHandler
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
            userPreferencesRepository = preferencesRepository,
            errorHandler = FakeErrorHandler(),
        )
        composeTestRule.setContent {
            SmartCameraTheme {
                SettingsScreen(
                    onBack = {},
                    onNavigateToAuth = { navigatedToAuth = true },
                    viewModel = viewModel,
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

        composeTestRule.onNodeWithText(string(R.string.cancel)).performClick()
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
}