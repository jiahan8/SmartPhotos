package com.jiahan.smartcamera.settings

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jiahan.smartcamera.fake.FakeAnalyticsRepository
import com.jiahan.smartcamera.fake.FakeAuthRepository
import com.jiahan.smartcamera.fake.FakeErrorHandler
import com.jiahan.smartcamera.fake.FakeUserPreferencesRepository
import com.jiahan.smartcamera.feature.settings.R
import com.jiahan.smartcamera.ui.theme.SmartPhotosTheme
import com.jiahan.smartcamera.uitest.BaseScreenTest
import com.jiahan.smartcamera.uitest.UI_TEST_TIMEOUT_MS
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The two [SettingsScreen] cases that cannot run on the JVM, kept apart from the sharedTest suite
 * rather than holding it back.
 *
 * Both wait on `onNavigateToAuth`, and `SettingsViewModel` sleeps `AUTH_ACTION_DELAY_MS` (1s) after
 * a successful sign-out/delete before it emits `NavigateToAuth`. That is a real `delay` on
 * `Dispatchers.Main`, so under Robolectric it becomes a message on a paused looper that no amount
 * of `waitUntil` makes due -- `composeTestRule.mainClock.advanceTimeBy` drives the Compose frame
 * clock, not the looper's, and was tried. On device the looper runs in wall-clock time and the
 * existing 5s timeout covers it comfortably.
 *
 * Everything else about this screen -- the toggle, both dialogs, the change-password form and its
 * failure arm -- is in [SettingsScreenTest] and runs in CI.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenNavigationTest : BaseScreenTest() {

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
    fun confirmingLogout_signsOut_andNavigatesToAuth() {
        launchSettingsScreen()

        composeTestRule.onNodeWithText(string(R.string.log_out)).performClick()
        composeTestRule.waitForIdle()

        // The confirm button is the dialog's clickable node carrying the label; the list row also
        // carries the label and a click action, so scope the match to inside the dialog.
        composeTestRule.onNode(
            hasText(string(R.string.log_out)) and hasClickAction() and hasAnyAncestor(isDialog())
        ).performClick()

        composeTestRule.waitUntil(timeoutMillis = UI_TEST_TIMEOUT_MS) { navigatedToAuth }
        assertEquals(1, authRepository.signOutCallCount)
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

        composeTestRule.waitUntil(timeoutMillis = UI_TEST_TIMEOUT_MS) { navigatedToAuth }
        assertEquals(1, authRepository.deleteAccountCallCount)
    }
}