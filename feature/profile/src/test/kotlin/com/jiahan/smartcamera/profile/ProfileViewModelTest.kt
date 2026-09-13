package com.jiahan.smartcamera.profile

import android.net.Uri
import app.cash.turbine.test
import com.jiahan.smartcamera.MainDispatcherRule
import com.jiahan.smartcamera.data.datastore.UserPreferencesRepository
import com.jiahan.smartcamera.data.repository.AnalyticsRepository
import com.jiahan.smartcamera.data.repository.AuthRepository
import com.jiahan.smartcamera.data.repository.MediaFileRepository
import com.jiahan.smartcamera.data.repository.MediaUploadRepository
import com.jiahan.smartcamera.data.repository.UserRepository
import com.jiahan.smartcamera.domain.AppError
import com.jiahan.smartcamera.domain.MediaUri
import com.jiahan.smartcamera.domain.ProfilePictureUpdate
import com.jiahan.smartcamera.domain.User
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.ErrorMessage
import com.jiahan.smartcamera.util.ValidationError
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.time.Clock

class ProfileViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val userRepository: UserRepository = mockk()
    private val authRepository: AuthRepository = mockk()
    private val userPreferencesRepository: UserPreferencesRepository = mockk()
    private val mediaFileRepository: MediaFileRepository = mockk()
    private val mediaUploadRepository: MediaUploadRepository = mockk()
    private val analyticsRepository: AnalyticsRepository = mockk()
    private val errorHandler: ErrorHandler = mockk()

    private val testUser = User(
        userId = "uid123",
        email = "user@example.com",
        metadata = "",
        displayName = "Test User",
        username = "testuser",
        profilePictureUrl = null,
        createdDate = Clock.System.now(),
    )

    private lateinit var viewModel: ProfileViewModel

    @Before
    fun setUp() {
        every { errorHandler.logError(any()) } just runs
        every { analyticsRepository.logDisplayName(any()) } just runs
        every { analyticsRepository.logUsername(any()) } just runs
        coEvery { userRepository.getUser() } returns Result.success(testUser)
        coEvery {
            userPreferencesRepository.updateLocalUserProfile(any(), any())
        } returns Result.success(Unit)
        coEvery { mediaUploadRepository.uploadMediaToCache(any(), any()) } returns Unit
        viewModel = ProfileViewModel(
            userRepository, authRepository, userPreferencesRepository,
            mediaFileRepository, mediaUploadRepository, analyticsRepository,
            errorHandler
        )
    }

    @After
    fun tearDown() = unmockkAll()

    // -------------------------------------------------------------------------
    // Init / load profile
    // -------------------------------------------------------------------------

    @Test
    fun `init loads user profile into state fields`() = runTest {
        assertEquals("user@example.com", viewModel.uiState.value.email)
        assertEquals("Test User", viewModel.uiState.value.displayName)
        assertEquals("testuser", viewModel.uiState.value.username)
        assertNull(viewModel.uiState.value.profilePictureUrl)
    }

    @Test
    fun `init load failure sets errorMessage`() = runTest {
        val exception = RuntimeException("load failed")
        coEvery { userRepository.getUser() } returns Result.failure(exception)
        val vm = ProfileViewModel(
            userRepository, authRepository, userPreferencesRepository,
            mediaFileRepository, mediaUploadRepository, analyticsRepository,
            errorHandler
        )
        assertEquals(ErrorMessage.Unlocalized("load failed"), vm.uiState.value.errorMessage)
    }

    // -------------------------------------------------------------------------
    // Field updates
    // -------------------------------------------------------------------------

    @Test
    fun `updateDisplayName valid value clears error and marks form changed`() = runTest {
        viewModel.updateDisplayName("New Name")

        assertEquals("New Name", viewModel.uiState.value.displayName)
        assertNull(viewModel.uiState.value.displayNameError)
        assertTrue(viewModel.uiState.value.isFormChanged)
    }

    @Test
    fun `updateDisplayName blank value sets displayNameError`() = runTest {
        viewModel.updateDisplayName("  ")

        assertEquals(ValidationError.NAME_EMPTY, viewModel.uiState.value.displayNameError)
    }

    @Test
    fun `updateUsername valid value clears error`() = runTest {
        viewModel.updateUsername("newuser")

        assertEquals("newuser", viewModel.uiState.value.username)
        assertNull(viewModel.uiState.value.usernameError)
    }

    @Test
    fun `updateUsername with invalid characters sets usernameError`() = runTest {
        viewModel.updateUsername("bad user!")

        assertEquals(
            UsernameError.Invalid(ValidationError.USERNAME_INVALID_CHARACTERS),
            viewModel.uiState.value.usernameError
        )
    }

    @Test
    fun `updateDisplayName logs analytics event`() = runTest {
        viewModel.updateDisplayName("New Name")

        verify { analyticsRepository.logDisplayName("New Name") }
    }

    @Test
    fun `updateUsername logs analytics event`() = runTest {
        viewModel.updateUsername("newuser")

        verify { analyticsRepository.logUsername("newuser") }
    }

    // -------------------------------------------------------------------------
    // updateUserProfile
    // -------------------------------------------------------------------------

    @Test
    fun `updateUserProfile when form unchanged does nothing`() = runTest {
        // form is not changed initially after loading the same values
        viewModel.updateUserProfile()
        coVerify(exactly = 0) {
            userRepository.updateUserProfile(
                any(),
                any(),
                any()
            )
        }
    }

    @Test
    fun `updateUserProfile success emits UpdateSuccess event`() = runTest {
        viewModel.updateDisplayName("Updated Name")
        coEvery { userRepository.updateUserProfile(any(), any(), any()) } returns
                Result.success(Unit)

        viewModel.profileEvent.test {
            viewModel.updateUserProfile()
            assertEquals(ProfileEvent.UpdateSuccess, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateUserProfile username changed checks availability`() = runTest {
        viewModel.updateUsername("brandnew")
        coEvery { authRepository.isUsernameAvailable("brandnew") } returns Result.success(true)
        coEvery { userRepository.updateUserProfile(any(), any(), any()) } returns
                Result.success(Unit)

        viewModel.updateUserProfile()
        coVerify { authRepository.isUsernameAvailable("brandnew") }
    }

    @Test
    fun `updateUserProfile username not available sets error and stops`() = runTest {
        viewModel.updateUsername("taken")
        coEvery { authRepository.isUsernameAvailable("taken") } returns Result.success(false)

        viewModel.updateUserProfile()
        assertEquals(UsernameError.Taken, viewModel.uiState.value.usernameError)
        assertFalse(viewModel.uiState.value.isErrorFree)
    }

    @Test
    fun `updateUserProfile isUsernameAvailable failure sets errorMessage and emits UpdateError`() =
        runTest {
            viewModel.updateUsername("newname")
            val exception = RuntimeException("network down")
            coEvery { authRepository.isUsernameAvailable("newname") } returns
                    Result.failure(exception)

            viewModel.profileEvent.test {
                viewModel.updateUserProfile()
                assertEquals(ProfileEvent.UpdateError(), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(
                ErrorMessage.Unlocalized("network down"),
                viewModel.uiState.value.errorMessage
            )
            assertFalse(viewModel.uiState.value.isLoading)
            coVerify(exactly = 0) { userRepository.updateUserProfile(any(), any(), any()) }
        }

    @Test
    fun `updateUserProfile repository failure sets errorMessage and emits UpdateError`() =
        runTest {
            viewModel.updateDisplayName("Updated Name")
            val exception = RuntimeException("boom")
            coEvery { userRepository.updateUserProfile(any(), any(), any()) } returns
                    Result.failure(exception)

            viewModel.profileEvent.test {
                viewModel.updateUserProfile()
                assertEquals(ProfileEvent.UpdateError(), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(ErrorMessage.Unlocalized("boom"), viewModel.uiState.value.errorMessage)
            assertNull(viewModel.uiState.value.usernameError)
            assertFalse(viewModel.uiState.value.isLoading)
        }

    /**
     * A server-side username conflict goes under the username field rather than the general error
     * slot. These pin which field and which identity; the copy each renders is ProfileScreen's.
     */
    @Test
    fun `updateUserProfile UsernameTaken from the server shows under the username field`() =
        runTest {
            viewModel.updateUsername("brandnew")
            coEvery { authRepository.isUsernameAvailable("brandnew") } returns Result.success(true)
            coEvery { userRepository.updateUserProfile(any(), any(), any()) } returns
                    Result.failure(AppError.UsernameTaken())

            viewModel.updateUserProfile()

            assertEquals(UsernameError.Taken, viewModel.uiState.value.usernameError)
            assertNull(viewModel.uiState.value.errorMessage)
        }

    @Test
    fun `updateUserProfile UsernameReserved from the server renders as the reserved-name rule`() =
        runTest {
            viewModel.updateUsername("brandnew")
            coEvery { authRepository.isUsernameAvailable("brandnew") } returns Result.success(true)
            coEvery { userRepository.updateUserProfile(any(), any(), any()) } returns
                    Result.failure(AppError.UsernameReserved())

            viewModel.updateUserProfile()

            assertEquals(
                UsernameError.Invalid(ValidationError.USERNAME_RESERVED),
                viewModel.uiState.value.usernameError
            )
            assertNull(viewModel.uiState.value.errorMessage)
        }

    // -------------------------------------------------------------------------
    // Dialog / bottom sheet
    // -------------------------------------------------------------------------

    @Test
    fun `showDeletePictureDialog sets dialogState to DeletePicture`() {
        viewModel.showDeletePictureDialog()
        assertEquals(ProfileDialogState.DeletePicture, viewModel.uiState.value.dialogState)
    }

    @Test
    fun `dismissDialog resets dialogState to None`() {
        viewModel.showDeletePictureDialog()
        viewModel.dismissDialog()
        assertEquals(ProfileDialogState.None, viewModel.uiState.value.dialogState)
    }

    @Test
    fun `showBottomSheet and dismissBottomSheet update state`() {
        viewModel.showBottomSheet()
        assertTrue(viewModel.uiState.value.isBottomSheetVisible)
        viewModel.dismissBottomSheet()
        assertFalse(viewModel.uiState.value.isBottomSheetVisible)
    }

    /**
     * A [Uri] mock with a fixed [toString], paired with the [MediaUri] the ViewModel converts it
     * into before calling the repository. Repository contracts take [MediaUri], so expectations
     * have to be written against the converted value rather than the platform [Uri].
     */
    private fun fakeUri(value: String): Pair<Uri, MediaUri> =
        mockk<Uri>().also { every { it.toString() } returns value } to MediaUri(value)

    // -------------------------------------------------------------------------
    // Photo URI
    // -------------------------------------------------------------------------

    @Test
    fun `updatePhotoUri stores the uri`() {
        val uri: Uri = mockk()
        viewModel.updatePhotoUri(uri)
        assertEquals(uri, viewModel.uiState.value.photoUri)
    }

    @Test
    fun `cancelPhotoCapture quick-uploads uri and clears photoUri`() = runTest {
        val (uri, mediaUri) = fakeUri("content://media/photo")
        viewModel.updatePhotoUri(uri)           // establish a non-null state first
        assertEquals(uri, viewModel.uiState.value.photoUri) // precondition
        viewModel.cancelPhotoCapture(uri)
        coVerify { mediaUploadRepository.uploadMediaToCache(listOf(mediaUri), true) }
        assertNull(viewModel.uiState.value.photoUri)
    }

    @Test
    fun `uploadProfilePicture quick-uploads the picked uri without deleting it`() = runTest {
        val (uri, mediaUri) = fakeUri("content://media/profile")
        coEvery { userRepository.uploadProfilePicture(mediaUri) } returns Result.success("url")
        coEvery {
            userRepository.updateUserProfile(any(), any(), any())
        } returns Result.success(Unit)

        viewModel.uploadProfilePicture(uri)

        coVerify { mediaUploadRepository.uploadMediaToCache(listOf(mediaUri), false) }
    }

    @Test
    fun `uploadProfilePicture success updates profile and emits PictureChanged`() = runTest {
        val (uri, mediaUri) = fakeUri("content://media/profile")
        viewModel.showBottomSheet()
        coEvery { userRepository.uploadProfilePicture(mediaUri) } returns
                Result.success("https://example.com/pic.jpg")
        coEvery {
            userRepository.updateUserProfile(
                displayName = null,
                username = null,
                profilePicture = ProfilePictureUpdate.Set(
                    uri = mediaUri,
                    url = "https://example.com/pic.jpg"
                )
            )
        } returns Result.success(Unit)

        viewModel.profileEvent.test {
            viewModel.uploadProfilePicture(uri)
            assertEquals(ProfileEvent.PictureChanged, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 2) { userRepository.getUser() } // init load + reload after success
        assertFalse(viewModel.uiState.value.isUploading)
        assertFalse(viewModel.uiState.value.isBottomSheetVisible)
    }

    @Test
    fun `uploadProfilePicture null url from repository emits UpdateError without updating profile`() =
        runTest {
            val (uri, mediaUri) = fakeUri("content://media/profile")
            coEvery { userRepository.uploadProfilePicture(mediaUri) } returns Result.success(null)

            viewModel.profileEvent.test {
                viewModel.uploadProfilePicture(uri)
                assertEquals(ProfileEvent.UpdateError(), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            coVerify(exactly = 0) { userRepository.updateUserProfile(any(), any(), any()) }
            assertFalse(viewModel.uiState.value.isUploading)
        }

    @Test
    fun `uploadProfilePicture upload failure emits UpdateError with message`() = runTest {
        val (uri, mediaUri) = fakeUri("content://media/profile")
        val exception = RuntimeException("upload failed")
        coEvery { userRepository.uploadProfilePicture(mediaUri) } returns Result.failure(exception)

        viewModel.profileEvent.test {
            viewModel.uploadProfilePicture(uri)
            assertEquals(
                ProfileEvent.UpdateError(ErrorMessage.Unlocalized("upload failed")),
                awaitItem()
            )
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse(viewModel.uiState.value.isUploading)
    }

    @Test
    fun `uploadProfilePicture nested profile update failure emits UpdateError with message`() =
        runTest {
            val (uri, mediaUri) = fakeUri("content://media/profile")
            val exception = RuntimeException("save failed")
            coEvery { userRepository.uploadProfilePicture(mediaUri) } returns
                    Result.success("https://example.com/pic.jpg")
            coEvery { userRepository.updateUserProfile(any(), any(), any()) } returns
                    Result.failure(exception)

            viewModel.profileEvent.test {
                viewModel.uploadProfilePicture(uri)
                assertEquals(
                    ProfileEvent.UpdateError(ErrorMessage.Unlocalized("save failed")),
                    awaitItem()
                )
                cancelAndIgnoreRemainingEvents()
            }
            assertFalse(viewModel.uiState.value.isUploading)
        }

    // -------------------------------------------------------------------------
    // deleteProfilePicture
    // -------------------------------------------------------------------------

    @Test
    fun `deleteProfilePicture success updates profile and emits PictureChanged`() = runTest {
        viewModel.showBottomSheet()
        coEvery {
            userRepository.updateUserProfile(
                displayName = null,
                username = null,
                profilePicture = ProfilePictureUpdate.Delete
            )
        } returns Result.success(Unit)

        viewModel.profileEvent.test {
            viewModel.deleteProfilePicture()
            assertEquals(ProfileEvent.PictureChanged, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 2) { userRepository.getUser() } // init load + reload after success
        assertFalse(viewModel.uiState.value.isUploading)
        assertFalse(viewModel.uiState.value.isBottomSheetVisible)
    }

    @Test
    fun `deleteProfilePicture failure emits UpdateError with message`() = runTest {
        val exception = RuntimeException("delete failed")
        coEvery { userRepository.updateUserProfile(any(), any(), any()) } returns
                Result.failure(exception)

        viewModel.profileEvent.test {
            viewModel.deleteProfilePicture()
            assertEquals(
                ProfileEvent.UpdateError(ErrorMessage.Unlocalized("delete failed")),
                awaitItem()
            )
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse(viewModel.uiState.value.isUploading)
    }
}