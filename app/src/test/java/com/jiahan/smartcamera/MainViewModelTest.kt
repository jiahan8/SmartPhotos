package com.jiahan.smartcamera

import android.content.Intent
import android.net.Uri
import app.cash.turbine.test
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.ktx.AppUpdateResult
import com.jiahan.smartcamera.data.datastore.UserPreferences
import com.jiahan.smartcamera.data.datastore.UserPreferencesRepository
import com.jiahan.smartcamera.data.repository.AnalyticsRepository
import com.jiahan.smartcamera.data.repository.AppUpdateRepository
import com.jiahan.smartcamera.data.repository.AuthRepository
import com.jiahan.smartcamera.data.repository.RemoteConfigRepository
import com.jiahan.smartcamera.data.repository.UserRepository
import com.jiahan.smartcamera.navigation.Screen
import com.jiahan.smartcamera.note.IncomingShare
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
    private val analyticsRepository: AnalyticsRepository = mockk()
    private val userPreferencesRepository: UserPreferencesRepository = mockk()
    private val incomingShareHandler: IncomingShareHandler = mockk()
    private val appUpdateRepository: AppUpdateRepository = mockk()
    private val errorHandler: ErrorHandler = mockk()

    private val defaultPrefs =
        UserPreferences(isDarkTheme = false, username = "", profilePicture = null)

    @Before
    fun setUp() {
        coEvery { remoteConfigRepository.fetchAndActivateConfig() } returns Result.success(Unit)
        every { analyticsRepository.setUserId(any()) } just runs
        every { errorHandler.logError(any()) } just runs
        every { userPreferencesRepository.userPreferencesFlow } returns flowOf(defaultPrefs)
        every { incomingShareHandler.incomingShare } returns MutableStateFlow(null)
        every { incomingShareHandler.postShare(any()) } just runs
        every { appUpdateRepository.observeUpdateState() } returns flowOf()
        // Default: unauthenticated user. Tests that need a different state override these.
        every { authRepository.currentUserId } returns null
        every { authRepository.isCurrentUserEmailVerified } returns false
        coEvery { userRepository.registerForPushNotifications() } returns Result.success(Unit)
        coEvery { userRepository.recordUserActivity(any()) } returns Result.success(Unit)
    }

    @After
    fun tearDown() = unmockkAll()

    private fun createViewModel() =
        MainViewModel(
            remoteConfigRepository,
            errorHandler,
            authRepository,
            userRepository,
            analyticsRepository,
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

        assertEquals(Screen.Home, vm.uiState.value.startDestination)
        coVerify { userRepository.registerForPushNotifications() }
    }

    @Test
    fun `startDestination is Auth when no user is signed in`() = runTest {

        val vm = createViewModel()

        assertEquals(Screen.Auth, vm.uiState.value.startDestination)
        coVerify(exactly = 0) { userRepository.registerForPushNotifications() }
    }

    @Test
    fun `startDestination is Auth when signed in but email not verified`() = runTest {
        every { authRepository.currentUserId } returns "uid_123"
        every { authRepository.isCurrentUserEmailVerified } returns false

        val vm = createViewModel()

        assertEquals(Screen.Auth, vm.uiState.value.startDestination)
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

        vm.updateStartDestination(Screen.Home)

        assertEquals(Screen.Home, vm.uiState.value.startDestination)
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

    // -------------------------------------------------------------------------
    // App update
    // -------------------------------------------------------------------------
    @Test
    fun `updateState reflects an available update from the repository`() = runTest {
        val available = AppUpdateResult.Available(mockk<AppUpdateManager>(), mockk<AppUpdateInfo>())
        every { appUpdateRepository.observeUpdateState() } returns flowOf(available)

        val vm = createViewModel()

        vm.updateState.test {
            assertEquals(available, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `completeUpdate completes the update once it has downloaded`() = runTest {
        val appUpdateManager: AppUpdateManager = mockk()
        every { appUpdateManager.completeUpdate() } returns Tasks.forResult(null)
        val downloaded = AppUpdateResult.Downloaded(appUpdateManager)
        every { appUpdateRepository.observeUpdateState() } returns flowOf(downloaded)

        val vm = createViewModel()

        vm.updateState.test {
            assertEquals(downloaded, awaitItem())
            vm.completeUpdate()
            verify { appUpdateManager.completeUpdate() }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `completeUpdate is a no-op when no update has downloaded`() = runTest {
        val appUpdateManager: AppUpdateManager = mockk()
        val available = AppUpdateResult.Available(appUpdateManager, mockk<AppUpdateInfo>())
        every { appUpdateRepository.observeUpdateState() } returns flowOf(available)

        val vm = createViewModel()

        vm.updateState.test {
            awaitItem()
            vm.completeUpdate()
            cancelAndIgnoreRemainingEvents()
        }

        verify(exactly = 0) { appUpdateManager.completeUpdate() }
    }

    // -------------------------------------------------------------------------
    // handleIncomingIntent
    // -------------------------------------------------------------------------
    @Test
    fun `handleIncomingIntent with ACTION_SEND text posts a text-only share`() = runTest {
        val vm = createViewModel()
        val intent: Intent = mockk()
        every { intent.action } returns Intent.ACTION_SEND
        every { intent.getStringExtra(Intent.EXTRA_TEXT) } returns "hello"
        every { intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) } returns null

        vm.handleIncomingIntent(intent)

        verify { incomingShareHandler.postShare(IncomingShare(text = "hello", uris = emptyList())) }
    }

    @Test
    fun `handleIncomingIntent with ACTION_SEND uri posts a uri-only share`() = runTest {
        val vm = createViewModel()
        val uri: Uri = mockk()
        val intent: Intent = mockk()
        every { intent.action } returns Intent.ACTION_SEND
        every { intent.getStringExtra(Intent.EXTRA_TEXT) } returns null
        every { intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) } returns uri

        vm.handleIncomingIntent(intent)

        verify { incomingShareHandler.postShare(IncomingShare(text = null, uris = listOf(uri))) }
    }

    @Test
    fun `handleIncomingIntent with ACTION_SEND text and uri posts both`() = runTest {
        val vm = createViewModel()
        val uri: Uri = mockk()
        val intent: Intent = mockk()
        every { intent.action } returns Intent.ACTION_SEND
        every { intent.getStringExtra(Intent.EXTRA_TEXT) } returns "hello"
        every { intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) } returns uri

        vm.handleIncomingIntent(intent)

        verify {
            incomingShareHandler.postShare(IncomingShare(text = "hello", uris = listOf(uri)))
        }
    }

    @Test
    fun `handleIncomingIntent with ACTION_SEND and no text or uri does not post a share`() =
        runTest {
            val vm = createViewModel()
            val intent: Intent = mockk()
            every { intent.action } returns Intent.ACTION_SEND
            every { intent.getStringExtra(Intent.EXTRA_TEXT) } returns null
            every { intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) } returns null

            vm.handleIncomingIntent(intent)

            verify(exactly = 0) { incomingShareHandler.postShare(any()) }
        }

    @Test
    fun `handleIncomingIntent with ACTION_SEND_MULTIPLE posts all uris`() = runTest {
        val vm = createViewModel()
        val uri1: Uri = mockk()
        val uri2: Uri = mockk()
        val intent: Intent = mockk()
        every { intent.action } returns Intent.ACTION_SEND_MULTIPLE
        every { intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM) } returns
                arrayListOf(uri1, uri2)

        vm.handleIncomingIntent(intent)

        verify {
            incomingShareHandler.postShare(IncomingShare(text = null, uris = listOf(uri1, uri2)))
        }
    }

    @Test
    fun `handleIncomingIntent with ACTION_SEND_MULTIPLE and no uris does not post a share`() =
        runTest {
            val vm = createViewModel()
            val intent: Intent = mockk()
            every { intent.action } returns Intent.ACTION_SEND_MULTIPLE
            every { intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM) } returns null

            vm.handleIncomingIntent(intent)

            verify(exactly = 0) { incomingShareHandler.postShare(any()) }
        }

    @Test
    fun `handleIncomingIntent with an unsupported action does not post a share`() = runTest {
        val vm = createViewModel()
        val intent: Intent = mockk()
        every { intent.action } returns Intent.ACTION_VIEW

        vm.handleIncomingIntent(intent)

        verify(exactly = 0) { incomingShareHandler.postShare(any()) }
    }

    // -------------------------------------------------------------------------
    // hasPendingShare
    // -------------------------------------------------------------------------
    @Test
    fun `hasPendingShare is false when no share is pending`() = runTest {
        val vm = createViewModel()

        vm.hasPendingShare.test {
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hasPendingShare becomes true once a share is posted`() = runTest {
        val shareFlow = MutableStateFlow<IncomingShare?>(null)
        every { incomingShareHandler.incomingShare } returns shareFlow
        val vm = createViewModel()

        vm.hasPendingShare.test {
            assertEquals(false, awaitItem())
            shareFlow.value = IncomingShare(text = "hello", uris = emptyList())
            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}