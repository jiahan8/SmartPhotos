package com.jiahan.smartcamera

import com.jiahan.smartcamera.data.datastore.UserPreferences
import com.jiahan.smartcamera.data.datastore.UserPreferencesRepository
import com.jiahan.smartcamera.data.repository.AuthRepository
import com.jiahan.smartcamera.data.repository.RemoteConfigRepository
import com.jiahan.smartcamera.navigation.Screen
import com.jiahan.smartcamera.util.ErrorHandler
import io.mockk.coEvery
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
    private val errorHandler: ErrorHandler = mockk()
    private val authRepository: AuthRepository = mockk()
    private val userPreferencesRepository: UserPreferencesRepository = mockk()

    private val defaultPrefs =
        UserPreferences(isDarkTheme = false, username = "", profilePicture = null)

    @Before
    fun setUp() {
        coEvery { remoteConfigRepository.fetchAndActivateConfig() } returns Result.success(Unit)
        every { errorHandler.logError(any()) } just runs
        every { userPreferencesRepository.userPreferencesFlow } returns flowOf(defaultPrefs)
        // Default: unauthenticated user. Tests that need a different state override these.
        every { authRepository.currentUserId } returns null
        every { authRepository.isCurrentUserEmailVerified } returns false
    }

    @After
    fun tearDown() = unmockkAll()

    private fun createViewModel() =
        MainViewModel(
            remoteConfigRepository,
            errorHandler,
            authRepository,
            userPreferencesRepository
        )

    // -------------------------------------------------------------------------
    // Startup
    // -------------------------------------------------------------------------
    @Test
    fun `isAppReady is true after init completes`() = runTest {

        val vm = createViewModel()

        assertTrue(vm.isAppReady.value)
    }

    @Test
    fun `startDestination is Home when user is authenticated and email is verified`() = runTest {
        every { authRepository.currentUserId } returns "uid_123"
        every { authRepository.isCurrentUserEmailVerified } returns true

        val vm = createViewModel()

        assertEquals(Screen.Home.route, vm.startDestination.value)
    }

    @Test
    fun `startDestination is Auth when no user is signed in`() = runTest {

        val vm = createViewModel()

        assertEquals(Screen.Auth.route, vm.startDestination.value)
    }

    @Test
    fun `startDestination is Auth when signed in but email not verified`() = runTest {
        every { authRepository.currentUserId } returns "uid_123"
        every { authRepository.isCurrentUserEmailVerified } returns false

        val vm = createViewModel()

        assertEquals(Screen.Auth.route, vm.startDestination.value)
    }

    @Test
    fun `remote config failure logs error but still marks app as ready`() = runTest {
        val exception = RuntimeException("config unavailable")
        coEvery { remoteConfigRepository.fetchAndActivateConfig() } returns Result.failure(exception)

        val vm = createViewModel()

        verify { errorHandler.logError(exception) }
        assertTrue(vm.isAppReady.value)
    }

    // -------------------------------------------------------------------------
    // Bottom bar visibility
    // -------------------------------------------------------------------------
    @Test
    fun `showBottomBar is true by default`() = runTest {
        val vm = createViewModel()

        assertTrue(vm.showBottomBar.value)
    }

    @Test
    fun `updateBottomBarVisibility false hides the bar`() = runTest {
        val vm = createViewModel()

        vm.updateBottomBarVisibility(false)

        assertFalse(vm.showBottomBar.value)
    }

    @Test
    fun `updateBottomBarVisibility can be toggled back to true`() = runTest {
        val vm = createViewModel()

        vm.updateBottomBarVisibility(false)
        vm.updateBottomBarVisibility(true)

        assertTrue(vm.showBottomBar.value)
    }

    // -------------------------------------------------------------------------
    // Start destination override
    // -------------------------------------------------------------------------
    @Test
    fun `updateStartDestination overrides the destination`() = runTest {
        val vm = createViewModel()

        vm.updateStartDestination(Screen.Home.route)

        assertEquals(Screen.Home.route, vm.startDestination.value)
    }

    // -------------------------------------------------------------------------
    // Scroll to top
    // -------------------------------------------------------------------------
    @Test
    fun `scrollToTop is null initially`() = runTest {
        val vm = createViewModel()

        assertNull(vm.scrollToTop.value)
    }

    @Test
    fun `triggerScrollToTop sets a non-null timestamp`() = runTest {
        val vm = createViewModel()

        vm.triggerScrollToTop()

        assertNotNull(vm.scrollToTop.value)
    }

    @Test
    fun `consumeScrollToTopEvent clears the timestamp`() = runTest {
        val vm = createViewModel()

        vm.triggerScrollToTop()
        vm.consumeScrollToTopEvent()

        assertNull(vm.scrollToTop.value)
    }
}