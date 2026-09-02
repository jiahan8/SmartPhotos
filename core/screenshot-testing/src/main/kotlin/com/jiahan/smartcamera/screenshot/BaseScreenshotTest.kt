package com.jiahan.smartcamera.screenshot

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Shared setup for Roborazzi screenshot tests, rendered on the JVM via Robolectric (no emulator).
 * A fixed device profile (Pixel 5 qualifiers) keeps captures deterministic across machines.
 *
 * Four modules subclass this -- :core:ui, :feature:home, :feature:search and :feature:settings --
 * each keeping its own goldens under its own `src/test/screenshots/`. The tasks are per-module, so
 * run them unqualified to cover all four, or name one while iterating:
 *
 * Record references: ./gradlew recordRoborazziDebug
 * Verify:            ./gradlew verifyRoborazziDebug
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel5
)
abstract class BaseScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    protected fun capture(content: @Composable () -> Unit) {
        composeRule.setContent { content() }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage()
    }
}