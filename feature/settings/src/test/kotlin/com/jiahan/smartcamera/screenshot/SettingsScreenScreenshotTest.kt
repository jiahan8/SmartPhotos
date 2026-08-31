package com.jiahan.smartcamera.screenshot

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import com.jiahan.smartcamera.fake.FakeAnalyticsRepository
import com.jiahan.smartcamera.fake.FakeAuthRepository
import com.jiahan.smartcamera.fake.FakeErrorHandler
import com.jiahan.smartcamera.fake.FakeResourceProvider
import com.jiahan.smartcamera.fake.FakeUserPreferencesRepository
import com.jiahan.smartcamera.settings.SettingsScreen
import com.jiahan.smartcamera.settings.SettingsViewModel
import com.jiahan.smartcamera.ui.theme.SmartCameraTheme
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

    @Test
    fun settingsScreen_default() {
        val viewModel = SettingsViewModel(
            authRepository = FakeAuthRepository(),
            analyticsRepository = FakeAnalyticsRepository(),
            userPreferencesRepository = FakeUserPreferencesRepository(),
            resourceProvider = FakeResourceProvider(RuntimeEnvironment.getApplication()),
            errorHandler = FakeErrorHandler(),
        )
        capture {
            SmartCameraTheme {
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