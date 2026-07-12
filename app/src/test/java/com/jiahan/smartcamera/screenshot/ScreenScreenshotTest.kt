package com.jiahan.smartcamera.screenshot

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.fake.FakeAnalyticsRepository
import com.jiahan.smartcamera.fake.FakeAuthRepository
import com.jiahan.smartcamera.fake.FakeErrorHandler
import com.jiahan.smartcamera.fake.FakeNoteRepository
import com.jiahan.smartcamera.fake.FakeUserPreferencesRepository
import com.jiahan.smartcamera.home.HomeScreen
import com.jiahan.smartcamera.home.HomeViewModel
import com.jiahan.smartcamera.note.NoteHandler
import com.jiahan.smartcamera.search.SearchScreen
import com.jiahan.smartcamera.search.SearchViewModel
import com.jiahan.smartcamera.settings.SettingsScreen
import com.jiahan.smartcamera.settings.SettingsViewModel
import com.jiahan.smartcamera.ui.theme.SmartCameraTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/**
 * Full-screen Roborazzi screenshot tests, rendered on the JVM via Robolectric (no emulator).
 *
 * Each screen is driven by its real ViewModel wired to in-memory fakes, then captured after the
 * state settles. Only states that settle synchronously (no `debounce`/`delay`) are captured so the
 * images are deterministic; notes carry no remote image URLs, so Coil never performs I/O.
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
class ScreenScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun captureSettled(content: @Composable () -> Unit) {
        composeRule.setContent { SmartCameraTheme { content() } }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage()
    }

    private fun note(documentPath: String, text: String, favorite: Boolean = false) = HomeNote(
        text = text,
        documentPath = documentPath,
        username = "tester",
        favorite = favorite,
        createdDate = Instant.ofEpochMilli(1_700_000_000_000L),
    )

    private fun homeViewModel(notes: Result<List<HomeNote>>): HomeViewModel {
        val repo = FakeNoteRepository().apply { notesResult = notes }
        return HomeViewModel(repo, NoteHandler(), FakeErrorHandler())
    }

    @Test
    fun homeScreen_empty() {
        captureSettled {
            HomeScreen(
                onNavigateToNotePreview = {},
                onNavigateToPhotoPreview = {},
                onNavigateToVideoPreview = {},
                viewModel = homeViewModel(Result.success(emptyList())),
                scrollToTop = null,
                onScrollToTopConsumed = {},
            )
        }
    }

    @Test
    fun homeScreen_success() {
        val notes = listOf(
            note("doc1", "First note in the feed."),
            note("doc2", "A second note, marked as a favourite.", favorite = true),
        )
        captureSettled {
            HomeScreen(
                onNavigateToNotePreview = {},
                onNavigateToPhotoPreview = {},
                onNavigateToVideoPreview = {},
                viewModel = homeViewModel(Result.success(notes)),
                scrollToTop = null,
                onScrollToTopConsumed = {},
            )
        }
    }

    @Test
    fun homeScreen_error() {
        captureSettled {
            HomeScreen(
                onNavigateToNotePreview = {},
                onNavigateToPhotoPreview = {},
                onNavigateToVideoPreview = {},
                viewModel = homeViewModel(Result.failure(RuntimeException("Something went wrong"))),
                scrollToTop = null,
                onScrollToTopConsumed = {},
            )
        }
    }

    @Test
    fun settingsScreen_default() {
        val viewModel = SettingsViewModel(
            authRepository = FakeAuthRepository(),
            userPreferencesRepository = FakeUserPreferencesRepository(),
            errorHandler = FakeErrorHandler(),
        )
        captureSettled {
            SettingsScreen(onBack = {}, onNavigateToAuth = {}, viewModel = viewModel)
        }
    }

    @Test
    fun searchScreen_idle() {
        val viewModel = SearchViewModel(
            noteRepository = FakeNoteRepository(),
            analyticsRepository = FakeAnalyticsRepository(),
            noteHandler = NoteHandler(),
            errorHandler = FakeErrorHandler(),
        )
        captureSettled {
            SearchScreen(
                onNavigateToNotePreview = {},
                onNavigateToPhotoPreview = {},
                onNavigateToVideoPreview = {},
                viewModel = viewModel,
                scrollToTop = null,
                onScrollToTopConsumed = {},
            )
        }
    }
}