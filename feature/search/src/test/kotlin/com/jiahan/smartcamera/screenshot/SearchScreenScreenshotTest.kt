package com.jiahan.smartcamera.screenshot

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import com.jiahan.smartcamera.fake.FakeAnalyticsRepository
import com.jiahan.smartcamera.fake.FakeErrorHandler
import com.jiahan.smartcamera.fake.FakeMediaFileRepository
import com.jiahan.smartcamera.fake.FakeNoteRepository
import com.jiahan.smartcamera.fake.FakeResourceProvider
import com.jiahan.smartcamera.note.NoteErrorReporter
import com.jiahan.smartcamera.note.NoteShareDelegate
import com.jiahan.smartcamera.search.SearchScreen
import com.jiahan.smartcamera.search.SearchViewModel
import com.jiahan.smartcamera.ui.theme.SmartCameraTheme
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
 */
class SearchScreenScreenshotTest : BaseScreenshotTest() {

    @Test
    fun searchScreen_idle() {
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
                FakeResourceProvider(RuntimeEnvironment.getApplication())
            ),
            errorHandler = errorHandler,
        )
        capture {
            SmartCameraTheme {
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
}