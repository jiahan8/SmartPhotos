package com.jiahan.smartcamera.explore

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jiahan.smartcamera.domain.Photo
import com.jiahan.smartcamera.fake.FakeAnalyticsRepository
import com.jiahan.smartcamera.fake.FakeErrorHandler
import com.jiahan.smartcamera.fake.FakePhotoRepository
import com.jiahan.smartcamera.feature.explore.R
import com.jiahan.smartcamera.core.ui.R as UiR
import com.jiahan.smartcamera.ui.theme.SmartPhotosTheme
import com.jiahan.smartcamera.uitest.BaseScreenTest
import com.jiahan.smartcamera.uitest.UI_TEST_TIMEOUT_MS
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for [ExploreScreen], the last screen in the app with no UI suite of its own.
 *
 * A real [ExploreViewModel] is driven by [FakePhotoRepository], so this covers the three
 * [ExploreContent] branches plus the empty-results arm and the search toggle, with no Unsplash
 * call. Photo URLs are left blank, so Coil resolves nothing and performs no network I/O; the
 * assertions are on the username and the copy around the list rather than on any image.
 *
 * Lives in `sharedTest`, so it runs under Robolectric in CI and on-device under the instrumentation
 * runner -- the :feature:auth arrangement. This is the module the feature convention describes as
 * "a feature with no sharedTest/ directory pays a source set that resolves to nothing"; it now
 * resolves to something.
 */
@RunWith(AndroidJUnit4::class)
class ExploreScreenTest : BaseScreenTest() {

    private val photoRepository = FakePhotoRepository()
    private var previewedPhotoUrl: String? = null

    private fun photo(id: String, username: String) = Photo(
        id = id,
        photoUrl = "",
        thumbnailUrl = "",
        width = 100,
        height = 100,
        username = username,
    )

    private fun launchExploreScreen() {
        val viewModel = ExploreViewModel(
            photoRepository = photoRepository,
            analyticsRepository = FakeAnalyticsRepository(),
            errorHandler = FakeErrorHandler(),
        )
        composeTestRule.setContent {
            SmartPhotosTheme {
                ExploreScreen(
                    onBack = {},
                    onNavigateToPhotoPreview = { previewedPhotoUrl = it },
                    viewModel = viewModel,
                )
            }
        }
    }

    @Test
    fun photos_areRendered() {
        photoRepository.setPhotos(listOf(photo("p1", "ansel")))
        launchExploreScreen()

        waitForText("ansel")
        composeTestRule.onNodeWithText("ansel").assertIsDisplayed()
    }

    @Test
    fun emptyFeed_showsNoPhotosFound() {
        photoRepository.setPhotos(emptyList())
        launchExploreScreen()

        waitForText(string(R.string.no_photos_found))
        composeTestRule.onNodeWithText(string(R.string.no_photos_found)).assertIsDisplayed()
    }

    /**
     * The failure arm renders the failure's message. An exception with text of its own reaches the
     * screen as `ErrorMessage.Unlocalized`, which the screen shows verbatim.
     */
    @Test
    fun repositoryFailure_showsErrorMessage() {
        photoRepository.listResult = Result.failure(RuntimeException("Something went wrong"))
        launchExploreScreen()

        waitForText("Something went wrong")
        composeTestRule.onNodeWithText("Something went wrong").assertIsDisplayed()
    }

    @Test
    fun tappingPhoto_navigatesToPhotoPreview() {
        photoRepository.setPhotos(listOf(photo("p1", "ansel")))
        launchExploreScreen()
        waitForText("ansel")

        composeTestRule.onNodeWithContentDescription(string(R.string.photo)).performClick()

        composeTestRule.waitUntil(timeoutMillis = UI_TEST_TIMEOUT_MS) { previewedPhotoUrl != null }
        assertEquals("", previewedPhotoUrl)
    }

    /**
     * Opening search swaps the title for the query field. The browse feed is deliberately left in
     * place behind it -- `toggleSearch()` keeps the pagination state so reopening restores it --
     * so the assertion is on the field appearing, not on the list emptying.
     */
    @Test
    fun openingSearch_showsSearchField() {
        photoRepository.setPhotos(listOf(photo("p1", "ansel")))
        launchExploreScreen()
        waitForText("ansel")

        composeTestRule.onNodeWithContentDescription(string(UiR.string.search)).performClick()

        waitForText(string(R.string.search_photos))
        composeTestRule.onNodeWithText(string(R.string.search_photos)).assertIsDisplayed()
    }
}