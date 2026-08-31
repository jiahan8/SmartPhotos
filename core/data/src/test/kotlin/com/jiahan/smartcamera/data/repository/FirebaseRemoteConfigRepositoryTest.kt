package com.jiahan.smartcamera.data.repository

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.jiahan.smartcamera.util.AppConstants.REMOTE_CONFIG_DEBUG_FETCH_INTERVAL_SECONDS
import com.jiahan.smartcamera.util.AppConstants.REMOTE_CONFIG_FETCH_INTERVAL_SECONDS
import com.jiahan.smartcamera.util.ErrorHandler
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.Assert.assertEquals
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
}