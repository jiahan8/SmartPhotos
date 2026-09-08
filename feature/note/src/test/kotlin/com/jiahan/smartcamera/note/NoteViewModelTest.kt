package com.jiahan.smartcamera.note

import android.net.Uri
import com.jiahan.smartcamera.MainDispatcherRule
import com.jiahan.smartcamera.data.datastore.UserPreferences
import com.jiahan.smartcamera.data.datastore.UserPreferencesRepository
import com.jiahan.smartcamera.data.repository.AnalyticsRepository
import com.jiahan.smartcamera.data.repository.MediaFileRepository
import com.jiahan.smartcamera.data.repository.MediaUploadRepository
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.domain.MediaDetail
import com.jiahan.smartcamera.domain.MediaUri
import com.jiahan.smartcamera.domain.NoteMediaDetail
import com.jiahan.smartcamera.util.AppConstants.MAX_NOTE_TEXT_LENGTH
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.ResourceProvider
import io.mockk.coEvery
import io.mockk.coVerify
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
    private val mediaUploadRepository: MediaUploadRepository = mockk()
    private val userPreferencesRepository: UserPreferencesRepository = mockk()
    private val analyticsRepository: AnalyticsRepository = mockk()
    private val mediaFileRepository: MediaFileRepository = mockk()
    private val incomingShareHandler: IncomingShareHandler = mockk()
    private val resourceProvider: ResourceProvider = mockk()
    private val errorHandler: ErrorHandler = mockk()

    private lateinit var viewModel: NoteViewModel

    @Before
    fun setUp() {
        every { analyticsRepository.logNoteCreate(any()) } just runs
        every { errorHandler.logError(any()) } just runs
        every { errorHandler.getErrorMessage(any()) } returns "Error"
        every { resourceProvider.getString(any()) } returns "Text too long"
        every { incomingShareHandler.consume() } returns null
        every { userPreferencesRepository.userPreferences } returns
                flowOf(
                    UserPreferences(
                        isDarkTheme = false,
                        username = "user1",
                        profilePictureUrl = null
                    )
                )
        viewModel = NoteViewModel(
            noteRepository,
            mediaUploadRepository,
            userPreferencesRepository,
            analyticsRepository,
            mediaFileRepository,
            incomingShareHandler,
            resourceProvider,
            errorHandler
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
    fun `updateNoteText exceeding max length sets noteTextError`() {
        val longText = "a".repeat(MAX_NOTE_TEXT_LENGTH + 1)
        viewModel.updateNoteText(longText)
        assertEquals("Text too long", viewModel.uiState.value.noteTextError)
    }

    @Test
    fun `updateNoteText within max length clears noteTextError`() {
        viewModel.updateNoteText("a".repeat(MAX_NOTE_TEXT_LENGTH + 1)) // set error
        viewModel.updateNoteText("short text")                           // clear error
        assertNull(viewModel.uiState.value.noteTextError)
    }

    @Test
    fun `updateNoteText logs analytics event`() {
        viewModel.updateNoteText("cat photo")
        verify { analyticsRepository.logNoteCreate("cat photo") }
    }

    /**
     * A [Uri] mock with a fixed [toString], paired with the [MediaUri] the ViewModel converts it
     * into before calling the repository. Repository contracts take [MediaUri], so expectations
     * have to be written against the converted value rather than the platform [Uri].
     */
    private fun fakeUri(value: String): Pair<Uri, MediaUri> =
        mockk<Uri>().also { every { it.toString() } returns value } to MediaUri(value)

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
        coEvery { mediaUploadRepository.buildLocalMediaDetails(any()) } returns Result.success(mediaDetails)
        coEvery { mediaUploadRepository.uploadMediaToCache(any(), any()) } returns Unit

        viewModel.addMedia(listOf(mockk(), mockk()))
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

    // -------------------------------------------------------------------------
    // resetUploadStatus
    // -------------------------------------------------------------------------

    @Test
    fun `resetUploadStatus resets to Idle`() = runTest {
        coEvery { mediaUploadRepository.uploadMedia(any()) } returns
                Result.failure(RuntimeException("upload fail"))
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
    fun `cancelPhotoCapture quick-uploads uri and clears photoUri`() = runTest {
        val (uri, mediaUri) = fakeUri("content://media/photo")
        coEvery { mediaUploadRepository.uploadMediaToCache(listOf(mediaUri), true) } returns Unit
        viewModel.updatePhotoUri(uri)
        viewModel.cancelPhotoCapture(uri)
        coVerify { mediaUploadRepository.uploadMediaToCache(listOf(mediaUri), true) }
        assertNull(viewModel.uiState.value.photoUri)
    }

    @Test
    fun `cancelVideoCapture quick-uploads uri and clears videoUri`() = runTest {
        val (uri, mediaUri) = fakeUri("content://media/video")
        coEvery { mediaUploadRepository.uploadMediaToCache(listOf(mediaUri), true) } returns Unit
        viewModel.updateVideoUri(uri)
        viewModel.cancelVideoCapture(uri)
        coVerify { mediaUploadRepository.uploadMediaToCache(listOf(mediaUri), true) }
        assertNull(viewModel.uiState.value.videoUri)
    }

    // -------------------------------------------------------------------------
    // saveNote
    // -------------------------------------------------------------------------

    @Test
    fun `saveNote success emits Success state`() = runTest {
        viewModel.updateNoteText("My note")
        coEvery { mediaUploadRepository.uploadMedia(any()) } returns
                Result.success(listOf(MediaDetail(photoUrl = "http://url")))
        coEvery { noteRepository.addNote(any()) } returns Result.success(Unit)

        viewModel.saveNote()

        // This used to also await a NoteHandler emission. addNote reads the created note back into
        // the `notes` table now, so the feeds see it as a row -- there is no event to assert.
        assertTrue(viewModel.uiState.value.uploadStatus is UploadStatus.Success)
        coVerify { noteRepository.addNote(any()) }
    }

    @Test
    fun `saveNote failure on media upload sets Error state`() = runTest {
        viewModel.updateNoteText("My note")
        coEvery { mediaUploadRepository.uploadMedia(any()) } returns
                Result.failure(RuntimeException("upload fail"))
        every { errorHandler.getErrorMessage(any()) } returns "upload fail"

        viewModel.saveNote()

        val state = viewModel.uiState.value.uploadStatus
        assertTrue(state is UploadStatus.Error)
        assertEquals("upload fail", (state as UploadStatus.Error).message)
    }
}