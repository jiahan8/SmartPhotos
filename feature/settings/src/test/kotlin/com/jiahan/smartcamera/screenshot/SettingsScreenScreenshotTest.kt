package com.jiahan.smartcamera.screenshot

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import com.jiahan.smartcamera.fake.FakeAnalyticsRepository
import com.jiahan.smartcamera.fake.FakeAuthRepository
import com.jiahan.smartcamera.fake.FakeErrorHandler
import com.jiahan.smartcamera.fake.FakeUserPreferencesRepository
import com.jiahan.smartcamera.settings.SettingsScreen
import com.jiahan.smartcamera.settings.SettingsViewModel
import com.jiahan.smartcamera.ui.theme.SmartPhotosTheme
import org.junit.Test
import org.robolectric.RuntimeEnvironment

/**
 * Came out of :app's ScreenScreenshotTest when this module was extracted, on the rule the
 * NoteItem goldens already follow: a screenshot lives beside the composable it captures, with its
 * PNG under this module's own `src/test/screenshots`. Leaving it in :app would have meant :app's
 * test sources compiling against a feature module's screen -- exactly the coupling the extraction
 * removes from the main source set.
 *
 * [SettingsScreen] now takes `versionName` as a parameter rather than reading :app's
 * `BuildConfig.VERSION_NAME`, so this pins a fixed string. That is what stops the golden going
 * stale on every version bump in `app/build.gradle.kts` -- it used to need re-recording alongside
 * one.
 */
class SettingsScreenScreenshotTest : BaseScreenshotTest() {

    /**
     * Captures the screen after [prepare] has put the ViewModel in the state under test. The
     * dialogs are driven through the real `show*Dialog()` entry points rather than by handing the
     * screen a state object, so a dialog that stops opening fails these goldens too.
     */
    private fun captureSettings(
        darkTheme: Boolean,
        prepare: SettingsViewModel.() -> Unit = {},
    ) {
        val viewModel = SettingsViewModel(
            authRepository = FakeAuthRepository(),
            analyticsRepository = FakeAnalyticsRepository(),
            userPreferencesRepository = FakeUserPreferencesRepository(),
            errorHandler = FakeErrorHandler(),
        ).apply(prepare)
        capture {
            SmartPhotosTheme(darkTheme = darkTheme) {
                // The background the app actually paints: SmartPhotosApp wraps the nav host in
                // `Surface(color = colorScheme.background)` and no feature screen paints its own.
                // Without it a capture lands on the host's default light ground -- which flatters
                // a light golden and makes a dark one plainly wrong: dark app bar, light body.
                Surface(color = MaterialTheme.colorScheme.background) {
                    SettingsScreen(
                        onBack = {},
                        onNavigateToAuth = {},
                        versionName = "1.0.0",
                        viewModel = viewModel,
                        snackbarHostState = remember { SnackbarHostState() },
                    )
                }
            }
        }
    }

    @Test
    fun settingsScreen_default() = captureSettings(darkTheme = false)

    /**
     * The dark capture is the load-bearing one on this screen: it is the only golden that renders
     * the theme toggle in the state the toggle itself produces.
     */
    @Test
    fun settingsScreen_default_dark() = captureSettings(darkTheme = true)

    // -------------------------------------------------------------------------
    // The three dialogs. Two of them confirm a destructive action, and the copy
    // distinguishing "log out" from "delete account" is the whole safeguard.
    // -------------------------------------------------------------------------

    @Test
    fun settingsScreen_logoutDialog_light() =
        captureSettings(darkTheme = false, prepare = SettingsViewModel::showLogoutDialog)

    @Test
    fun settingsScreen_logoutDialog_dark() =
        captureSettings(darkTheme = true, prepare = SettingsViewModel::showLogoutDialog)

    @Test
    fun settingsScreen_deleteAccountDialog_light() =
        captureSettings(darkTheme = false, prepare = SettingsViewModel::showDeleteAccountDialog)

    @Test
    fun settingsScreen_deleteAccountDialog_dark() =
        captureSettings(darkTheme = true, prepare = SettingsViewModel::showDeleteAccountDialog)

    /*
     * The third dialog, ChangePassword, has no golden and cannot get one here.
     *
     * A Compose text field whose width is not fixed never reports itself idle inside a dialog
     * window under Robolectric: `capture` spins ~600,000 recompositions and dies on Espresso's
     * `AppNotIdleException` after 60s. Bisected -- it is the pair, not either half. A dialog of
     * plain `Text` idles (the two goldens above), text fields outside a dialog idle (`:core:ui`'s
     * PasswordField goldens), and inside a dialog a field pinned to `Modifier.width(240.dp)` idles
     * while `fillMaxWidth()`, no modifier, filled `TextField` and a raw `Dialog` all hang. The
     * dialog passes `Modifier.fillMaxWidth()`, so it lands on the failing side.
     *
     * Pausing the test clock does not help -- it is a recomposition loop, not an animation -- and
     * every Roborazzi entry point syncs first, `captureScreenRoboImage` included, so there is no
     * capture path around it. Nothing is wrong with the screen: it behaves on a device, which is
     * where `SettingsScreenNavigationTest` exercises it.
     *
     * What is uncovered is the dialog chrome only. The three fields it stacks are `PasswordField`,
     * whose hidden, visible and error states all have goldens in `:core:ui`. Revisit when Compose
     * or Robolectric moves; the check is to add the test back and see whether it idles.
     */
}