package com.jiahan.smartcamera.screenshot

import androidx.compose.runtime.Composable
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.fake.FakeAnalyticsRepository
import com.jiahan.smartcamera.fake.FakeAuthRepository
import com.jiahan.smartcamera.fake.FakeErrorHandler
import com.jiahan.smartcamera.fake.FakeNoteRepository
import com.jiahan.smartcamera.fake.FakeUserPreferencesRepository
import com.jiahan.smartcamera.home.HomeScreen
import com.jiahan.smartcamera.home.HomeViewModel
import com.jiahan.smartcamera.note.NoteActionsDelegate
import com.jiahan.smartcamera.note.NoteHandler
import com.jiahan.smartcamera.search.SearchScreen
import com.jiahan.smartcamera.search.SearchViewModel
import com.jiahan.smartcamera.settings.SettingsScreen
import com.jiahan.smartcamera.settings.SettingsViewModel
import com.jiahan.smartcamera.ui.theme.SmartCameraTheme
import org.junit.Test
import java.time.Instant

/**
 * Full-screen Roborazzi screenshot tests. Each screen is driven by its real ViewModel wired to
 * in-memory fakes, then captured after the state settles. Only states that settle synchronously
 * (no `debounce`/`delay`) are captured so the images are deterministic; notes carry no remote
 * image URLs, so Coil never performs I/O.
 */
class ScreenScreenshotTest : BaseScreenshotTest() {

    private fun captureSettled(content: @Composable () -> Unit) {
        capture { SmartCameraTheme { content() } }
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
        val noteHandler = NoteHandler()
        val errorHandler = FakeErrorHandler()
        return HomeViewModel(
            repo,
            noteHandler,
            NoteActionsDelegate(repo, noteHandler, errorHandler),
            errorHandler,
        )
    }

    @Test
    fun homeScreen_empty() {
        captureSettled {
            HomeScreen(
                onNavigateToNotePreview = {},
                onNavigateToPhotoPreview = {},
                onNavigateToVideoPreview = {},
                onNavigateToExplore = {},
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
                onNavigateToExplore = {},
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
                onNavigateToExplore = {},
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
        val noteRepository = FakeNoteRepository()
        val noteHandler = NoteHandler()
        val errorHandler = FakeErrorHandler()
        val viewModel = SearchViewModel(
            noteRepository = noteRepository,
            analyticsRepository = FakeAnalyticsRepository(),
            noteHandler = noteHandler,
            noteActions = NoteActionsDelegate(noteRepository, noteHandler, errorHandler),
            errorHandler = errorHandler,
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