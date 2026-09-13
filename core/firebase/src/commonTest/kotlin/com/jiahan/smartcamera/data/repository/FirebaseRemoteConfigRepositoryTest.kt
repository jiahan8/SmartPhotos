package com.jiahan.smartcamera.data.repository

import com.jiahan.smartcamera.util.AppConstants.REMOTE_CONFIG_DEBUG_FETCH_INTERVAL_SECONDS
import com.jiahan.smartcamera.util.AppConstants.REMOTE_CONFIG_FETCH_INTERVAL_SECONDS
import com.jiahan.smartcamera.util.ErrorHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers [FirebaseRemoteConfigRepository] over a fake [RemoteConfigSource].
 *
 * The fetch-interval pair pins the branch on the build type, which is reachable here only because
 * the flag is injected rather than read from `BuildConfig.DEBUG`; see the Build type bullet in
 * AGENTS.md. The other half of that pair -- that the binding feeding the flag reports the real build
 * type -- is :app's `AppModuleTest`.
 */
class FirebaseRemoteConfigRepositoryTest {

    private class FakeRemoteConfigSource : RemoteConfigSource {
        val calls = mutableListOf<String>()
        var configuredInterval: Long? = null
        var activateFailure: Throwable? = null
        val updates = MutableSharedFlow<Set<String>>()

        override suspend fun configure(
            minimumFetchIntervalSeconds: Long,
            defaults: Map<String, Any>,
        ) {
            calls += "configure"
            configuredInterval = minimumFetchIntervalSeconds
        }

        override suspend fun fetchAndActivate() {
            calls += "fetchAndActivate"
        }

        override suspend fun activate() {
            calls += "activate"
            activateFailure?.let { throw it }
        }

        override fun getString(key: String): String = ""

        override fun getBoolean(key: String): Boolean = false

        override fun configUpdates(onListenerError: (Throwable) -> Unit): Flow<Set<String>> =
            updates
    }

    private class RecordingErrorHandler : ErrorHandler {
        val logged = mutableListOf<Throwable>()

        override fun logError(throwable: Throwable, tag: String) {
            logged += throwable
        }
    }

    private fun TestScope.repository(
        source: FakeRemoteConfigSource,
        isDebugBuild: Boolean,
        errorHandler: ErrorHandler = RecordingErrorHandler(),
    ) = FirebaseRemoteConfigRepository(
        source = source,
        errorHandler = errorHandler,
        isDebugBuild = isDebugBuild,
        scope = backgroundScope,
    )

    @Test
    fun `debug build fetches config without throttling`() = runTest {
        val source = FakeRemoteConfigSource()

        repository(source, isDebugBuild = true)
        runCurrent()

        assertEquals(REMOTE_CONFIG_DEBUG_FETCH_INTERVAL_SECONDS, source.configuredInterval)
    }

    @Test
    fun `release build throttles config fetches to the standard interval`() = runTest {
        val source = FakeRemoteConfigSource()

        repository(source, isDebugBuild = false)
        runCurrent()

        assertEquals(REMOTE_CONFIG_FETCH_INTERVAL_SECONDS, source.configuredInterval)
    }

    /**
     * Settings are applied from a launched coroutine, so without the wait a fetch requested straight
     * after construction would run first -- under Remote Config's default throttle instead of this
     * build's interval. The test dispatcher does not start that coroutine until something yields,
     * which is exactly the ordering an unguarded fetch would lose.
     */
    @Test
    fun `a fetch waits for the settings to be applied`() = runTest {
        val source = FakeRemoteConfigSource()
        val repository = repository(source, isDebugBuild = false)

        repository.fetchAndActivateConfig()

        assertEquals(listOf("configure", "fetchAndActivate"), source.calls)
    }

    /**
     * A failed `activate()` must not reach whoever is collecting.
     *
     * The collector is `HomeViewModel`'s init block, collecting in `viewModelScope` with no `catch`,
     * where an uncaught exception kills the process -- over a config refresh whose only job is to
     * toggle an icon. The assertion is two-sided: the stream stays alive *and* the failure is logged
     * rather than swallowed. `runTest` supplies the third: if the flow threw, the collecting child
     * would fail this test on its own.
     */
    @Test
    fun `a failed config activation is logged instead of failing the stream`() = runTest {
        val source = FakeRemoteConfigSource().apply {
            activateFailure = IllegalStateException("activate failed")
        }
        val errorHandler = RecordingErrorHandler()
        val repository = repository(source, isDebugBuild = true, errorHandler = errorHandler)

        // Unconfined so the collector subscribes to the updates before one is emitted.
        val collecting = launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeExploreIconVisible().collect { }
        }

        source.updates.emit(setOf(EXPLORE_ICON_VISIBLE_KEY))
        runCurrent()

        assertTrue(collecting.isActive, "the stream must survive a failed activate()")
        assertTrue(errorHandler.logged.any { it.message == "activate failed" })

        collecting.cancel()
    }

    private companion object {
        /** The wire key, which the repository keeps private. */
        const val EXPLORE_ICON_VISIBLE_KEY = "explore_icon_visible"
    }
}