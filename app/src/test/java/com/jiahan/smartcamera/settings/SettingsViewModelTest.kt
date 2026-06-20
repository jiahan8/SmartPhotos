package com.jiahan.smartcamera.settings

import app.cash.turbine.test
import com.jiahan.smartcamera.MainDispatcherRule
import com.jiahan.smartcamera.data.datastore.UserPreferences
import com.jiahan.smartcamera.data.datastore.UserPreferencesRepository
import com.jiahan.smartcamera.data.repository.AuthRepository
import com.jiahan.smartcamera.util.ErrorHandler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.verify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
    private val userPreferencesRepository: UserPreferencesRepository = mockk()
    private val errorHandler: ErrorHandler = mockk()

    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        every { errorHandler.logError(any()) } just runs
        every { errorHandler.getErrorMessage(any()) } returns "An error occurred"
        every { userPreferencesRepository.userPreferencesFlow } returns
                flowOf(UserPreferences(isDarkTheme = false, username = "", profilePicture = null))
        viewModel = SettingsViewModel(authRepository, userPreferencesRepository, errorHandler)
    }

    @After
    fun tearDown() = unmockkAll()

    // -------------------------------------------------------------------------
    // Initial state
    // -------------------------------------------------------------------------

    @Test
    fun `initial uiState is Idle`() {
        assertEquals(SettingsUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `initial dialogState is None`() {
        assertEquals(SettingsDialogState.None, viewModel.dialogState.value)
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
        assertEquals(SettingsUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `signOut failure sets Error uiState`() = runTest(mainDispatcherRule.testDispatcher) {
        val exception = RuntimeException("sign out failed")
        coEvery { authRepository.signOut() } returns Result.failure(exception)
        every { errorHandler.getErrorMessage(exception) } returns "sign out failed"

        viewModel.signOut()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is SettingsUiState.Error)
        assertEquals("sign out failed", (state as SettingsUiState.Error).message)
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
            assertEquals(SettingsUiState.Idle, viewModel.uiState.value)
        }

    @Test
    fun `deleteAccount failure sets Error uiState`() = runTest(mainDispatcherRule.testDispatcher) {
        val exception = RuntimeException("delete failed")
        coEvery { authRepository.deleteAccount() } returns Result.failure(exception)
        every { errorHandler.getErrorMessage(exception) } returns "delete failed"

        viewModel.deleteAccount()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is SettingsUiState.Error)
    }

    // -------------------------------------------------------------------------
    // Dialog state
    // -------------------------------------------------------------------------

    @Test
    fun `showLogoutDialog sets dialogState to Logout`() {
        viewModel.showLogoutDialog()
        assertEquals(SettingsDialogState.Logout, viewModel.dialogState.value)
    }

    @Test
    fun `showDeleteAccountDialog sets dialogState to DeleteAccount`() {
        viewModel.showDeleteAccountDialog()
        assertEquals(SettingsDialogState.DeleteAccount, viewModel.dialogState.value)
    }

    @Test
    fun `dismissDialog resets dialogState to None`() {
        viewModel.showLogoutDialog()
        viewModel.dismissDialog()
        assertEquals(SettingsDialogState.None, viewModel.dialogState.value)
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
    fun `resetActionError resets uiState to Idle`() = runTest(mainDispatcherRule.testDispatcher) {
        val exception = RuntimeException("err")
        coEvery { authRepository.signOut() } returns Result.failure(exception)
        viewModel.signOut()
        advanceUntilIdle()

        viewModel.resetActionError()

        assertEquals(SettingsUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `updateDarkThemeVisibility delegates to userPreferencesRepository`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { userPreferencesRepository.updateDarkThemeVisibility(true) } returns
                    Result.success(Unit)

            viewModel.updateDarkThemeVisibility(true)
            advanceUntilIdle()

            coVerify { userPreferencesRepository.updateDarkThemeVisibility(true) }
        }

    @Test
    fun `updateDarkThemeVisibility failure is logged silently`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val exception = RuntimeException("pref error")
            coEvery { userPreferencesRepository.updateDarkThemeVisibility(any()) } returns
                    Result.failure(exception)

            viewModel.updateDarkThemeVisibility(false)
            advanceUntilIdle()

            verify { errorHandler.logError(exception) }
            // uiState unchanged — no error shown to user
            assertEquals(SettingsUiState.Idle, viewModel.uiState.value)
        }
}