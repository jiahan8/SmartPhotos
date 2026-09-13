package com.jiahan.smartcamera.profile

import app.cash.turbine.test
import com.jiahan.smartcamera.domain.AppError
import com.jiahan.smartcamera.domain.MediaUri
import com.jiahan.smartcamera.domain.ProfilePictureUpdate
import com.jiahan.smartcamera.domain.User
import com.jiahan.smartcamera.fake.FakeAnalyticsRepository
import com.jiahan.smartcamera.fake.FakeAuthRepository
import com.jiahan.smartcamera.fake.FakeErrorHandler
import com.jiahan.smartcamera.fake.FakeMediaCaptureRepository
import com.jiahan.smartcamera.fake.FakeMediaUploadRepository
import com.jiahan.smartcamera.fake.FakeUserPreferencesRepository
import com.jiahan.smartcamera.fake.FakeUserRepository
import com.jiahan.smartcamera.util.ErrorMessage
import com.jiahan.smartcamera.util.ValidationError
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
import kotlin.time.Clock

/**
 * [ProfileViewModel]'s suite, in `commonTest` beside its subject, on :core:domain-testing's fakes
 * with [Dispatchers.setMain] called directly -- `ExploreViewModelTest` records why each of those.
 *
 * The strict mocks it used to build on said two things these fakes have to say out loud. A stub
 * written for exact arguments -- `uploadProfilePicture(mediaUri)`, `updateUserProfile(...Set(uri,
 * url))` -- failed the test if the ViewModel passed anything else, so those cases now assert
 * [FakeUserRepository]'s `lastUploadedProfilePictureUri` and `lastUpdatedProfilePicture`. And a call
 * left unstubbed failed it if made at all, so where a case depends on `updateUserProfile` never
 * running, its call count is asserted.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val userRepository = FakeUserRepository()
    private val authRepository = FakeAuthRepository()
    private val mediaUploadRepository = FakeMediaUploadRepository()
    private val analyticsRepository = FakeAnalyticsRepository()

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

    private fun createViewModel() = ProfileViewModel(
        userRepository = userRepository,
        authRepository = authRepository,
        userPreferencesRepository = FakeUserPreferencesRepository(),
        mediaCaptureRepository = FakeMediaCaptureRepository(),
        mediaUploadRepository = mediaUploadRepository,
        analyticsRepository = analyticsRepository,
        errorHandler = FakeErrorHandler()
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        userRepository.user = testUser
        viewModel = createViewModel()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

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
        userRepository.getUserResult = Result.failure(RuntimeException("load failed"))
        val vm = createViewModel()
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

        assertEquals("New Name", analyticsRepository.lastLoggedDisplayName)
    }

    @Test
    fun `updateUsername logs analytics event`() = runTest {
        viewModel.updateUsername("newuser")

        assertEquals("newuser", analyticsRepository.lastLoggedUsername)
    }

    // -------------------------------------------------------------------------
    // updateUserProfile
    // -------------------------------------------------------------------------

    @Test
    fun `updateUserProfile when form unchanged does nothing`() = runTest {
        // form is not changed initially after loading the same values
        viewModel.updateUserProfile()
        assertEquals(0, userRepository.updateUserProfileCallCount)
    }

    @Test
    fun `updateUserProfile success emits UpdateSuccess event`() = runTest {
        viewModel.updateDisplayName("Updated Name")

        viewModel.profileEvent.test {
            viewModel.updateUserProfile()
            assertEquals(ProfileEvent.UpdateSuccess, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateUserProfile username changed checks availability`() = runTest {
        viewModel.updateUsername("brandnew")

        viewModel.updateUserProfile()
        assertEquals(listOf("brandnew"), authRepository.checkedUsernames)
    }

    @Test
    fun `updateUserProfile username not available sets error and stops`() = runTest {
        viewModel.updateUsername("taken")
        authRepository.usernameAvailableResult = Result.success(false)

        viewModel.updateUserProfile()
        assertEquals(UsernameError.Taken, viewModel.uiState.value.usernameError)
        assertFalse(viewModel.uiState.value.isErrorFree)
        assertEquals(0, userRepository.updateUserProfileCallCount)
    }

    @Test
    fun `updateUserProfile isUsernameAvailable failure sets errorMessage and emits UpdateError`() =
        runTest {
            viewModel.updateUsername("newname")
            authRepository.usernameAvailableResult =
                Result.failure(RuntimeException("network down"))

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
            assertEquals(0, userRepository.updateUserProfileCallCount)
        }

    @Test
    fun `updateUserProfile repository failure sets errorMessage and emits UpdateError`() =
        runTest {
            viewModel.updateDisplayName("Updated Name")
            userRepository.updateUserProfileResult = Result.failure(RuntimeException("boom"))

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
            userRepository.updateUserProfileResult = Result.failure(AppError.UsernameTaken())

            viewModel.updateUserProfile()

            assertEquals(UsernameError.Taken, viewModel.uiState.value.usernameError)
            assertNull(viewModel.uiState.value.errorMessage)
        }

    @Test
    fun `updateUserProfile UsernameReserved from the server renders as the reserved-name rule`() =
        runTest {
            viewModel.updateUsername("brandnew")
            userRepository.updateUserProfileResult = Result.failure(AppError.UsernameReserved())

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

    // -------------------------------------------------------------------------
    // Photo URI
    // -------------------------------------------------------------------------

    @Test
    fun `updatePhotoUri stores the uri`() {
        val uri = MediaUri("content://media/photo")
        viewModel.updatePhotoUri(uri)
        assertEquals(uri, viewModel.uiState.value.photoUri)
    }

    @Test
    fun `cancelPhotoCapture quick-uploads uri and clears photoUri`() = runTest {
        val uri = MediaUri("content://media/photo")
        viewModel.updatePhotoUri(uri)           // establish a non-null state first
        assertEquals(uri, viewModel.uiState.value.photoUri) // precondition
        viewModel.cancelPhotoCapture(uri)
        assertEquals(listOf(listOf(uri) to true), mediaUploadRepository.cacheUploads)
        assertNull(viewModel.uiState.value.photoUri)
    }

    @Test
    fun `uploadProfilePicture quick-uploads the picked uri without deleting it`() = runTest {
        val uri = MediaUri("content://media/profile")
        userRepository.uploadProfilePictureResult = Result.success("url")

        viewModel.uploadProfilePicture(uri)

        assertEquals(listOf(listOf(uri) to false), mediaUploadRepository.cacheUploads)
    }

    @Test
    fun `uploadProfilePicture success updates profile and emits PictureChanged`() = runTest {
        val uri = MediaUri("content://media/profile")
        viewModel.showBottomSheet()
        userRepository.uploadProfilePictureResult = Result.success("https://example.com/pic.jpg")

        viewModel.profileEvent.test {
            viewModel.uploadProfilePicture(uri)
            assertEquals(ProfileEvent.PictureChanged, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(uri, userRepository.lastUploadedProfilePictureUri)
        assertEquals(
            ProfilePictureUpdate.Set(uri = uri, url = "https://example.com/pic.jpg"),
            userRepository.lastUpdatedProfilePicture
        )
        assertEquals(2, userRepository.getUserCallCount) // init load + reload after success
        assertFalse(viewModel.uiState.value.isUploading)
        assertFalse(viewModel.uiState.value.isBottomSheetVisible)
    }

    @Test
    fun `uploadProfilePicture null url from repository emits UpdateError without updating profile`() =
        runTest {
            val uri = MediaUri("content://media/profile")
            userRepository.uploadProfilePictureResult = Result.success(null)

            viewModel.profileEvent.test {
                viewModel.uploadProfilePicture(uri)
                assertEquals(ProfileEvent.UpdateError(), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(0, userRepository.updateUserProfileCallCount)
            assertFalse(viewModel.uiState.value.isUploading)
        }

    @Test
    fun `uploadProfilePicture upload failure emits UpdateError with message`() = runTest {
        val uri = MediaUri("content://media/profile")
        userRepository.uploadProfilePictureResult =
            Result.failure(RuntimeException("upload failed"))

        viewModel.profileEvent.test {
            viewModel.uploadProfilePicture(uri)
            assertEquals(
                ProfileEvent.UpdateError(ErrorMessage.Unlocalized("upload failed")),
                awaitItem()
            )
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0, userRepository.updateUserProfileCallCount)
        assertFalse(viewModel.uiState.value.isUploading)
    }

    @Test
    fun `uploadProfilePicture nested profile update failure emits UpdateError with message`() =
        runTest {
            val uri = MediaUri("content://media/profile")
            userRepository.uploadProfilePictureResult =
                Result.success("https://example.com/pic.jpg")
            userRepository.updateUserProfileResult =
                Result.failure(RuntimeException("save failed"))

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

        viewModel.profileEvent.test {
            viewModel.deleteProfilePicture()
            assertEquals(ProfileEvent.PictureChanged, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(ProfilePictureUpdate.Delete, userRepository.lastUpdatedProfilePicture)
        assertEquals(2, userRepository.getUserCallCount) // init load + reload after success
        assertFalse(viewModel.uiState.value.isUploading)
        assertFalse(viewModel.uiState.value.isBottomSheetVisible)
    }

    @Test
    fun `deleteProfilePicture failure emits UpdateError with message`() = runTest {
        userRepository.updateUserProfileResult = Result.failure(RuntimeException("delete failed"))

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