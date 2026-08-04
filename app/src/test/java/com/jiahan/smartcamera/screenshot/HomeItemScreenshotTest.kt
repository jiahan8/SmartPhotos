package com.jiahan.smartcamera.screenshot

import com.jiahan.smartcamera.domain.DetectedLabel
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.domain.MediaDetail
import com.jiahan.smartcamera.home.HomeItem
import com.jiahan.smartcamera.ui.theme.SmartCameraTheme
import org.junit.Test
import java.time.Instant

/**
 * Roborazzi screenshot tests for [HomeItem]. Each test captures a PNG that is diffed against a
 * checked-in reference under `src/test/screenshots`, catching visual/layout regressions that
 * semantic (text) assertions cannot.
 *
 * Deterministic by construction: no remote image URLs (the account-circle fallback is drawn
 * instead of a network image), so Coil never performs I/O during rendering.
 */
class HomeItemScreenshotTest : BaseScreenshotTest() {

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