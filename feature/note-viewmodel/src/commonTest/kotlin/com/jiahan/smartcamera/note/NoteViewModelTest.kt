package com.jiahan.smartcamera.note

import com.jiahan.smartcamera.data.datastore.UserPreferences
import com.jiahan.smartcamera.domain.MediaDetail
import com.jiahan.smartcamera.domain.MediaUri
import com.jiahan.smartcamera.domain.NoteMediaDetail
import com.jiahan.smartcamera.fake.FakeAnalyticsRepository
import com.jiahan.smartcamera.fake.FakeErrorHandler
import com.jiahan.smartcamera.fake.FakeMediaCaptureRepository
import com.jiahan.smartcamera.fake.FakeMediaUploadRepository
import com.jiahan.smartcamera.fake.FakeNoteRepository
import com.jiahan.smartcamera.fake.FakeUserPreferencesRepository
import com.jiahan.smartcamera.util.AppConstants.MAX_NOTE_MEDIA_ITEMS
import com.jiahan.smartcamera.util.AppConstants.MAX_NOTE_TEXT_LENGTH
import com.jiahan.smartcamera.util.ErrorMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [NoteViewModel]'s suite, in `commonTest` beside its subject, on :core:domain-testing's fakes with
 * [Dispatchers.setMain] called directly -- `ExploreViewModelTest` records why each of those.
 *
 * Every media location is a [MediaUri] now, so the `Uri` mocks with a stubbed `toString`, which
 * paired each platform URI with the value the ViewModel converted it into, are gone: a test hands
 * over the value the repository receives. Where a strict mock implied that a call never happened --
 * `addNote` after a failed upload -- the fake's call count asserts it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NoteViewModelTest {

    private val noteRepository = FakeNoteRepository()
    private val mediaUploadRepository = FakeMediaUploadRepository()
    private val analyticsRepository = FakeAnalyticsRepository()

    private lateinit var viewModel: NoteViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = NoteViewModel(
            noteRepository = noteRepository,
            mediaUploadRepository = mediaUploadRepository,
            userPreferencesRepository = FakeUserPreferencesRepository(
                initial = UserPreferences(
                    isDarkTheme = false,
                    username = "user1",
                    profilePictureUrl = null
                )
            ),
            analyticsRepository = analyticsRepository,
            mediaCaptureRepository = FakeMediaCaptureRepository(),
            pendingShare = null,
            errorHandler = FakeErrorHandler()
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -------------------------------------------------------------------------
    // Initial state
    // -------------------------------------------------------------------------

    @Test
    fun `initial uploadUiState is Idle`() {
        assertTrue(viewModel.uiState.value.uploadStatus is UploadStatus.Idle)
    }

    @Test
    fun `initial noteText is empty`() {
        assertEquals("", viewModel.uiState.value.noteText)
    }

    @Test
    fun `initial saveButtonEnabled is false`() = runTest {
        assertFalse(viewModel.saveButtonEnabled.value)
    }

    // -------------------------------------------------------------------------
    // updateNoteText
    // -------------------------------------------------------------------------

    @Test
    fun `updateNoteText updates noteText state`() {
        viewModel.updateNoteText("Hello world")
        assertEquals("Hello world", viewModel.uiState.value.noteText)
    }

    @Test
    fun `updateNoteText valid text enables save button`() = runTest {
        viewModel.updateNoteText("Hello world")
        assertTrue(viewModel.saveButtonEnabled.value)
    }

    @Test
    fun `updateNoteText blank text disables save button`() = runTest {
        viewModel.updateNoteText("Hello")
        viewModel.updateNoteText("   ")
        assertFalse(viewModel.saveButtonEnabled.value)
    }

    @Test
    fun `updateNoteText exceeding max length sets isNoteTextTooLong`() {
        val longText = "a".repeat(MAX_NOTE_TEXT_LENGTH + 1)
        viewModel.updateNoteText(longText)
        assertTrue(viewModel.uiState.value.isNoteTextTooLong)
    }

    @Test
    fun `updateNoteText within max length clears isNoteTextTooLong`() {
        viewModel.updateNoteText("a".repeat(MAX_NOTE_TEXT_LENGTH + 1)) // set error
        viewModel.updateNoteText("short text")                           // clear error
        assertFalse(viewModel.uiState.value.isNoteTextTooLong)
    }

    @Test
    fun `updateNoteText logs analytics event`() {
        viewModel.updateNoteText("cat photo")
        assertEquals("cat photo", analyticsRepository.lastLoggedNoteCreate)
    }

    // -------------------------------------------------------------------------
    // removeMediaAt
    // -------------------------------------------------------------------------

    @Test
    fun `removeMediaAt valid index removes item`() = runTest {
        val mediaDetails = listOf(
            NoteMediaDetail(
                photoUri = MediaUri("content://media/1"),
                videoUri = null,
                thumbnailUri = null,
                isVideo = false
            ),
            NoteMediaDetail(
                photoUri = MediaUri("content://media/2"),
                videoUri = null,
                thumbnailUri = null,
                isVideo = false
            )
        )
        mediaUploadRepository.buildLocalMediaDetailsResult = Result.success(mediaDetails)

        viewModel.addMedia(listOf(MediaUri("content://media/1"), MediaUri("content://media/2")))
        assertEquals(2, viewModel.uiState.value.mediaList.size)

        viewModel.removeMediaAt(0)
        assertEquals(1, viewModel.uiState.value.mediaList.size)
    }

    @Test
    fun `removeMediaAt out of bounds index does nothing`() = runTest {
        viewModel.removeMediaAt(99)
        assertEquals(0, viewModel.uiState.value.mediaList.size)
    }

    @Test
    fun `removeMediaAt negative index does nothing`() = runTest {
        viewModel.removeMediaAt(-1)
        assertEquals(0, viewModel.uiState.value.mediaList.size)
    }

    /**
     * The one upload status that is not an upload outcome. It used to be an `Error` carrying the
     * resolved `note_media_limit` string, which this suite could only match against a stubbed
     * `ResourceProvider`; as its own case it is asserted as itself.
     */
    @Test
    fun `addMedia past the per-note limit keeps the first items and reports MediaLimitReached`() =
        runTest {
            val tooMany = (0..MAX_NOTE_MEDIA_ITEMS).map {
                NoteMediaDetail(
                    photoUri = MediaUri("content://media/$it"),
                    videoUri = null,
                    thumbnailUri = null,
                    isVideo = false
                )
            }
            mediaUploadRepository.buildLocalMediaDetailsResult = Result.success(tooMany)

            viewModel.addMedia(listOf(MediaUri("content://media/picked")))

            assertEquals(tooMany.take(MAX_NOTE_MEDIA_ITEMS), viewModel.uiState.value.mediaList)
            assertEquals(UploadStatus.MediaLimitReached, viewModel.uiState.value.uploadStatus)
        }

    // -------------------------------------------------------------------------
    // resetUploadStatus
    // -------------------------------------------------------------------------

    @Test
    fun `resetUploadStatus resets to Idle`() = runTest {
        mediaUploadRepository.uploadMediaResult = Result.failure(RuntimeException("upload fail"))
        viewModel.updateNoteText("hello")
        viewModel.saveNote()

        viewModel.resetUploadStatus()
        assertTrue(viewModel.uiState.value.uploadStatus is UploadStatus.Idle)
    }

    // -------------------------------------------------------------------------
    // URI management
    // -------------------------------------------------------------------------

    @Test
    fun `updatePhotoUri stores the uri`() {
        val uri = MediaUri("content://media/photo")
        viewModel.updatePhotoUri(uri)
        assertEquals(uri, viewModel.uiState.value.photoUri)
    }

    @Test
    fun `updateVideoUri stores the uri`() {
        val uri = MediaUri("content://media/video")
        viewModel.updateVideoUri(uri)
        assertEquals(uri, viewModel.uiState.value.videoUri)
    }

    @Test
    fun `cancelPhotoCapture quick-uploads uri and clears photoUri`() = runTest {
        val uri = MediaUri("content://media/photo")
        viewModel.updatePhotoUri(uri)
        viewModel.cancelPhotoCapture(uri)
        assertEquals(listOf(listOf(uri) to true), mediaUploadRepository.cacheUploads)
        assertNull(viewModel.uiState.value.photoUri)
    }

    @Test
    fun `cancelVideoCapture quick-uploads uri and clears videoUri`() = runTest {
        val uri = MediaUri("content://media/video")
        viewModel.updateVideoUri(uri)
        viewModel.cancelVideoCapture(uri)
        assertEquals(listOf(listOf(uri) to true), mediaUploadRepository.cacheUploads)
        assertNull(viewModel.uiState.value.videoUri)
    }

    // -------------------------------------------------------------------------
    // saveNote
    // -------------------------------------------------------------------------

    @Test
    fun `saveNote success emits Success state`() = runTest {
        viewModel.updateNoteText("My note")
        mediaUploadRepository.uploadMediaResult =
            Result.success(listOf(MediaDetail(photoUrl = "http://url")))
        noteRepository.addNoteResult = Result.success(Unit)

        viewModel.saveNote()

        // This used to also await a NoteHandler emission. addNote reads the created note back into
        // the `notes` table now, so the feeds see it as a row -- there is no event to assert.
        assertTrue(viewModel.uiState.value.uploadStatus is UploadStatus.Success)
        assertEquals(1, noteRepository.addNoteCallCount)
    }

    @Test
    fun `saveNote failure on media upload sets Error state`() = runTest {
        viewModel.updateNoteText("My note")
        mediaUploadRepository.uploadMediaResult = Result.failure(RuntimeException("upload fail"))

        viewModel.saveNote()

        val state = viewModel.uiState.value.uploadStatus
        assertTrue(state is UploadStatus.Error)
        assertEquals(ErrorMessage.Unlocalized("upload fail"), state.message)
        assertEquals(0, noteRepository.addNoteCallCount)
    }
}