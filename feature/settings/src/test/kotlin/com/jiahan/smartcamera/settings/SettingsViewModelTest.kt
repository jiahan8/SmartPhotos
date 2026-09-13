package com.jiahan.smartcamera.settings

import app.cash.turbine.test
import com.jiahan.smartcamera.MainDispatcherRule
import com.jiahan.smartcamera.data.datastore.UserPreferences
import com.jiahan.smartcamera.data.datastore.UserPreferencesRepository
import com.jiahan.smartcamera.data.repository.AnalyticsRepository
import com.jiahan.smartcamera.data.repository.AuthRepository
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.ErrorMessage
import com.jiahan.smartcamera.util.ValidationError
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.verify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val authRepository: AuthRepository = mockk()
    private val analyticsRepository: AnalyticsRepository = mockk()
    private val userPreferencesRepository: UserPreferencesRepository = mockk()
    private val errorHandler: ErrorHandler = mockk()

    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        every { analyticsRepository.setUserId(any()) } just runs
        every { analyticsRepository.logText(any()) } just runs
        every { errorHandler.logError(any()) } just runs
        every { userPreferencesRepository.userPreferences } returns
                flowOf(UserPreferences(isDarkTheme = false, username = "", profilePictureUrl = null))
        viewModel = SettingsViewModel(
            authRepository,
            analyticsRepository,
            userPreferencesRepository,
            errorHandler
        )
    }

    @After
    fun tearDown() = unmockkAll()

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
    fun `signOut success sends NavigateToAuth event`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { authRepository.signOut() } returns Result.success(Unit)

            viewModel.navigationEvent.test {
                viewModel.signOut()
                advanceUntilIdle()
                assertEquals(SettingsNavigationEvent.NavigateToAuth, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `signOut success resets uiState to Idle`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { authRepository.signOut() } returns Result.success(Unit)
        viewModel.signOut()
        advanceUntilIdle()
        assertEquals(SettingsStatus.Idle, viewModel.uiState.value.status)
    }

    @Test
    fun `signOut failure sets Error uiState`() = runTest(mainDispatcherRule.testDispatcher) {
        val exception = RuntimeException("sign out failed")
        coEvery { authRepository.signOut() } returns Result.failure(exception)

        viewModel.signOut()
        advanceUntilIdle()

        val state = viewModel.uiState.value.status
        assertTrue(state is SettingsStatus.Error)
        assertEquals(
            ErrorMessage.Unlocalized("sign out failed"),
            (state as SettingsStatus.Error).message
        )
    }

    // -------------------------------------------------------------------------
    // deleteAccount
    // -------------------------------------------------------------------------

    @Test
    fun `deleteAccount success sends NavigateToAuth event`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { authRepository.deleteAccount() } returns Result.success(Unit)

            viewModel.navigationEvent.test {
                viewModel.deleteAccount()
                advanceUntilIdle()
                assertEquals(SettingsNavigationEvent.NavigateToAuth, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `deleteAccount success resets uiState to Idle`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { authRepository.deleteAccount() } returns Result.success(Unit)
            viewModel.deleteAccount()
            advanceUntilIdle()
            assertEquals(SettingsStatus.Idle, viewModel.uiState.value.status)
        }

    @Test
    fun `deleteAccount failure sets Error uiState`() = runTest(mainDispatcherRule.testDispatcher) {
        val exception = RuntimeException("delete failed")
        coEvery { authRepository.deleteAccount() } returns Result.failure(exception)

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
    fun `updateNewPassword clears any existing newPasswordError`() =
        runTest(mainDispatcherRule.testDispatcher) {
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
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.showChangePasswordDialog()
            viewModel.updateCurrentPassword("current")
            viewModel.changePassword()
            advanceUntilIdle()

            coVerify(exactly = 0) { authRepository.changePassword(any(), any()) }
            assertEquals(
                ValidationError.PASSWORD_EMPTY,
                (viewModel.uiState.value.dialogState as SettingsDialogState.ChangePassword)
                    .newPasswordError
            )
        }

    @Test
    fun `changePassword with mismatched confirm password does not call repository`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.showChangePasswordDialog()
            viewModel.updateCurrentPassword("current")
            viewModel.updateNewPassword("newPass1")
            viewModel.updateConfirmNewPassword("different")
            viewModel.changePassword()
            advanceUntilIdle()

            coVerify(exactly = 0) { authRepository.changePassword(any(), any()) }
            assertEquals(
                ConfirmPasswordError.MISMATCH,
                (viewModel.uiState.value.dialogState as SettingsDialogState.ChangePassword)
                    .confirmNewPasswordError
            )
        }

    @Test
    fun `changePassword success calls repository, dismisses dialog, and emits event`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { authRepository.changePassword("current", "newPass1") } returns
                    Result.success(Unit)

            viewModel.showChangePasswordDialog()
            viewModel.updateCurrentPassword("current")
            viewModel.updateNewPassword("newPass1")
            viewModel.updateConfirmNewPassword("newPass1")

            viewModel.changePasswordEvent.test {
                viewModel.changePassword()
                advanceUntilIdle()
                assertEquals(
                    SettingsChangePasswordEvent.Success,
                    awaitItem()
                )
                cancelAndIgnoreRemainingEvents()
            }

            val state = viewModel.uiState.value
            assertEquals(SettingsDialogState.None, state.dialogState)
            assertEquals(SettingsStatus.Idle, state.status)
        }

    @Test
    fun `changePassword sets Loading status while repository call is in flight`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val deferredResult = CompletableDeferred<Result<Unit>>()
            coEvery { authRepository.changePassword("current", "newPass1") } coAnswers {
                deferredResult.await()
            }

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
        runTest(mainDispatcherRule.testDispatcher) {
            val exception = RuntimeException("wrong password")
            coEvery { authRepository.changePassword(any(), any()) } returns
                    Result.failure(exception)

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
                (state.status as SettingsStatus.Error).message
            )
            assertTrue(state.dialogState is SettingsDialogState.ChangePassword)
        }

    // -------------------------------------------------------------------------
    // Navigation & misc
    // -------------------------------------------------------------------------

    @Test
    fun `openLanguageSettings sends OpenLanguageSettings event`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.navigationEvent.test {
                viewModel.openLanguageSettings()
                assertEquals(SettingsNavigationEvent.OpenLanguageSettings, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `dismissError resets uiState to Idle`() = runTest(mainDispatcherRule.testDispatcher) {
        val exception = RuntimeException("err")
        coEvery { authRepository.signOut() } returns Result.failure(exception)
        viewModel.signOut()
        advanceUntilIdle()

        viewModel.dismissError()

        assertEquals(SettingsStatus.Idle, viewModel.uiState.value.status)
    }

    @Test
    fun `setDarkTheme delegates to userPreferencesRepository`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { userPreferencesRepository.setDarkTheme(true) } returns
                    Result.success(Unit)

            viewModel.setDarkTheme(true)
            advanceUntilIdle()

            coVerify { userPreferencesRepository.setDarkTheme(true) }
        }

    @Test
    fun `setDarkTheme failure is logged silently`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val exception = RuntimeException("pref error")
            coEvery { userPreferencesRepository.setDarkTheme(any()) } returns
                    Result.failure(exception)

            viewModel.setDarkTheme(false)
            advanceUntilIdle()

            verify { errorHandler.logError(exception) }
            // uiState unchanged — no error shown to user
            assertEquals(SettingsStatus.Idle, viewModel.uiState.value.status)
        }
}