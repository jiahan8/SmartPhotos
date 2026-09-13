package com.jiahan.smartcamera.data.repository

import com.jiahan.smartcamera.util.AppConstants.REMOTE_CONFIG_DEBUG_FETCH_INTERVAL_SECONDS
import com.jiahan.smartcamera.util.AppConstants.REMOTE_CONFIG_FETCH_INTERVAL_SECONDS
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.safeCall
import dev.gitlive.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * [RemoteConfigRepository] on GitLive's multiplatform Remote Config.
 *
 * Settings and defaults are applied once, launched in [scope] as the repository is constructed.
 * That is the fire-and-forget the Android SDK's `setConfigSettingsAsync`/`setDefaultsAsync` gave the
 * old `init` block, which GitLive cannot reproduce synchronously: both calls are `suspend` there.
 * [fetchAndActivateConfig] waits for them, so a fetch runs under this build's fetch interval rather
 * than racing the call that sets it.
 *
 * Real-time updates to the Explore icon arrive through [RemoteConfigSource.configUpdates], on
 * Android only for now -- see `configUpdates`' actuals.
 */
class FirebaseRemoteConfigRepository internal constructor(
    private val source: RemoteConfigSource,
    private val errorHandler: ErrorHandler,
    isDebugBuild: Boolean,
    scope: CoroutineScope,
) : RemoteConfigRepository {

    constructor(
        remoteConfig: FirebaseRemoteConfig,
        errorHandler: ErrorHandler,
        isDebugBuild: Boolean,
        scope: CoroutineScope,
    ) : this(GitLiveRemoteConfigSource(remoteConfig), errorHandler, isDebugBuild, scope)

    private companion object {
        const val STORAGE_URL_KEY = "firebase_storage_url"
        const val STORAGE_FOLDER_KEY = "firebase_storage_folder"
        const val STORAGE_CACHE_FOLDER_KEY = "firebase_storage_cache_folder"
        const val EXPLORE_ICON_VISIBLE_KEY = "explore_icon_visible"
    }

    private val configured: Job = scope.launch {
        safeCall {
            source.configure(
                minimumFetchIntervalSeconds = if (isDebugBuild) {
                    REMOTE_CONFIG_DEBUG_FETCH_INTERVAL_SECONDS
                } else {
                    REMOTE_CONFIG_FETCH_INTERVAL_SECONDS
                },
                defaults = mapOf(
                    STORAGE_URL_KEY to "default_value",
                    STORAGE_FOLDER_KEY to "default_value",
                    STORAGE_CACHE_FOLDER_KEY to "default_value",
                    EXPLORE_ICON_VISIBLE_KEY to false,
                ),
            )
        }.onFailure { errorHandler.logError(it) }
    }

    override suspend fun fetchAndActivateConfig(): Result<Unit> = safeCall {
        configured.join()
        source.fetchAndActivate()
    }

    override fun getStorageUrl(): String = source.getString(STORAGE_URL_KEY)

    override fun getStorageFolderName(): String = source.getString(STORAGE_FOLDER_KEY)

    override fun getStorageCacheFolderName(): String = source.getString(STORAGE_CACHE_FOLDER_KEY)

    override fun observeExploreIconVisible(): Flow<Boolean> = flow {
        emit(source.getBoolean(EXPLORE_ICON_VISIBLE_KEY))

        source.configUpdates(onListenerError = { errorHandler.logError(it) })
            .filter { EXPLORE_ICON_VISIBLE_KEY in it }
            .collect {
                /*
                 * safeCall, not a bare activate(). An exception escaping this collect ends the flow
                 * and re-throws at the collector -- HomeViewModel's init block, collecting in
                 * viewModelScope with no `catch`, where it is an uncaught exception and a dead
                 * process over a config refresh whose only job is to toggle an icon.
                 *
                 * A Flow-returning repository function is one of the two things that cannot carry a
                 * Result, so it has to absorb its own failures -- which is what onListenerError above
                 * already does for the listener's own.
                 *
                 * safeCall rather than runCatching because runCatching swallows the
                 * CancellationException raised when this flow is closed, which would break
                 * cancellation instead of a process; see its KDoc in :core:domain.
                 */
                safeCall { source.activate() }
                    .onSuccess { emit(source.getBoolean(EXPLORE_ICON_VISIBLE_KEY)) }
                    .onFailure { errorHandler.logError(it) }
            }
    }
}