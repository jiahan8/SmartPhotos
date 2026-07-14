package com.jiahan.smartcamera.profile

import android.net.Uri
import app.cash.turbine.test
import com.jiahan.smartcamera.MainDispatcherRule
import com.jiahan.smartcamera.data.datastore.UserPreferencesRepository
import com.jiahan.smartcamera.data.repository.AuthRepository
import com.jiahan.smartcamera.data.repository.MediaFileRepository
import com.jiahan.smartcamera.data.repository.UserRepository
import com.jiahan.smartcamera.domain.User
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.ResourceProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class ProfileViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val userRepository: UserRepository = mockk()
    private val authRepository: AuthRepository = mockk()
    private val userPreferencesRepository: UserPreferencesRepository = mockk()
    private val mediaFileRepository: MediaFileRepository = mockk()
    private val resourceProvider: ResourceProvider = mockk()
    private val errorHandler: ErrorHandler = mockk()

    private val testUser = User(
        email = "user@example.com",
        metadata = "",
        displayName = "Test User",
        username = "testuser",
        profilePicture = null,
        createdDate = Instant.now(),
        documentPath = "users/uid123"
    )

    private lateinit var viewModel: ProfileViewModel

    @Before
    fun setUp() {
        every { errorHandler.logError(any()) } just runs
        every { errorHandler.getErrorMessage(any()) } returns "Error"
        every { resourceProvider.getString(any()) } returns "Validation error"
        coEvery { userRepository.getUser() } returns Result.success(testUser)
        coEvery {
            userPreferencesRepository.updateLocalUserProfile(any(), any())
        } returns Result.success(Unit)
        viewModel = ProfileViewModel(
            userRepository, authRepository, userPreferencesRepository,
            mediaFileRepository, resourceProvider, errorHandler
        )
    }

    @After
    fun tearDown() = unmockkAll()

    // -------------------------------------------------------------------------
    // Init / load profile
    // -------------------------------------------------------------------------

    @Test
    fun `init loads user profile into state fields`() = runTest {
        assertEquals("user@example.com", viewModel.email.value)
        assertEquals("Test User", viewModel.displayName.value)
        assertEquals("testuser", viewModel.username.value)
        assertNull(viewModel.profilePictureUrl.value)
    }

    @Test
    fun `init load failure sets errorMessage`() = runTest {
        val exception = RuntimeException("load failed")
        coEvery { userRepository.getUser() } returns Result.failure(exception)
        every { errorHandler.getErrorMessage(exception) } returns "load failed"
        val vm = ProfileViewModel(
            userRepository, authRepository, userPreferencesRepository,
            mediaFileRepository, resourceProvider, errorHandler
        )
        assertEquals("load failed", vm.errorMessage.value)
    }

    // -------------------------------------------------------------------------
    // Field updates
    // -------------------------------------------------------------------------

    @Test
    fun `updateDisplayNameText valid value clears error and marks form changed`() = runTest {
        viewModel.updateDisplayNameText("New Name")

        assertEquals("New Name", viewModel.displayName.value)
        assertNull(viewModel.displayNameErrorMessage.value)
        assertTrue(viewModel.isFormChanged.value)
    }

    @Test
    fun `updateDisplayNameText blank value sets displayNameError`() = runTest {
        viewModel.updateDisplayNameText("  ")

        assertNotNull(viewModel.displayNameErrorMessage.value)
    }

    @Test
    fun `updateUsernameText valid value clears error`() = runTest {
        viewModel.updateUsernameText("newuser")

        assertEquals("newuser", viewModel.username.value)
        assertNull(viewModel.usernameErrorMessage.value)
    }

    @Test
    fun `updateUsernameText with invalid characters sets usernameError`() = runTest {
        viewModel.updateUsernameText("bad user!")

        assertNotNull(viewModel.usernameErrorMessage.value)
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
        viewModel.updateDisplayNameText("Updated Name")
        coEvery { userRepository.updateUserProfile(any(), any(), any()) } returns
                Result.success(Unit)

        viewModel.events.test {
            viewModel.updateUserProfile()
            assertEquals(ProfileEvent.UpdateSuccess, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateUserProfile username changed checks availability`() = runTest {
        viewModel.updateUsernameText("brandnew")
        coEvery { authRepository.isUsernameAvailable("brandnew") } returns Result.success(true)
        coEvery { userRepository.updateUserProfile(any(), any(), any()) } returns
                Result.success(Unit)

        viewModel.updateUserProfile()
        coVerify { authRepository.isUsernameAvailable("brandnew") }
    }

    @Test
    fun `updateUserProfile username not available sets error and stops`() = runTest {
        viewModel.updateUsernameText("taken")
        coEvery { authRepository.isUsernameAvailable("taken") } returns Result.success(false)

        viewModel.updateUserProfile()
        assertNotNull(viewModel.usernameErrorMessage.value)
        assertFalse(viewModel.isErrorFree.value)
    }

    // -------------------------------------------------------------------------
    // Dialog / bottom sheet
    // -------------------------------------------------------------------------

    @Test
    fun `showDeletePictureDialog sets dialogState to DeletePicture`() {
        viewModel.showDeletePictureDialog()
        assertEquals(ProfileDialogState.DeletePicture, viewModel.dialogState.value)
    }

    @Test
    fun `dismissDialog resets dialogState to None`() {
        viewModel.showDeletePictureDialog()
        viewModel.dismissDialog()
        assertEquals(ProfileDialogState.None, viewModel.dialogState.value)
    }

    @Test
    fun `updateBottomSheetVisibility updates state`() {
        viewModel.updateBottomSheetVisibility(true)
        assertTrue(viewModel.showBottomSheet.value)
        viewModel.updateBottomSheetVisibility(false)
        assertFalse(viewModel.showBottomSheet.value)
    }

    // -------------------------------------------------------------------------
    // Photo URI
    // -------------------------------------------------------------------------

    @Test
    fun `updatePhotoUri stores the uri`() {
        val uri: Uri = mockk()
        viewModel.updatePhotoUri(uri)
        assertEquals(uri, viewModel.photoUri.value)
    }

    @Test
    fun `cancelPhotoCapture deletes uri and clears photoUri`() {
        val uri: Uri = mockk()
        every { mediaFileRepository.deleteUri(uri) } just runs
        viewModel.updatePhotoUri(uri)           // establish a non-null state first
        assertEquals(uri, viewModel.photoUri.value) // precondition
        viewModel.cancelPhotoCapture(uri)
        assertNull(viewModel.photoUri.value)
    }
}