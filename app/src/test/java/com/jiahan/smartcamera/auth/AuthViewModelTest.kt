package com.jiahan.smartcamera.auth

import app.cash.turbine.test
import com.jiahan.smartcamera.MainDispatcherRule
import com.jiahan.smartcamera.data.datastore.UserPreferencesRepository
import com.jiahan.smartcamera.data.repository.AnalyticsRepository
import com.jiahan.smartcamera.data.repository.AuthRepository
import com.jiahan.smartcamera.data.repository.UserRepository
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.ResourceProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository: AuthRepository = mockk()
    private val userRepository: UserRepository = mockk()
    private val userPreferencesRepository: UserPreferencesRepository = mockk()
    private val analyticsRepository: AnalyticsRepository = mockk()
    private val resourceProvider: ResourceProvider = mockk()
    private val errorHandler: ErrorHandler = mockk()

    private lateinit var viewModel: AuthViewModel

    @Before
    fun setUp() {
        every { analyticsRepository.logTextCustomEvent(any()) } just runs
        every { errorHandler.logError(any()) } just runs
        every { errorHandler.getErrorMessage(any()) } returns "Error"
        every { resourceProvider.getString(any()) } returns "Error message"
        every { resourceProvider.getString(any(), any()) } returns "Error message"

        viewModel = AuthViewModel(
            authRepository, userRepository, userPreferencesRepository,
            analyticsRepository, resourceProvider, errorHandler
        )
    }

    @After
    fun tearDown() = unmockkAll()

    // -------------------------------------------------------------------------
    // Field updates
    // -------------------------------------------------------------------------

    @Test
    fun `updateEmailText updates email StateFlow`() {
        viewModel.updateEmailText("user@example.com")
        assertEquals("user@example.com", viewModel.email.value)
    }

    @Test
    fun `updatePasswordText updates password StateFlow`() {
        viewModel.updatePasswordText("secret123")
        assertEquals("secret123", viewModel.password.value)
    }

    @Test
    fun `updateDisplayNameText updates displayName StateFlow`() {
        viewModel.updateDisplayNameText("Jane Doe")
        assertEquals("Jane Doe", viewModel.displayName.value)
    }

    @Test
    fun `updateUsernameText updates username StateFlow`() {
        viewModel.updateUsernameText("janedoe")
        assertEquals("janedoe", viewModel.username.value)
    }

    @Test
    fun `updatePasswordVisibility true shows password`() {
        viewModel.updatePasswordVisibility(true)
        assertTrue(viewModel.passwordVisible.value)
    }

    @Test
    fun `updatePasswordVisibility false hides password`() {
        viewModel.updatePasswordVisibility(true)
        viewModel.updatePasswordVisibility(false)
        assertFalse(viewModel.passwordVisible.value)
    }

    // -------------------------------------------------------------------------
    // toggleAuthMode
    // -------------------------------------------------------------------------

    @Test
    fun `toggleAuthMode switches from login to register mode`() {
        assertTrue(viewModel.isLoginMode.value)
        viewModel.toggleAuthMode()
        assertFalse(viewModel.isLoginMode.value)
    }

    @Test
    fun `toggleAuthMode clears all fields`() {
        viewModel.updateEmailText("test@example.com")
        viewModel.updatePasswordText("pass")
        viewModel.updateDisplayNameText("Test")
        viewModel.updateUsernameText("testuser")

        viewModel.toggleAuthMode()

        assertEquals("", viewModel.email.value)
        assertEquals("", viewModel.password.value)
        assertEquals("", viewModel.displayName.value)
        assertEquals("", viewModel.username.value)
    }

    @Test
    fun `toggleAuthMode resets authUiState to Idle`() {
        viewModel.toggleAuthMode()
        assertEquals(AuthUiState.Idle, viewModel.authUiState.value)
    }

    // -------------------------------------------------------------------------
    // signIn — validation
    // -------------------------------------------------------------------------

    @Test
    fun `signIn with blank email sets Error state without network call`() = runTest {
        viewModel.updateEmailText("   ")
        viewModel.updatePasswordText("password")

        viewModel.signIn()

        assertTrue(viewModel.authUiState.value is AuthUiState.Error)
    }

    @Test
    fun `signIn with blank password sets Error state without network call`() = runTest {
        viewModel.updateEmailText("user@example.com")
        // password left empty

        viewModel.signIn()

        assertTrue(viewModel.authUiState.value is AuthUiState.Error)
    }

    // -------------------------------------------------------------------------
    // signIn — success path
    // -------------------------------------------------------------------------

    @Test
    fun `signIn success with verified email sends NavigateToHome event`() = runTest {
        viewModel.updateEmailText("user@example.com")
        viewModel.updatePasswordText("password123")

        coEvery { authRepository.signIn(any(), any()) } returns Result.success(Unit)
        coEvery { authRepository.checkEmailVerified() } returns Result.success(true)
        coEvery { userRepository.getUser() } returns Result.success(null)
        coEvery {
            userPreferencesRepository.updateLocalUserProfile(any(), any())
        } returns Result.success(Unit)

        viewModel.navigationEvent.test {
            viewModel.signIn()
            assertEquals(AuthNavigationEvent.NavigateToHome, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `signIn success with verified email resets state to Idle`() = runTest {
        viewModel.updateEmailText("user@example.com")
        viewModel.updatePasswordText("password123")

        coEvery { authRepository.signIn(any(), any()) } returns Result.success(Unit)
        coEvery { authRepository.checkEmailVerified() } returns Result.success(true)
        coEvery { userRepository.getUser() } returns Result.success(null)
        coEvery {
            userPreferencesRepository.updateLocalUserProfile(any(), any())
        } returns Result.success(Unit)

        viewModel.signIn()

        assertEquals(AuthUiState.Idle, viewModel.authUiState.value)
    }

    @Test
    fun `signIn success with unverified email sets Error with showResendButton true`() = runTest {
        viewModel.updateEmailText("user@example.com")
        viewModel.updatePasswordText("password123")

        coEvery { authRepository.signIn(any(), any()) } returns Result.success(Unit)
        coEvery { authRepository.checkEmailVerified() } returns Result.success(false)

        viewModel.signIn()

        val state = viewModel.authUiState.value
        assertTrue(state is AuthUiState.Error)
        assertTrue((state as AuthUiState.Error).showResendButton)
    }

    // -------------------------------------------------------------------------
    // signIn — loading state
    // -------------------------------------------------------------------------

    @Test
    fun `signIn emits Loading state before network call completes`() = runTest {
        // Use StandardTestDispatcher so we can pause execution between Loading and the result
        val paused = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(paused)
        val vm = AuthViewModel(
            authRepository, userRepository, userPreferencesRepository,
            analyticsRepository, resourceProvider, errorHandler
        )
        vm.updateEmailText("user@example.com")
        vm.updatePasswordText("password123")
        coEvery {
            authRepository.signIn(
                any(),
                any()
            )
        } coAnswers { delay(1.seconds); Result.success(Unit) }
        coEvery { authRepository.checkEmailVerified() } returns Result.success(true)
        coEvery { userRepository.getUser() } returns Result.success(null)
        coEvery {
            userPreferencesRepository.updateLocalUserProfile(any(), any())
        } returns Result.success(Unit)

        vm.authUiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())        // initial value
            vm.signIn()
            advanceTimeBy(1.milliseconds)                       // let the launch start; suspends at delay(1s)
            assertEquals(AuthUiState.Loading, awaitItem())
            advanceUntilIdle()                                  // complete the delay → signIn finishes
            assertEquals(AuthUiState.Idle, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // signUp — loading state
    // -------------------------------------------------------------------------

    @Test
    fun `signUp emits Loading state before network call completes`() = runTest {
        val paused = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(paused)
        val vm = AuthViewModel(
            authRepository, userRepository, userPreferencesRepository,
            analyticsRepository, resourceProvider, errorHandler
        )
        vm.updateEmailText("new@example.com")
        vm.updatePasswordText("password123")
        vm.updateDisplayNameText("New User")
        vm.updateUsernameText("newuser")
        coEvery { authRepository.isUsernameAvailable("newuser") } coAnswers {
            delay(1.seconds); Result.success(true)
        }
        coEvery { authRepository.signUp(any(), any(), any(), any()) } returns Result.success(Unit)
        every { resourceProvider.getString(any()) } returns "Verification email sent"

        vm.authUiState.test {
            assertEquals(AuthUiState.Idle, awaitItem())
            vm.signUp()
            advanceTimeBy(1.milliseconds)
            assertEquals(AuthUiState.Loading, awaitItem())
            advanceUntilIdle()
            assertTrue(awaitItem() is AuthUiState.Info)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // signIn — failure path
    // -------------------------------------------------------------------------

    @Test
    fun `signIn network failure sets Error state`() = runTest {
        viewModel.updateEmailText("user@example.com")
        viewModel.updatePasswordText("wrongpass")

        val exception = RuntimeException("Invalid credentials")
        coEvery { authRepository.signIn(any(), any()) } returns Result.failure(exception)
        every { errorHandler.getErrorMessage(exception) } returns "Invalid credentials"

        viewModel.signIn()

        val state = viewModel.authUiState.value
        assertTrue(state is AuthUiState.Error)
        assertEquals("Invalid credentials", (state as AuthUiState.Error).message)
    }

    // -------------------------------------------------------------------------
    // signUp — validation
    // -------------------------------------------------------------------------

    @Test
    fun `signUp with blank email sets Error state`() = runTest {
        viewModel.updateEmailText("")
        viewModel.updatePasswordText("password")
        viewModel.updateDisplayNameText("John")
        viewModel.updateUsernameText("johndoe")

        viewModel.signUp()

        assertTrue(viewModel.authUiState.value is AuthUiState.Error)
    }

    @Test
    fun `signUp with blank displayName sets Error state`() = runTest {
        viewModel.updateEmailText("new@example.com")
        viewModel.updatePasswordText("password")
        viewModel.updateDisplayNameText("")
        viewModel.updateUsernameText("johndoe")

        viewModel.signUp()

        assertTrue(viewModel.authUiState.value is AuthUiState.Error)
    }

    @Test
    fun `signUp with blank username sets Error state`() = runTest {
        viewModel.updateEmailText("new@example.com")
        viewModel.updatePasswordText("password")
        viewModel.updateDisplayNameText("John Doe")
        viewModel.updateUsernameText("")

        viewModel.signUp()

        assertTrue(viewModel.authUiState.value is AuthUiState.Error)
    }

    @Test
    fun `signUp with too long displayName sets Error state`() = runTest {
        viewModel.updateEmailText("new@example.com")
        viewModel.updatePasswordText("password")
        viewModel.updateDisplayNameText("a".repeat(51)) // MAX = 50
        viewModel.updateUsernameText("johndoe")

        viewModel.signUp()

        assertTrue(viewModel.authUiState.value is AuthUiState.Error)
    }

    @Test
    fun `signUp with invalid username characters sets Error state`() = runTest {
        viewModel.updateEmailText("new@example.com")
        viewModel.updatePasswordText("password")
        viewModel.updateDisplayNameText("John Doe")
        viewModel.updateUsernameText("user name!") // space and ! not allowed

        viewModel.signUp()

        assertTrue(viewModel.authUiState.value is AuthUiState.Error)
    }

    // -------------------------------------------------------------------------
    // signUp — username unavailable
    // -------------------------------------------------------------------------

    @Test
    fun `signUp with unavailable username sets Error state`() = runTest {
        viewModel.updateEmailText("new@example.com")
        viewModel.updatePasswordText("password")
        viewModel.updateDisplayNameText("John Doe")
        viewModel.updateUsernameText("taken")

        coEvery { authRepository.isUsernameAvailable("taken") } returns Result.success(false)

        viewModel.signUp()

        assertTrue(viewModel.authUiState.value is AuthUiState.Error)
    }

    // -------------------------------------------------------------------------
    // signUp — success
    // -------------------------------------------------------------------------

    @Test
    fun `signUp success sets Info state with showResendButton true`() = runTest {
        viewModel.updateEmailText("new@example.com")
        viewModel.updatePasswordText("password123")
        viewModel.updateDisplayNameText("New User")
        viewModel.updateUsernameText("newuser")

        coEvery { authRepository.isUsernameAvailable("newuser") } returns Result.success(true)
        coEvery { authRepository.signUp(any(), any(), any(), any()) } returns Result.success(Unit)
        every { resourceProvider.getString(any()) } returns "Verification email sent"

        viewModel.signUp()

        val state = viewModel.authUiState.value
        assertTrue(state is AuthUiState.Info)
        assertTrue((state as AuthUiState.Info).showResendButton)
    }

    // -------------------------------------------------------------------------
    // resetPassword
    // -------------------------------------------------------------------------

    @Test
    fun `resetPassword with blank email sets Error state`() = runTest {
        viewModel.updateEmailText("")
        viewModel.resetPassword()
        assertTrue(viewModel.authUiState.value is AuthUiState.Error)
    }

    @Test
    fun `resetPassword with unregistered email sets Error state`() = runTest {
        viewModel.updateEmailText("unknown@example.com")
        coEvery {
            authRepository.isEmailRegistered("unknown@example.com")
        } returns Result.success(false)

        viewModel.resetPassword()

        assertTrue(viewModel.authUiState.value is AuthUiState.Error)
    }

    @Test
    fun `resetPassword success sets Info state`() = runTest {
        viewModel.updateEmailText("user@example.com")
        coEvery { authRepository.isEmailRegistered("user@example.com") } returns Result.success(true)
        coEvery { authRepository.resetPassword("user@example.com") } returns Result.success(Unit)
        every { resourceProvider.getString(any()) } returns "Reset email sent"

        viewModel.resetPassword()

        assertTrue(viewModel.authUiState.value is AuthUiState.Info)
    }

    // -------------------------------------------------------------------------
    // resendVerificationEmail
    // -------------------------------------------------------------------------

    @Test
    fun `resendVerificationEmail success sets Info with showResendButton true`() = runTest {
        coEvery { authRepository.sendEmailVerification() } returns Result.success(Unit)
        every { resourceProvider.getString(any()) } returns "Email resent"

        viewModel.resendVerificationEmail()

        val state = viewModel.authUiState.value
        assertTrue(state is AuthUiState.Info)
        assertTrue((state as AuthUiState.Info).showResendButton)
    }

    @Test
    fun `resendVerificationEmail failure sets Error state`() = runTest {
        coEvery { authRepository.sendEmailVerification() } returns Result.failure(RuntimeException())

        viewModel.resendVerificationEmail()

        assertTrue(viewModel.authUiState.value is AuthUiState.Error)
    }

    // -------------------------------------------------------------------------
    // submit
    // -------------------------------------------------------------------------

    @Test
    fun `submit in login mode delegates to signIn`() = runTest {
        assertTrue(viewModel.isLoginMode.value)
        // signIn with blank fields → Error
        viewModel.submit()
        assertTrue(viewModel.authUiState.value is AuthUiState.Error)
    }

    @Test
    fun `submit in register mode delegates to signUp`() = runTest {
        viewModel.toggleAuthMode()
        assertFalse(viewModel.isLoginMode.value)
        // signUp with blank fields → Error
        viewModel.submit()
        assertTrue(viewModel.authUiState.value is AuthUiState.Error)
    }
}