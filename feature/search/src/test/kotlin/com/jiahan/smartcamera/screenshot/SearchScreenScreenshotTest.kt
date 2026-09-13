package com.jiahan.smartcamera.screenshot

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import com.jiahan.smartcamera.fake.FakeAnalyticsRepository
import com.jiahan.smartcamera.fake.FakeErrorHandler
import com.jiahan.smartcamera.fake.FakeMediaFileRepository
import com.jiahan.smartcamera.fake.FakeNoteRepository
import com.jiahan.smartcamera.note.NoteErrorReporter
import com.jiahan.smartcamera.note.NoteShareDelegate
import com.jiahan.smartcamera.search.SearchScreen
import com.jiahan.smartcamera.search.SearchViewModel
import com.jiahan.smartcamera.ui.theme.SmartPhotosTheme
import org.junit.Test
import org.robolectric.RuntimeEnvironment

/**
 * Roborazzi screenshot test for [SearchScreen] in its idle state, driven by the real
 * [SearchViewModel] wired to in-memory fakes.
 *
 * Idle is the only state captured, and deliberately so: every populated state on this screen is
 * reached through the query `debounce`, and a capture that has to wait on virtual time is the kind
 * of golden that passes locally and flakes on CI. See the note on `HomeScreenScreenshotTest` for
 * where this file came from.
 *
 * Both themes, though -- Idle still draws a search bar, a prompt and a background, which is where
 * a colour regression would land whether or not the list below it has anything in it.
 */
class SearchScreenScreenshotTest : BaseScreenshotTest() {

    private fun captureSearch(darkTheme: Boolean) {
        val noteRepository = FakeNoteRepository()
        val errorHandler = FakeErrorHandler()
        val noteErrorReporter = NoteErrorReporter(errorHandler)
        val viewModel = SearchViewModel(
            noteRepository = noteRepository,
            analyticsRepository = FakeAnalyticsRepository(),
            noteErrorReporter = noteErrorReporter,
            noteShare = NoteShareDelegate(
                FakeMediaFileRepository(),
                noteErrorReporter,
            ),
            errorHandler = errorHandler,
        )
        capture {
            SmartPhotosTheme(darkTheme = darkTheme) {
                // The background the app actually paints: SmartPhotosApp wraps the nav host in
                // `Surface(color = colorScheme.background)` and no feature screen paints its own.
                // Without it a capture lands on the host's default light ground -- which flatters
                // a light golden and makes a dark one plainly wrong: dark app bar, light body.
                Surface(color = MaterialTheme.colorScheme.background) {
                    SearchScreen(
                        onNavigateToNotePreview = {},
                        onNavigateToEditNote = {},
                        onNavigateToPhotoPreview = {},
                        onNavigateToVideoPreview = {},
                        viewModel = viewModel,
                        scrollToTopRequestedAt = null,
                        onScrollToTopConsumed = {},
                        snackbarHostState = remember { SnackbarHostState() },
                    )
                }
            }
        }
    }

    @Test
    fun searchScreen_idle() = captureSearch(darkTheme = false)

    @Test
    fun searchScreen_idle_dark() = captureSearch(darkTheme = true)
}