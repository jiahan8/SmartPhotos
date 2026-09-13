package com.jiahan.smartcamera.settings

import app.cash.turbine.test
import com.jiahan.smartcamera.fake.FakeAnalyticsRepository
import com.jiahan.smartcamera.fake.FakeAuthRepository
import com.jiahan.smartcamera.fake.FakeErrorHandler
import com.jiahan.smartcamera.fake.FakeUserPreferencesRepository
import com.jiahan.smartcamera.util.ErrorMessage
import com.jiahan.smartcamera.util.ValidationError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [SettingsViewModel]'s suite, in `commonTest` beside its subject: one source for the JVM and the
 * Apple targets, on :core:domain-testing's fakes rather than mockk, with [Dispatchers.setMain]
 * called directly where `MainDispatcherRule` was. `ExploreViewModelTest` records why each of those.
 *
 * Where a mock's `coVerify` checked a repository was or was not called, the fake's call count and
 * last arguments do; where a test held a call in flight with a `CompletableDeferred`, the fake's
 * answer hook awaits it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    // Standard rather than unconfined: signOut and deleteAccount wait AUTH_ACTION_DELAY_MS before
    // navigating, so these tests drive virtual time themselves.
    private val testDispatcher: TestDispatcher = StandardTestDispatcher()

    private val authRepository = FakeAuthRepository()
    private val analyticsRepository = FakeAnalyticsRepository()
    private val userPreferencesRepository = FakeUserPreferencesRepository()
    private val errorHandler = FakeErrorHandler()

    private lateinit var viewModel: SettingsViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = SettingsViewModel(
            authRepository,
            analyticsRepository,
            userPreferencesRepository,
            errorHandler
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
    fun `initial uiState is Idle`() {
        assertEquals(SettingsStatus.Idle, viewModel.uiState.value.status)
    }

    @Test
    fun `initial dialogState is None`() {
        assertEquals(SettingsDialogState.None, viewModel.uiState.value.dialogState)
    }

    // -------------------------------------------------------------------------
    // signOut
    // -------------------------------------------------------------------------

    @Test
    fun `signOut success sends NavigateToAuth event`() = runTest(testDispatcher) {
        authRepository.signOutResult = Result.success(Unit)

        viewModel.navigationEvent.test {
            viewModel.signOut()
            advanceUntilIdle()
            assertEquals(SettingsNavigationEvent.NavigateToAuth, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `signOut success resets uiState to Idle`() = runTest(testDispatcher) {
        authRepository.signOutResult = Result.success(Unit)
        viewModel.signOut()
        advanceUntilIdle()
        assertEquals(SettingsStatus.Idle, viewModel.uiState.value.status)
    }

    @Test
    fun `signOut failure sets Error uiState`() = runTest(testDispatcher) {
        authRepository.signOutResult = Result.failure(RuntimeException("sign out failed"))

        viewModel.signOut()
        advanceUntilIdle()

        val state = viewModel.uiState.value.status
        assertTrue(state is SettingsStatus.Error)
        assertEquals(
            ErrorMessage.Unlocalized("sign out failed"),
            state.message
        )
    }

    // -------------------------------------------------------------------------
    // deleteAccount
    // -------------------------------------------------------------------------

    @Test
    fun `deleteAccount success sends NavigateToAuth event`() = runTest(testDispatcher) {
        authRepository.deleteAccountResult = Result.success(Unit)

        viewModel.navigationEvent.test {
            viewModel.deleteAccount()
            advanceUntilIdle()
            assertEquals(SettingsNavigationEvent.NavigateToAuth, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteAccount success resets uiState to Idle`() = runTest(testDispatcher) {
        authRepository.deleteAccountResult = Result.success(Unit)
        viewModel.deleteAccount()
        advanceUntilIdle()
        assertEquals(SettingsStatus.Idle, viewModel.uiState.value.status)
    }

    @Test
    fun `deleteAccount failure sets Error uiState`() = runTest(testDispatcher) {
        authRepository.deleteAccountResult = Result.failure(RuntimeException("delete failed"))

        viewModel.deleteAccount()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.status is SettingsStatus.Error)
    }

    // -------------------------------------------------------------------------
    // Dialog state
    // -------------------------------------------------------------------------

    @Test
    fun `showLogoutDialog sets dialogState to Logout`() {
        viewModel.showLogoutDialog()
        assertEquals(SettingsDialogState.Logout, viewModel.uiState.value.dialogState)
    }

    @Test
    fun `showDeleteAccountDialog sets dialogState to DeleteAccount`() {
        viewModel.showDeleteAccountDialog()
        assertEquals(SettingsDialogState.DeleteAccount, viewModel.uiState.value.dialogState)
    }

    @Test
    fun `dismissDialog resets dialogState to None`() {
        viewModel.showLogoutDialog()
        viewModel.dismissDialog()
        assertEquals(SettingsDialogState.None, viewModel.uiState.value.dialogState)
    }

    @Test
    fun `showChangePasswordDialog sets dialogState to ChangePassword and clears fields`() {
        viewModel.showChangePasswordDialog()
        viewModel.updateCurrentPassword("stale")
        viewModel.dismissDialog()

        viewModel.showChangePasswordDialog()

        val dialog = viewModel.uiState.value.dialogState as SettingsDialogState.ChangePassword
        assertEquals("", dialog.currentPassword)
        assertEquals("", dialog.newPassword)
        assertEquals("", dialog.confirmNewPassword)
    }

    @Test
    fun `dismissDialog while ChangePassword dialog open clears password fields`() {
        viewModel.showChangePasswordDialog()
        viewModel.updateCurrentPassword("current")
        viewModel.updateNewPassword("newPass1")
        viewModel.updateConfirmNewPassword("newPass1")

        viewModel.dismissDialog()

        assertEquals(SettingsDialogState.None, viewModel.uiState.value.dialogState)
    }

    // -------------------------------------------------------------------------
    // changePassword
    // -------------------------------------------------------------------------

    @Test
    fun `updateNewPassword clears any existing newPasswordError`() = runTest(testDispatcher) {
        viewModel.showChangePasswordDialog()
        viewModel.updateCurrentPassword("current")
        viewModel.changePassword()
        advanceUntilIdle()
        assertEquals(
            ValidationError.PASSWORD_EMPTY,
            (viewModel.uiState.value.dialogState as SettingsDialogState.ChangePassword)
                .newPasswordError
        )

        viewModel.updateNewPassword("newPass1")

        assertEquals(
            null,
            (viewModel.uiState.value.dialogState as SettingsDialogState.ChangePassword)
                .newPasswordError
        )
    }

    @Test
    fun `updateConfirmNewPassword mismatch sets confirmNewPasswordError`() {
        viewModel.showChangePasswordDialog()
        viewModel.updateNewPassword("newPass1")
        viewModel.updateConfirmNewPassword("different")
        assertEquals(
            ConfirmPasswordError.MISMATCH,
            (viewModel.uiState.value.dialogState as SettingsDialogState.ChangePassword)
                .confirmNewPasswordError
        )
    }

    @Test
    fun `updateConfirmNewPassword match clears confirmNewPasswordError`() {
        viewModel.showChangePasswordDialog()
        viewModel.updateNewPassword("newPass1")
        viewModel.updateConfirmNewPassword("newPass1")
        assertEquals(
            null,
            (viewModel.uiState.value.dialogState as SettingsDialogState.ChangePassword)
                .confirmNewPasswordError
        )
    }

    @Test
    fun `changePassword with blank new password does not call repository`() =
        runTest(testDispatcher) {
            viewModel.showChangePasswordDialog()
            viewModel.updateCurrentPassword("current")
            viewModel.changePassword()
            advanceUntilIdle()

            assertEquals(0, authRepository.changePasswordCallCount)
            assertEquals(
                ValidationError.PASSWORD_EMPTY,
                (viewModel.uiState.value.dialogState as SettingsDialogState.ChangePassword)
                    .newPasswordError
            )
        }

    @Test
    fun `changePassword with mismatched confirm password does not call repository`() =
        runTest(testDispatcher) {
            viewModel.showChangePasswordDialog()
            viewModel.updateCurrentPassword("current")
            viewModel.updateNewPassword("newPass1")
            viewModel.updateConfirmNewPassword("different")
            viewModel.changePassword()
            advanceUntilIdle()

            assertEquals(0, authRepository.changePasswordCallCount)
            assertEquals(
                ConfirmPasswordError.MISMATCH,
                (viewModel.uiState.value.dialogState as SettingsDialogState.ChangePassword)
                    .confirmNewPasswordError
            )
        }

    // No comma in the name: Kotlin/Native rejects one, and this suite compiles for the Apple targets.
    @Test
    fun `changePassword success calls the repository then dismisses the dialog and emits the event`() =
        runTest(testDispatcher) {
            authRepository.changePasswordResult = Result.success(Unit)

            viewModel.showChangePasswordDialog()
            viewModel.updateCurrentPassword("current")
            viewModel.updateNewPassword("newPass1")
            viewModel.updateConfirmNewPassword("newPass1")

            viewModel.changePasswordEvent.test {
                viewModel.changePassword()
                advanceUntilIdle()
                assertEquals(SettingsChangePasswordEvent.Success, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals("current" to "newPass1", authRepository.lastChangePasswordArgs)
            val state = viewModel.uiState.value
            assertEquals(SettingsDialogState.None, state.dialogState)
            assertEquals(SettingsStatus.Idle, state.status)
        }

    @Test
    fun `changePassword sets Loading status while repository call is in flight`() =
        runTest(testDispatcher) {
            val deferredResult = CompletableDeferred<Result<Unit>>()
            authRepository.changePasswordAnswer = { _, _ -> deferredResult.await() }

            viewModel.showChangePasswordDialog()
            viewModel.updateCurrentPassword("current")
            viewModel.updateNewPassword("newPass1")
            viewModel.updateConfirmNewPassword("newPass1")

            viewModel.changePassword()
            runCurrent()

            assertEquals(SettingsStatus.Loading, viewModel.uiState.value.status)

            deferredResult.complete(Result.success(Unit))
            advanceUntilIdle()

            assertEquals(SettingsStatus.Idle, viewModel.uiState.value.status)
        }

    @Test
    fun `changePassword failure sets Error uiState and leaves dialog open`() =
        runTest(testDispatcher) {
            authRepository.changePasswordResult =
                Result.failure(RuntimeException("wrong password"))

            viewModel.showChangePasswordDialog()
            viewModel.updateCurrentPassword("wrong")
            viewModel.updateNewPassword("newPass1")
            viewModel.updateConfirmNewPassword("newPass1")
            viewModel.changePassword()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.status is SettingsStatus.Error)
            assertEquals(
                ErrorMessage.Unlocalized("wrong password"),
                state.status.message
            )
            assertTrue(state.dialogState is SettingsDialogState.ChangePassword)
        }

    // -------------------------------------------------------------------------
    // Navigation & misc
    // -------------------------------------------------------------------------

    @Test
    fun `openLanguageSettings sends OpenLanguageSettings event`() = runTest(testDispatcher) {
        viewModel.navigationEvent.test {
            viewModel.openLanguageSettings()
            assertEquals(SettingsNavigationEvent.OpenLanguageSettings, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dismissError resets uiState to Idle`() = runTest(testDispatcher) {
        authRepository.signOutResult = Result.failure(RuntimeException("err"))
        viewModel.signOut()
        advanceUntilIdle()

        viewModel.dismissError()

        assertEquals(SettingsStatus.Idle, viewModel.uiState.value.status)
    }

    @Test
    fun `setDarkTheme delegates to userPreferencesRepository`() = runTest(testDispatcher) {
        viewModel.setDarkTheme(true)
        advanceUntilIdle()

        assertTrue(userPreferencesRepository.userPreferences.first().isDarkTheme)
    }

    @Test
    fun `setDarkTheme failure is logged silently`() = runTest(testDispatcher) {
        val exception = RuntimeException("pref error")
        userPreferencesRepository.setDarkThemeResult = Result.failure(exception)

        viewModel.setDarkTheme(false)
        advanceUntilIdle()

        assertEquals(listOf<Throwable>(exception), errorHandler.loggedErrors)
        // uiState unchanged — no error shown to user
        assertEquals(SettingsStatus.Idle, viewModel.uiState.value.status)
    }
}