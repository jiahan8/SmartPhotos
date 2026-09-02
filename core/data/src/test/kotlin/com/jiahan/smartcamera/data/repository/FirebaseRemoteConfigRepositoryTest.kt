package com.jiahan.smartcamera.data.repository

import com.google.android.gms.tasks.Tasks
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.jiahan.smartcamera.util.AppConstants.REMOTE_CONFIG_DEBUG_FETCH_INTERVAL_SECONDS
import com.jiahan.smartcamera.util.AppConstants.REMOTE_CONFIG_FETCH_INTERVAL_SECONDS
import com.jiahan.smartcamera.util.ErrorHandler
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the fetch-interval branch in [FirebaseRemoteConfigRepository]'s `init`.
 *
 * Both branches are reachable here only because the build type arrives as an injected
 * `@DebugBuild` flag: unit tests run against the debug variant, so a static `BuildConfig.DEBUG`
 * read could never exercise the release value. See the Build type bullet in AGENTS.md for when
 * that indirection is and isn't wanted.
 *
 * The other half of that pair -- that the binding feeding the flag reports the real build type --
 * stayed in :app as `AppModuleTest`, because it asserts against :app's `AppModule` and :app's
 * `BuildConfig`, neither of which exists below the application module.
 */
class FirebaseRemoteConfigRepositoryTest {

    /** Builds the repository and returns the settings it handed to Remote Config. */
    private fun configSettingsFor(isDebugBuild: Boolean): FirebaseRemoteConfigSettings {
        val remoteConfig: FirebaseRemoteConfig = mockk(relaxed = true)
        val errorHandler: ErrorHandler = mockk(relaxed = true)
        val settings = slot<FirebaseRemoteConfigSettings>()
        every { remoteConfig.setConfigSettingsAsync(capture(settings)) } returns mockk(relaxed = true)

        FirebaseRemoteConfigRepository(
            remoteConfig = remoteConfig,
            errorHandler = errorHandler,
            isDebugBuild = isDebugBuild,
        )

        return settings.captured
    }

    @Test
    fun `debug build fetches config without throttling`() {
        assertEquals(
            REMOTE_CONFIG_DEBUG_FETCH_INTERVAL_SECONDS,
            configSettingsFor(isDebugBuild = true).minimumFetchIntervalInSeconds
        )
    }

    @Test
    fun `release build throttles config fetches to the standard interval`() {
        assertEquals(
            REMOTE_CONFIG_FETCH_INTERVAL_SECONDS,
            configSettingsFor(isDebugBuild = false).minimumFetchIntervalInSeconds
        )
    }

    /**
     * A failed `activate()` must not reach whoever is collecting.
     *
     * `observeExploreIconVisible` refreshes the value on a config update from inside a `launch`
     * that is an ordinary child of the callbackFlow producer, so an exception there cancels the
     * producer and re-throws at the collector. That collector is `HomeViewModel`'s init block,
     * collecting in `viewModelScope` with no `catch`, where an uncaught exception kills the
     * process -- over a config refresh whose only job is to toggle an icon.
     *
     * The assertion is deliberately two-sided: the stream stays alive *and* the failure is logged
     * rather than swallowed. `runTest` supplies the third: if the flow threw, the collecting child
     * would fail this test on its own.
     */
    @Test
    fun `a failed config activation is logged instead of failing the stream`() = runTest {
        val remoteConfig: FirebaseRemoteConfig = mockk(relaxed = true)
        val errorHandler: ErrorHandler = mockk(relaxed = true)
        val listener = slot<ConfigUpdateListener>()

        every { remoteConfig.getBoolean(any()) } returns false
        every { remoteConfig.activate() } returns
                Tasks.forException(IllegalStateException("activate failed"))
        every { remoteConfig.addOnConfigUpdateListener(capture(listener)) } returns
                mockk(relaxed = true)

        val repository = FirebaseRemoteConfigRepository(
            remoteConfig = remoteConfig,
            errorHandler = errorHandler,
            isDebugBuild = true,
        )

        // Unconfined so the producer runs far enough to register the listener before it is fired.
        val collecting = launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeExploreIconVisible().collect { }
        }

        val update: ConfigUpdate = mockk {
            every { updatedKeys } returns setOf(EXPLORE_ICON_VISIBLE_KEY)
        }
        listener.captured.onUpdate(update)
        runCurrent()

        assertTrue("the stream must survive a failed activate()", collecting.isActive)
        verify { errorHandler.logError(any(), any()) }

        collecting.cancel()
    }

    private companion object {
        /** The wire key, which the repository keeps private. */
        const val EXPLORE_ICON_VISIBLE_KEY = "explore_icon_visible"
    }
}