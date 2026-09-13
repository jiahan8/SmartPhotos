package com.jiahan.smartcamera.screenshot

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.jiahan.smartcamera.domain.Note
import com.jiahan.smartcamera.domain.NotePage
import com.jiahan.smartcamera.fake.FakeErrorHandler
import com.jiahan.smartcamera.fake.FakeMediaFileRepository
import com.jiahan.smartcamera.fake.FakeNoteRepository
import com.jiahan.smartcamera.fake.FakeRemoteConfigRepository
import com.jiahan.smartcamera.home.HomeScreen
import com.jiahan.smartcamera.home.HomeViewModel
import com.jiahan.smartcamera.note.NoteErrorReporter
import com.jiahan.smartcamera.note.NoteShareDelegate
import com.jiahan.smartcamera.ui.theme.SmartPhotosTheme
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import kotlin.time.Instant

/**
 * Full-screen Roborazzi screenshot tests for [HomeScreen], driven by the real [HomeViewModel] wired
 * to in-memory fakes and captured once the state settles.
 *
 * Only states that settle synchronously (no `debounce`/`delay`) are captured, so the images are
 * deterministic; the notes carry no remote image URLs, so Coil never performs I/O.
 *
 * Came over from :app's `ScreenScreenshotTest`, which captured this screen and Search together
 * because both screens lived there. Splitting it put each half in the module that owns its screen
 * -- the same move `NoteItemScreenshotTest` made into :core:ui and `SettingsScreenScreenshotTest`
 * into :feature:settings -- and left :app with no screenshot tests at all.
 */
class HomeScreenScreenshotTest : BaseScreenshotTest() {

    private fun captureSettled(darkTheme: Boolean, content: @Composable () -> Unit) {
        capture {
            SmartPhotosTheme(darkTheme = darkTheme) {
                // The background the app actually paints: SmartPhotosApp wraps the nav host in
                // `Surface(color = colorScheme.background)` and no feature screen paints its own.
                // Without it a capture lands on the host's default light ground -- which flatters
                // a light golden and makes a dark one plainly wrong: dark app bar, light body.
                Surface(color = MaterialTheme.colorScheme.background) { content() }
            }
        }
    }

    /**
     * One screen, one state, both themes. Dark is where a hardcoded colour or a token read from
     * the wrong scheme shows; the light capture cannot see either.
     */
    private fun captureHome(darkTheme: Boolean, notes: Result<NotePage>) {
        captureSettled(darkTheme) {
            HomeScreen(
                // The literal app_name renders, so the goldens are unchanged by the hoist.
                title = "Smart Photos",
                onNavigateToNotePreview = {},
                onNavigateToEditNote = {},
                onNavigateToPhotoPreview = {},
                onNavigateToVideoPreview = {},
                onNavigateToExplore = {},
                viewModel = homeViewModel(notes),
                scrollToTopRequestedAt = null,
                onScrollToTopConsumed = {},
                snackbarHostState = remember { SnackbarHostState() },
            )
        }
    }

    private val successNotes
        get() = listOf(
            note("doc1", "First note in the feed."),
            note("doc2", "A second note, marked as a favourite.", isFavorite = true),
        )

    private fun note(noteId: String, text: String, isFavorite: Boolean = false) = Note(
        noteId = noteId,
        text = text,
        username = "tester",
        isFavorite = isFavorite,
        createdDate = Instant.fromEpochMilliseconds(1_700_000_000_000L),
    )

    private fun homeViewModel(notes: Result<NotePage>): HomeViewModel {
        val repo = FakeNoteRepository().apply { notesResult = notes }
        val errorHandler = FakeErrorHandler()
        val noteErrorReporter = NoteErrorReporter(errorHandler)
        return HomeViewModel(
            repo,
            noteErrorReporter,
            NoteShareDelegate(
                FakeMediaFileRepository(),
                noteErrorReporter,
            ),
            errorHandler,
            FakeRemoteConfigRepository(),
        )
    }

    @Test
    fun homeScreen_empty() = captureHome(darkTheme = false, notes = emptyPage)

    @Test
    fun homeScreen_empty_dark() = captureHome(darkTheme = true, notes = emptyPage)

    @Test
    fun homeScreen_success() =
        captureHome(darkTheme = false, notes = Result.success(NotePage(successNotes)))

    @Test
    fun homeScreen_success_dark() =
        captureHome(darkTheme = true, notes = Result.success(NotePage(successNotes)))

    @Test
    fun homeScreen_error() = captureHome(darkTheme = false, notes = failedPage)

    @Test
    fun homeScreen_error_dark() = captureHome(darkTheme = true, notes = failedPage)

    private companion object {
        val emptyPage: Result<NotePage> = Result.success(NotePage(emptyList()))
        val failedPage: Result<NotePage> =
            Result.failure(RuntimeException("Something went wrong"))
    }
}