package com.jiahan.smartcamera

import com.jiahan.smartcamera.data.datastore.UserPreferences
import com.jiahan.smartcamera.data.datastore.UserPreferencesRepository
import com.jiahan.smartcamera.data.repository.AppUpdateRepository
import com.jiahan.smartcamera.data.repository.AuthRepository
import com.jiahan.smartcamera.data.repository.RemoteConfigRepository
import com.jiahan.smartcamera.data.repository.UserRepository
import com.jiahan.smartcamera.navigation.Screen
import com.jiahan.smartcamera.note.IncomingShareHandler
import com.jiahan.smartcamera.util.ErrorHandler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
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

class MainViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val remoteConfigRepository: RemoteConfigRepository = mockk()
    private val authRepository: AuthRepository = mockk()
    private val userRepository: UserRepository = mockk()
    private val userPreferencesRepository: UserPreferencesRepository = mockk()
    private val incomingShareHandler: IncomingShareHandler = mockk()
    private val appUpdateRepository: AppUpdateRepository = mockk()
    private val errorHandler: ErrorHandler = mockk()

    private val defaultPrefs =
        UserPreferences(isDarkTheme = false, username = "", profilePicture = null)

    @Before
    fun setUp() {
        coEvery { remoteConfigRepository.fetchAndActivateConfig() } returns Result.success(Unit)
        every { errorHandler.logError(any()) } just runs
        every { userPreferencesRepository.userPreferencesFlow } returns flowOf(defaultPrefs)
        every { incomingShareHandler.incomingShare } returns MutableStateFlow(null)
        every { appUpdateRepository.observeUpdateState() } returns flowOf()
        // Default: unauthenticated user. Tests that need a different state override these.
        every { authRepository.currentUserId } returns null
        every { authRepository.isCurrentUserEmailVerified } returns false
        coEvery { userRepository.registerForPushNotifications() } returns Result.success(Unit)
    }

    @After
    fun tearDown() = unmockkAll()

    private fun createViewModel() =
        MainViewModel(
            remoteConfigRepository,
            errorHandler,
            authRepository,
            userRepository,
            userPreferencesRepository,
            incomingShareHandler,
            appUpdateRepository
        )

    // -------------------------------------------------------------------------
    // Startup
    // -------------------------------------------------------------------------
    @Test
    fun `isAppReady is true after init completes`() = runTest {

        val vm = createViewModel()

        assertTrue(vm.uiState.value.isAppReady)
    }

    @Test
    fun `startDestination is Home when user is authenticated and email is verified`() = runTest {
        every { authRepository.currentUserId } returns "uid_123"
        every { authRepository.isCurrentUserEmailVerified } returns true

        val vm = createViewModel()

        assertEquals(Screen.Home.route, vm.uiState.value.startDestination)
        coVerify { userRepository.registerForPushNotifications() }
    }

    @Test
    fun `startDestination is Auth when no user is signed in`() = runTest {

        val vm = createViewModel()

        assertEquals(Screen.Auth.route, vm.uiState.value.startDestination)
        coVerify(exactly = 0) { userRepository.registerForPushNotifications() }
    }

    @Test
    fun `startDestination is Auth when signed in but email not verified`() = runTest {
        every { authRepository.currentUserId } returns "uid_123"
        every { authRepository.isCurrentUserEmailVerified } returns false

        val vm = createViewModel()

        assertEquals(Screen.Auth.route, vm.uiState.value.startDestination)
    }

    @Test
    fun `remote config failure logs error but still marks app as ready`() = runTest {
        val exception = RuntimeException("config unavailable")
        coEvery { remoteConfigRepository.fetchAndActivateConfig() } returns Result.failure(exception)

        val vm = createViewModel()

        verify { errorHandler.logError(exception) }
        assertTrue(vm.uiState.value.isAppReady)
    }

    // -------------------------------------------------------------------------
    // Bottom bar visibility
    // -------------------------------------------------------------------------
    @Test
    fun `showBottomBar is true by default`() = runTest {
        val vm = createViewModel()

        assertTrue(vm.uiState.value.showBottomBar)
    }

    @Test
    fun `updateBottomBarVisibility false hides the bar`() = runTest {
        val vm = createViewModel()

        vm.updateBottomBarVisibility(false)

        assertFalse(vm.uiState.value.showBottomBar)
    }

    @Test
    fun `updateBottomBarVisibility can be toggled back to true`() = runTest {
        val vm = createViewModel()

        vm.updateBottomBarVisibility(false)
        vm.updateBottomBarVisibility(true)

        assertTrue(vm.uiState.value.showBottomBar)
    }

    // -------------------------------------------------------------------------
    // Start destination override
    // -------------------------------------------------------------------------
    @Test
    fun `updateStartDestination overrides the destination`() = runTest {
        val vm = createViewModel()

        vm.updateStartDestination(Screen.Home.route)

        assertEquals(Screen.Home.route, vm.uiState.value.startDestination)
    }

    // -------------------------------------------------------------------------
    // Scroll to top
    // -------------------------------------------------------------------------
    @Test
    fun `scrollToTop is null initially`() = runTest {
        val vm = createViewModel()

        assertNull(vm.uiState.value.scrollToTop)
    }

    @Test
    fun `triggerScrollToTop sets a non-null timestamp`() = runTest {
        val vm = createViewModel()

        vm.triggerScrollToTop()

        assertNotNull(vm.uiState.value.scrollToTop)
    }

    @Test
    fun `consumeScrollToTopEvent clears the timestamp`() = runTest {
        val vm = createViewModel()

        vm.triggerScrollToTop()
        vm.consumeScrollToTopEvent()

        assertNull(vm.uiState.value.scrollToTop)
    }

    // -------------------------------------------------------------------------
    // Notification deep link
    // -------------------------------------------------------------------------
    @Test
    fun `pendingNoteId is null initially`() = runTest {
        val vm = createViewModel()

        assertNull(vm.uiState.value.pendingNoteId)
    }

    @Test
    fun `onNotificationNoteIdReceived sets pendingNoteId`() = runTest {
        val vm = createViewModel()

        vm.onNotificationNoteIdReceived("note_123")

        assertEquals("note_123", vm.uiState.value.pendingNoteId)
    }

    @Test
    fun `consumePendingNoteId clears pendingNoteId`() = runTest {
        val vm = createViewModel()

        vm.onNotificationNoteIdReceived("note_123")
        vm.consumePendingNoteId()

        assertNull(vm.uiState.value.pendingNoteId)
    }
}