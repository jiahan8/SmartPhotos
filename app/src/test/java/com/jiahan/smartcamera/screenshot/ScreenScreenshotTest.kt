package com.jiahan.smartcamera.screenshot

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.fake.FakeAnalyticsRepository
import com.jiahan.smartcamera.fake.FakeAuthRepository
import com.jiahan.smartcamera.fake.FakeErrorHandler
import com.jiahan.smartcamera.fake.FakeMediaFileRepository
import com.jiahan.smartcamera.fake.FakeNoteRepository
import com.jiahan.smartcamera.fake.FakeRemoteConfigRepository
import com.jiahan.smartcamera.fake.FakeResourceProvider
import com.jiahan.smartcamera.fake.FakeUserPreferencesRepository
import com.jiahan.smartcamera.home.HomeScreen
import com.jiahan.smartcamera.home.HomeViewModel
import com.jiahan.smartcamera.note.NoteActionsDelegate
import com.jiahan.smartcamera.note.NoteErrorReporter
import com.jiahan.smartcamera.note.NoteHandler
import com.jiahan.smartcamera.note.NoteShareDelegate
import com.jiahan.smartcamera.search.SearchScreen
import com.jiahan.smartcamera.search.SearchViewModel
import com.jiahan.smartcamera.settings.SettingsScreen
import com.jiahan.smartcamera.settings.SettingsViewModel
import com.jiahan.smartcamera.ui.theme.SmartCameraTheme
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import kotlin.time.Instant

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

    private fun note(noteId: String, text: String, favorite: Boolean = false) = HomeNote(
        noteId = noteId,
        text = text,
        username = "tester",
        favorite = favorite,
        createdDate = Instant.fromEpochMilliseconds(1_700_000_000_000L),
    )

    private fun homeViewModel(notes: Result<List<HomeNote>>): HomeViewModel {
        val repo = FakeNoteRepository().apply { notesResult = notes }
        val noteHandler = NoteHandler()
        val errorHandler = FakeErrorHandler()
        val noteErrorReporter = NoteErrorReporter(errorHandler)
        val noteActions = NoteActionsDelegate(repo, noteHandler, noteErrorReporter)
        return HomeViewModel(
            repo,
            noteHandler,
            noteActions,
            NoteShareDelegate(
                FakeMediaFileRepository(),
                noteErrorReporter,
                FakeResourceProvider(RuntimeEnvironment.getApplication())
            ),
            errorHandler,
            FakeRemoteConfigRepository(),
        )
    }

    @Test
    fun homeScreen_empty() {
        captureSettled {
            HomeScreen(
                onNavigateToNotePreview = {},
                onNavigateToEditNote = {},
                onNavigateToPhotoPreview = {},
                onNavigateToVideoPreview = {},
                onNavigateToExplore = {},
                viewModel = homeViewModel(Result.success(emptyList())),
                scrollToTop = null,
                onScrollToTopConsumed = {},
                snackbarHostState = remember { SnackbarHostState() },
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
                onNavigateToEditNote = {},
                onNavigateToPhotoPreview = {},
                onNavigateToVideoPreview = {},
                onNavigateToExplore = {},
                viewModel = homeViewModel(Result.success(notes)),
                scrollToTop = null,
                onScrollToTopConsumed = {},
                snackbarHostState = remember { SnackbarHostState() },
            )
        }
    }

    @Test
    fun homeScreen_error() {
        captureSettled {
            HomeScreen(
                onNavigateToNotePreview = {},
                onNavigateToEditNote = {},
                onNavigateToPhotoPreview = {},
                onNavigateToVideoPreview = {},
                onNavigateToExplore = {},
                viewModel = homeViewModel(Result.failure(RuntimeException("Something went wrong"))),
                scrollToTop = null,
                onScrollToTopConsumed = {},
                snackbarHostState = remember { SnackbarHostState() },
            )
        }
    }

    @Test
    fun settingsScreen_default() {
        val viewModel = SettingsViewModel(
            authRepository = FakeAuthRepository(),
            analyticsRepository = FakeAnalyticsRepository(),
            userPreferencesRepository = FakeUserPreferencesRepository(),
            resourceProvider = FakeResourceProvider(RuntimeEnvironment.getApplication()),
            errorHandler = FakeErrorHandler(),
        )
        captureSettled {
            SettingsScreen(
                onBack = {},
                onNavigateToAuth = {},
                viewModel = viewModel,
                snackbarHostState = remember { SnackbarHostState() },
            )
        }
    }

    @Test
    fun searchScreen_idle() {
        val noteRepository = FakeNoteRepository()
        val noteHandler = NoteHandler()
        val errorHandler = FakeErrorHandler()
        val noteErrorReporter = NoteErrorReporter(errorHandler)
        val noteActions = NoteActionsDelegate(noteRepository, noteHandler, noteErrorReporter)
        val viewModel = SearchViewModel(
            noteRepository = noteRepository,
            analyticsRepository = FakeAnalyticsRepository(),
            noteHandler = noteHandler,
            noteActions = noteActions,
            noteShare = NoteShareDelegate(
                FakeMediaFileRepository(),
                noteErrorReporter,
                FakeResourceProvider(RuntimeEnvironment.getApplication())
            ),
            errorHandler = errorHandler,
        )
        captureSettled {
            SearchScreen(
                onNavigateToNotePreview = {},
                onNavigateToEditNote = {},
                onNavigateToPhotoPreview = {},
                onNavigateToVideoPreview = {},
                viewModel = viewModel,
                scrollToTop = null,
                onScrollToTopConsumed = {},
                snackbarHostState = remember { SnackbarHostState() },
            )
        }
    }
}