package com.jiahan.smartcamera.note

import android.net.Uri
import app.cash.turbine.test
import com.jiahan.smartcamera.MainDispatcherRule
import com.jiahan.smartcamera.data.datastore.UserPreferences
import com.jiahan.smartcamera.data.datastore.UserPreferencesRepository
import com.jiahan.smartcamera.data.repository.AnalyticsRepository
import com.jiahan.smartcamera.data.repository.MediaFileRepository
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.domain.MediaDetail
import com.jiahan.smartcamera.util.AppConstants.MAX_POST_TEXT_LENGTH
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.ResourceProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class NoteViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val noteRepository: NoteRepository = mockk()
    private val userPreferencesRepository: UserPreferencesRepository = mockk()
    private val analyticsRepository: AnalyticsRepository = mockk()
    private val noteHandler = NoteHandler()
    private val mediaFileRepository: MediaFileRepository = mockk()
    private val resourceProvider: ResourceProvider = mockk()
    private val errorHandler: ErrorHandler = mockk()

    private lateinit var viewModel: NoteViewModel

    @Before
    fun setUp() {
        every { analyticsRepository.logNoteCustomEvent(any()) } just runs
        every { errorHandler.logError(any()) } just runs
        every { errorHandler.getErrorMessage(any()) } returns "Error"
        every { resourceProvider.getString(any()) } returns "Text too long"
        every { userPreferencesRepository.userPreferencesFlow } returns
                flowOf(
                    UserPreferences(
                        isDarkTheme = false,
                        username = "user1",
                        profilePicture = null
                    )
                )
        viewModel = NoteViewModel(
            noteRepository, userPreferencesRepository, analyticsRepository,
            noteHandler, mediaFileRepository, resourceProvider, errorHandler
        )
    }

    @After
    fun tearDown() = unmockkAll()

    // -------------------------------------------------------------------------
    // Initial state
    // -------------------------------------------------------------------------

    @Test
    fun `initial uploadUiState is Idle`() {
        assertTrue(viewModel.uiState.value.uploadStatus is UploadStatus.Idle)
    }

    @Test
    fun `initial postText is empty`() {
        assertEquals("", viewModel.uiState.value.postText)
    }

    @Test
    fun `initial postButtonEnabled is false`() = runTest {
        assertFalse(viewModel.postButtonEnabled.value)
    }

    // -------------------------------------------------------------------------
    // updatePostText
    // -------------------------------------------------------------------------

    @Test
    fun `updatePostText updates postText state`() {
        viewModel.updatePostText("Hello world")
        assertEquals("Hello world", viewModel.uiState.value.postText)
    }

    @Test
    fun `updatePostText valid text enables post button`() = runTest {
        viewModel.updatePostText("Hello world")
        assertTrue(viewModel.postButtonEnabled.value)
    }

    @Test
    fun `updatePostText blank text disables post button`() = runTest {
        viewModel.updatePostText("Hello")
        viewModel.updatePostText("   ")
        assertFalse(viewModel.postButtonEnabled.value)
    }

    @Test
    fun `updatePostText exceeding max length sets postTextError`() {
        val longText = "a".repeat(MAX_POST_TEXT_LENGTH + 1)
        viewModel.updatePostText(longText)
        assertEquals("Text too long", viewModel.uiState.value.postTextError)
    }

    @Test
    fun `updatePostText within max length clears postTextError`() {
        viewModel.updatePostText("a".repeat(MAX_POST_TEXT_LENGTH + 1)) // set error
        viewModel.updatePostText("short text")                           // clear error
        assertNull(viewModel.uiState.value.postTextError)
    }

    @Test
    fun `updatePostText logs analytics event`() {
        viewModel.updatePostText("cat photo")
        verify { analyticsRepository.logNoteCustomEvent("cat photo") }
    }

    // -------------------------------------------------------------------------
    // removeUriFromList
    // -------------------------------------------------------------------------

    @Test
    fun `removeUriFromList valid index removes item`() = runTest {
        val mediaDetails = listOf(
            NoteMediaDetail(
                photoUri = mockk(),
                videoUri = null,
                thumbnailUri = null,
                isVideo = false
            ),
            NoteMediaDetail(
                photoUri = mockk(),
                videoUri = null,
                thumbnailUri = null,
                isVideo = false
            )
        )
        coEvery { noteRepository.buildLocalMediaDetails(any()) } returns Result.success(mediaDetails)
        coEvery { noteRepository.quickUploadMediaToFirebase(any()) } returns Unit

        viewModel.updateUriList(listOf(mockk(), mockk()))
        assertEquals(2, viewModel.uiState.value.mediaList.size)

        viewModel.removeUriFromList(0)
        assertEquals(1, viewModel.uiState.value.mediaList.size)
    }

    @Test
    fun `removeUriFromList out of bounds index does nothing`() = runTest {
        viewModel.removeUriFromList(99)
        assertEquals(0, viewModel.uiState.value.mediaList.size)
    }

    @Test
    fun `removeUriFromList negative index does nothing`() = runTest {
        viewModel.removeUriFromList(-1)
        assertEquals(0, viewModel.uiState.value.mediaList.size)
    }

    // -------------------------------------------------------------------------
    // resetUploadState
    // -------------------------------------------------------------------------

    @Test
    fun `resetUploadState resets to Idle`() = runTest {
        coEvery { noteRepository.uploadMediaToFirebase(any()) } returns
                Result.failure(RuntimeException("upload fail"))
        viewModel.updatePostText("hello")
        viewModel.uploadPost()

        viewModel.resetUploadState()
        assertTrue(viewModel.uiState.value.uploadStatus is UploadStatus.Idle)
    }

    // -------------------------------------------------------------------------
    // URI management
    // -------------------------------------------------------------------------

    @Test
    fun `updatePhotoUri stores the uri`() {
        val uri: Uri = mockk()
        viewModel.updatePhotoUri(uri)
        assertEquals(uri, viewModel.uiState.value.photoUri)
    }

    @Test
    fun `updateVideoUri stores the uri`() {
        val uri: Uri = mockk()
        viewModel.updateVideoUri(uri)
        assertEquals(uri, viewModel.uiState.value.videoUri)
    }

    @Test
    fun `cancelPhotoCapture deletes uri and clears photoUri`() {
        val uri: Uri = mockk()
        every { mediaFileRepository.deleteUri(uri) } just runs
        viewModel.updatePhotoUri(uri)
        viewModel.cancelPhotoCapture(uri)
        assertNull(viewModel.uiState.value.photoUri)
    }

    @Test
    fun `cancelVideoCapture deletes uri and clears videoUri`() {
        val uri: Uri = mockk()
        every { mediaFileRepository.deleteUri(uri) } just runs
        viewModel.updateVideoUri(uri)
        viewModel.cancelVideoCapture(uri)
        assertNull(viewModel.uiState.value.videoUri)
    }

    // -------------------------------------------------------------------------
    // uploadPost
    // -------------------------------------------------------------------------

    @Test
    fun `uploadPost success emits Success state and notifies NoteHandler`() = runTest {
        viewModel.updatePostText("My post")
        coEvery { noteRepository.uploadMediaToFirebase(any()) } returns
                Result.success(listOf(MediaDetail(photoUrl = "http://url")))
        coEvery { noteRepository.addNote(any()) } returns Result.success(Unit)

        noteHandler.noteAddedEvent.test {
            viewModel.uploadPost()
            awaitItem() // NoteHandler notified
            // Assert final UI state inside the same coroutine scope to avoid ordering ambiguity
            assertTrue(viewModel.uiState.value.uploadStatus is UploadStatus.Success)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `uploadPost failure on media upload sets Error state`() = runTest {
        viewModel.updatePostText("My post")
        coEvery { noteRepository.uploadMediaToFirebase(any()) } returns
                Result.failure(RuntimeException("upload fail"))
        every { errorHandler.getErrorMessage(any()) } returns "upload fail"

        viewModel.uploadPost()

        val state = viewModel.uiState.value.uploadStatus
        assertTrue(state is UploadStatus.Error)
        assertEquals("upload fail", (state as UploadStatus.Error).message)
    }
}