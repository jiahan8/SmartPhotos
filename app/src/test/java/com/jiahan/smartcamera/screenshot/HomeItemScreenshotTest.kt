package com.jiahan.smartcamera.screenshot

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.jiahan.smartcamera.domain.DetectedLabel
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.domain.MediaDetail
import com.jiahan.smartcamera.home.HomeItem
import com.jiahan.smartcamera.ui.theme.SmartCameraTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/**
 * Roborazzi screenshot tests for [HomeItem], rendered on the JVM via Robolectric — no emulator
 * required. Each test captures a PNG that is diffed against a checked-in reference under
 * `src/test/screenshots`, catching visual/layout regressions that semantic (text) assertions cannot.
 *
 * Deterministic by construction: a fixed device profile (Pixel 5 qualifiers), no remote image URLs
 * (the account-circle fallback is drawn instead of a network image), and a plain [Application] so no
 * Hilt/Firebase initialization runs during rendering.
 *
 * Record references: ./gradlew :app:recordRoborazziDebug
 * Verify:            ./gradlew :app:verifyRoborazziDebug
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel5
)
class HomeItemScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(content: @Composable () -> Unit) {
        composeRule.setContent { content() }
        composeRule.onRoot().captureRoboImage()
    }

    @Test
    fun homeItem_textOnly_light() {
        capture {
            SmartCameraTheme(darkTheme = false) {
                HomeItem(
                    sampleNote,
                    {},
                    {},
                    {},
                    {},
                    {},
                    {})
            }
        }
    }

    @Test
    fun homeItem_textOnly_dark() {
        capture {
            SmartCameraTheme(darkTheme = true) {
                HomeItem(
                    sampleNote,
                    {},
                    {},
                    {},
                    {},
                    {},
                    {})
            }
        }
    }

    @Test
    fun homeItem_favorited_light() {
        capture {
            SmartCameraTheme(darkTheme = false) {
                HomeItem(
                    favoritedNote,
                    {},
                    {},
                    {},
                    {},
                    {},
                    {})
            }
        }
    }

    @Test
    fun homeItem_withMediaThumbnail_light() {
        capture {
            SmartCameraTheme(darkTheme = false) {
                HomeItem(
                    noteWithMedia,
                    {},
                    {},
                    {},
                    {},
                    {},
                    {})
            }
        }
    }

    private companion object {
        val sampleNote = HomeNote(
            documentPath = "preview/1",
            username = "john_doe",
            text = "Hello, this is a preview note with some sample text that wraps across multiple lines.",
            mediaList = null,
            profilePictureUrl = null,
            favorite = false,
            createdDate = Instant.ofEpochMilli(1_700_000_000_000L),
        )

        val favoritedNote = sampleNote.copy(
            documentPath = "preview/2",
            username = "jane_doe",
            text = "This note is marked as a favourite.",
            favorite = true,
        )

        val noteWithMedia = sampleNote.copy(
            documentPath = "preview/3",
            text = "A note with an attached media thumbnail.",
            mediaList = listOf(
                MediaDetail(
                    photoUrl = "",
                    generatedText = listOf("a cat on a sofa"),
                    generatedLabels = listOf(DetectedLabel("Cat", 0.98)),
                )
            ),
        )
    }
}