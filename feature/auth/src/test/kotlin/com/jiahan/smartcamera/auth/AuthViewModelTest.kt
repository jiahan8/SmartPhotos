package com.jiahan.smartcamera.auth

import app.cash.turbine.test
import com.jiahan.smartcamera.MainDispatcherRule
import com.jiahan.smartcamera.data.datastore.UserPreferencesRepository
import com.jiahan.smartcamera.data.repository.AnalyticsRepository
import com.jiahan.smartcamera.data.repository.AuthRepository
import com.jiahan.smartcamera.data.repository.UserRepository
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.ErrorMessage
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    private val errorHandler: ErrorHandler = mockk()

    private lateinit var viewModel: AuthViewModel

    @Before
    fun setUp() {
        every { analyticsRepository.logText(any()) } just runs
        every { analyticsRepository.logDisplayName(any()) } just runs
        every { analyticsRepository.logUsername(any()) } just runs
        every { analyticsRepository.setUserId(any()) } just runs
        every { authRepository.currentUserId } returns "test-uid"
        every { errorHandler.logError(any()) } just runs
        coEvery { userRepository.registerForPushNotifications() } returns Result.success(Unit)

        viewModel = AuthViewModel(
            authRepository, userRepository, userPreferencesRepository,
            analyticsRepository, errorHandler
        )
    }

    @After
    fun tearDown() = unmockkAll()

    // -------------------------------------------------------------------------
    // Field updates
    // -------------------------------------------------------------------------

    @Test
    fun `updateEmail updates email StateFlow`() {
        viewModel.updateEmail("user@example.com")
        assertEquals("user@example.com", viewModel.uiState.value.email)
    }

    @Test
    fun `updatePassword updates password StateFlow`() {
        viewModel.updatePassword("secret123")
        assertEquals("secret123", viewModel.uiState.value.password)
    }

    @Test
    fun `updateDisplayName updates displayName StateFlow`() {
        viewModel.updateDisplayName("Jane Doe")
        assertEquals("Jane Doe", viewModel.uiState.value.displayName)
    }

    @Test
    fun `updateUsername updates username StateFlow`() {
        viewModel.updateUsername("janedoe")
        assertEquals("janedoe", viewModel.uiState.value.username)
    }

    @Test
    fun `updatePasswordVisibility true shows password`() {
        viewModel.updatePasswordVisibility(true)
        assertTrue(viewModel.uiState.value.isPasswordVisible)
    }

    @Test
    fun `updatePasswordVisibility false hides password`() {
        viewModel.updatePasswordVisibility(true)
        viewModel.updatePasswordVisibility(false)
        assertFalse(viewModel.uiState.value.isPasswordVisible)
    }

    // -------------------------------------------------------------------------
    // toggleAuthMode
    // -------------------------------------------------------------------------

    @Test
    fun `toggleAuthMode switches from login to register mode`() {
        assertTrue(viewModel.uiState.value.isLoginMode)
        viewModel.toggleAuthMode()
        assertFalse(viewModel.uiState.value.isLoginMode)
    }

    @Test
    fun `toggleAuthMode clears all fields`() {
        viewModel.updateEmail("test@example.com")
        viewModel.updatePassword("pass")
        viewModel.updateDisplayName("Test")
        viewModel.updateUsername("testuser")

        viewModel.toggleAuthMode()

        assertEquals("", viewModel.uiState.value.email)
        assertEquals("", viewModel.uiState.value.password)
        assertEquals("", viewModel.uiState.value.displayName)
        assertEquals("", viewModel.uiState.value.username)
    }

    @Test
    fun `toggleAuthMode resets authUiState to Idle`() {
        viewModel.toggleAuthMode()
        assertEquals(AuthStatus.Idle, viewModel.uiState.value.status)
    }

    // -------------------------------------------------------------------------
    // signIn — validation
    // -------------------------------------------------------------------------

    @Test
    fun `signIn with blank email sets Error state without network call`() = runTest {
        viewModel.updateEmail("   ")
        viewModel.updatePassword("password")

        viewModel.signIn()

        assertTrue(viewModel.uiState.value.status is AuthStatus.Error)
    }

    @Test
    fun `signIn with blank password sets Error state without network call`() = runTest {
        viewModel.updateEmail("user@example.com")
        // password left empty

        viewModel.signIn()

        assertTrue(viewModel.uiState.value.status is AuthStatus.Error)
    }

    // -------------------------------------------------------------------------
    // signIn — success path
    // -------------------------------------------------------------------------

    @Test
    fun `signIn success with verified email sends NavigateToHome event`() = runTest {
        viewModel.updateEmail("user@example.com")
        viewModel.updatePassword("password123")

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
        viewModel.updateEmail("user@example.com")
        viewModel.updatePassword("password123")

        coEvery { authRepository.signIn(any(), any()) } returns Result.success(Unit)
        coEvery { authRepository.checkEmailVerified() } returns Result.success(true)
        coEvery { userRepository.getUser() } returns Result.success(null)
        coEvery {
            userPreferencesRepository.updateLocalUserProfile(any(), any())
        } returns Result.success(Unit)

        viewModel.signIn()

        assertEquals(AuthStatus.Idle, viewModel.uiState.value.status)
    }

    @Test
    fun `signIn success with unverified email sets Error with isResendButtonVisible true`() = runTest {
        viewModel.updateEmail("user@example.com")
        viewModel.updatePassword("password123")

        coEvery { authRepository.signIn(any(), any()) } returns Result.success(Unit)
        coEvery { authRepository.checkEmailVerified() } returns Result.success(false)

        viewModel.signIn()

        assertEquals(AuthStatus.Error(AuthError.EmailNotVerified), viewModel.uiState.value.status)
        assertTrue(viewModel.uiState.value.isResendButtonVisible)
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
            analyticsRepository, errorHandler
        )
        vm.updateEmail("user@example.com")
        vm.updatePassword("password123")
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

        vm.uiState.map { it.status }.distinctUntilChanged().test {
            assertEquals(AuthStatus.Idle, awaitItem()) // initial value
            vm.signIn()
            advanceTimeBy(1.milliseconds) // let the launch start; suspends at delay(1s)
            assertEquals(AuthStatus.Loading, awaitItem())
            advanceUntilIdle() // complete the delay → signIn finishes
            assertEquals(AuthStatus.Idle, awaitItem())
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
            analyticsRepository, errorHandler
        )
        vm.updateEmail("new@example.com")
        vm.updatePassword("password123")
        vm.updateDisplayName("New User")
        vm.updateUsername("newuser")
        coEvery { authRepository.isUsernameAvailable("newuser") } coAnswers {
            delay(1.seconds); Result.success(true)
        }
        coEvery { authRepository.signUp(any(), any(), any(), any()) } returns Result.success(Unit)

        vm.uiState.map { it.status }.distinctUntilChanged().test {
            assertEquals(AuthStatus.Idle, awaitItem())
            vm.signUp()
            advanceTimeBy(1.milliseconds)
            assertEquals(AuthStatus.Loading, awaitItem())
            advanceUntilIdle()
            assertTrue(awaitItem() is AuthStatus.Info)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // signIn — failure path
    // -------------------------------------------------------------------------

    @Test
    fun `signIn network failure sets Error state`() = runTest {
        viewModel.updateEmail("user@example.com")
        viewModel.updatePassword("wrongpass")

        val exception = RuntimeException("Invalid credentials")
        coEvery { authRepository.signIn(any(), any()) } returns Result.failure(exception)

        viewModel.signIn()

        val state = viewModel.uiState.value.status
        assertTrue(state is AuthStatus.Error)
        assertEquals(
            AuthError.Failed(ErrorMessage.Unlocalized("Invalid credentials")),
            (state as AuthStatus.Error).error
        )
    }

    @Test
    fun `signIn checkEmailVerified failure sets Error state`() = runTest {
        viewModel.updateEmail("user@example.com")
        viewModel.updatePassword("password123")

        coEvery { authRepository.signIn(any(), any()) } returns Result.success(Unit)
        val exception = RuntimeException("verification check failed")
        coEvery { authRepository.checkEmailVerified() } returns Result.failure(exception)

        viewModel.signIn()

        val state = viewModel.uiState.value.status
        assertTrue(state is AuthStatus.Error)
        assertEquals(
            AuthError.Failed(ErrorMessage.Unlocalized("verification check failed")),
            (state as AuthStatus.Error).error
        )
        assertFalse(viewModel.uiState.value.isResendButtonVisible)
    }

    // -------------------------------------------------------------------------
    // signUp — validation
    // -------------------------------------------------------------------------

    @Test
    fun `signUp with blank email sets Error state`() = runTest {
        viewModel.updateEmail("")
        viewModel.updatePassword("password")
        viewModel.updateDisplayName("John")
        viewModel.updateUsername("johndoe")

        viewModel.signUp()

        assertTrue(viewModel.uiState.value.status is AuthStatus.Error)
    }

    @Test
    fun `signUp with blank displayName sets Error state`() = runTest {
        viewModel.updateEmail("new@example.com")
        viewModel.updatePassword("password")
        viewModel.updateDisplayName("")
        viewModel.updateUsername("johndoe")

        viewModel.signUp()

        assertTrue(viewModel.uiState.value.status is AuthStatus.Error)
    }

    @Test
    fun `signUp with blank username sets Error state`() = runTest {
        viewModel.updateEmail("new@example.com")
        viewModel.updatePassword("password")
        viewModel.updateDisplayName("John Doe")
        viewModel.updateUsername("")

        viewModel.signUp()

        assertTrue(viewModel.uiState.value.status is AuthStatus.Error)
    }

    @Test
    fun `signUp with too long displayName sets Error state`() = runTest {
        viewModel.updateEmail("new@example.com")
        viewModel.updatePassword("password")
        viewModel.updateDisplayName("a".repeat(51)) // MAX = 50
        viewModel.updateUsername("johndoe")

        viewModel.signUp()

        assertTrue(viewModel.uiState.value.status is AuthStatus.Error)
    }

    @Test
    fun `signUp with invalid username characters sets Error state`() = runTest {
        viewModel.updateEmail("new@example.com")
        viewModel.updatePassword("password")
        viewModel.updateDisplayName("John Doe")
        viewModel.updateUsername("user name!") // space and ! not allowed

        viewModel.signUp()

        assertTrue(viewModel.uiState.value.status is AuthStatus.Error)
    }

    // -------------------------------------------------------------------------
    // signUp — username unavailable
    // -------------------------------------------------------------------------

    @Test
    fun `signUp with unavailable username sets Error state`() = runTest {
        viewModel.updateEmail("new@example.com")
        viewModel.updatePassword("password")
        viewModel.updateDisplayName("John Doe")
        viewModel.updateUsername("taken")

        coEvery { authRepository.isUsernameAvailable("taken") } returns Result.success(false)

        viewModel.signUp()

        assertTrue(viewModel.uiState.value.status is AuthStatus.Error)
    }

    // -------------------------------------------------------------------------
    // signUp — success
    // -------------------------------------------------------------------------

    @Test
    fun `signUp success sets Info state with isResendButtonVisible true`() = runTest {
        viewModel.updateEmail("new@example.com")
        viewModel.updatePassword("password123")
        viewModel.updateDisplayName("New User")
        viewModel.updateUsername("newuser")

        coEvery { authRepository.isUsernameAvailable("newuser") } returns Result.success(true)
        coEvery { authRepository.signUp(any(), any(), any(), any()) } returns Result.success(Unit)

        viewModel.signUp()

        assertEquals(
            AuthStatus.Info(AuthNotice.VERIFICATION_EMAIL_SENT),
            viewModel.uiState.value.status
        )
        assertTrue(viewModel.uiState.value.isResendButtonVisible)
    }

    // -------------------------------------------------------------------------
    // signUp — failure path
    // -------------------------------------------------------------------------

    @Test
    fun `signUp isUsernameAvailable failure sets Error state`() = runTest {
        viewModel.updateEmail("new@example.com")
        viewModel.updatePassword("password")
        viewModel.updateDisplayName("John Doe")
        viewModel.updateUsername("johndoe")

        val exception = RuntimeException("network down")
        coEvery { authRepository.isUsernameAvailable("johndoe") } returns Result.failure(exception)

        viewModel.signUp()

        val state = viewModel.uiState.value.status
        assertTrue(state is AuthStatus.Error)
        assertEquals(
            AuthError.Failed(ErrorMessage.Unlocalized("network down")),
            (state as AuthStatus.Error).error
        )
    }

    @Test
    fun `signUp repository failure sets Error state`() = runTest {
        viewModel.updateEmail("new@example.com")
        viewModel.updatePassword("password123")
        viewModel.updateDisplayName("New User")
        viewModel.updateUsername("newuser")

        coEvery { authRepository.isUsernameAvailable("newuser") } returns Result.success(true)
        val exception = RuntimeException("signup failed")
        coEvery { authRepository.signUp(any(), any(), any(), any()) } returns
                Result.failure(exception)

        viewModel.signUp()

        val state = viewModel.uiState.value.status
        assertTrue(state is AuthStatus.Error)
        assertEquals(
            AuthError.Failed(ErrorMessage.Unlocalized("signup failed")),
            (state as AuthStatus.Error).error
        )
        assertFalse(viewModel.uiState.value.isResendButtonVisible)
    }

    // -------------------------------------------------------------------------
    // resetPassword
    // -------------------------------------------------------------------------

    @Test
    fun `resetPassword with blank email sets Error state`() = runTest {
        viewModel.updateEmail("")
        viewModel.resetPassword()
        assertTrue(viewModel.uiState.value.status is AuthStatus.Error)
    }

    @Test
    fun `resetPassword with unregistered email sets Error state`() = runTest {
        viewModel.updateEmail("unknown@example.com")
        coEvery {
            authRepository.isEmailRegistered("unknown@example.com")
        } returns Result.success(false)

        viewModel.resetPassword()

        assertTrue(viewModel.uiState.value.status is AuthStatus.Error)
    }

    @Test
    fun `resetPassword success sets Info state`() = runTest {
        viewModel.updateEmail("user@example.com")
        coEvery { authRepository.isEmailRegistered("user@example.com") } returns Result.success(true)
        coEvery { authRepository.resetPassword("user@example.com") } returns Result.success(Unit)

        viewModel.resetPassword()

        assertEquals(
            AuthStatus.Info(AuthNotice.PASSWORD_RESET_EMAIL_SENT),
            viewModel.uiState.value.status
        )
    }

    @Test
    fun `resetPassword repository failure sets Error state`() = runTest {
        viewModel.updateEmail("user@example.com")
        coEvery {
            authRepository.isEmailRegistered("user@example.com")
        } returns Result.success(true)
        val exception = RuntimeException("reset failed")
        coEvery { authRepository.resetPassword("user@example.com") } returns
                Result.failure(exception)

        viewModel.resetPassword()

        val state = viewModel.uiState.value.status
        assertTrue(state is AuthStatus.Error)
        assertEquals(
            AuthError.Failed(ErrorMessage.Unlocalized("reset failed")),
            (state as AuthStatus.Error).error
        )
    }

    // -------------------------------------------------------------------------
    // resendVerificationEmail
    // -------------------------------------------------------------------------

    @Test
    fun `resendVerificationEmail success sets Info with isResendButtonVisible true`() = runTest {
        coEvery { authRepository.sendEmailVerification() } returns Result.success(Unit)

        viewModel.resendVerificationEmail()

        assertEquals(
            AuthStatus.Info(AuthNotice.VERIFICATION_EMAIL_RESENT),
            viewModel.uiState.value.status
        )
        assertTrue(viewModel.uiState.value.isResendButtonVisible)
    }

    @Test
    fun `resendVerificationEmail failure sets Error state`() = runTest {
        coEvery { authRepository.sendEmailVerification() } returns Result.failure(RuntimeException())

        viewModel.resendVerificationEmail()

        // The exception carries no message, so the generic string rather than a blank line.
        assertEquals(
            AuthStatus.Error(AuthError.Failed(ErrorMessage.Generic)),
            viewModel.uiState.value.status
        )
    }

    // -------------------------------------------------------------------------
    // submit
    // -------------------------------------------------------------------------

    @Test
    fun `submit in login mode delegates to signIn`() = runTest {
        assertTrue(viewModel.uiState.value.isLoginMode)
        // signIn with blank fields → Error
        viewModel.submit()
        assertTrue(viewModel.uiState.value.status is AuthStatus.Error)
    }

    @Test
    fun `submit in register mode delegates to signUp`() = runTest {
        viewModel.toggleAuthMode()
        assertFalse(viewModel.uiState.value.isLoginMode)
        // signUp with blank fields → Error
        viewModel.submit()
        assertTrue(viewModel.uiState.value.status is AuthStatus.Error)
    }
}