package com.jiahan.smartcamera.data.repository

import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.jiahan.smartcamera.di.DebugBuild
import com.jiahan.smartcamera.util.AppConstants.REMOTE_CONFIG_DEBUG_FETCH_INTERVAL_SECONDS
import com.jiahan.smartcamera.util.AppConstants.REMOTE_CONFIG_FETCH_INTERVAL_SECONDS
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.safeCall
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseRemoteConfigRepository @Inject constructor(
    private val remoteConfig: FirebaseRemoteConfig,
    private val errorHandler: ErrorHandler,
    @param:DebugBuild private val isDebugBuild: Boolean
) : RemoteConfigRepository {

    companion object {
        private const val STORAGE_URL_KEY = "firebase_storage_url"
        private const val STORAGE_FOLDER_KEY = "firebase_storage_folder"
        private const val STORAGE_CACHE_FOLDER_KEY = "firebase_storage_cache_folder"
        private const val EXPLORE_ICON_VISIBLE_KEY = "explore_icon_visible"
    }

    init {
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = if (isDebugBuild)
                REMOTE_CONFIG_DEBUG_FETCH_INTERVAL_SECONDS
            else
                REMOTE_CONFIG_FETCH_INTERVAL_SECONDS
        }

        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.setDefaultsAsync(
            mapOf(
                STORAGE_URL_KEY to "default_value",
                STORAGE_FOLDER_KEY to "default_value",
                STORAGE_CACHE_FOLDER_KEY to "default_value",
                EXPLORE_ICON_VISIBLE_KEY to false
            )
        )
    }

    override suspend fun fetchAndActivateConfig(): Result<Unit> = safeCall {
        remoteConfig.fetchAndActivate().await()
    }

    override fun getStorageUrl(): String = remoteConfig.getString(STORAGE_URL_KEY)

    override fun getStorageFolderName(): String = remoteConfig.getString(STORAGE_FOLDER_KEY)

    override fun getStorageCacheFolderName(): String =
        remoteConfig.getString(STORAGE_CACHE_FOLDER_KEY)

    override fun observeExploreIconVisible(): Flow<Boolean> = callbackFlow {
        trySend(remoteConfig.getBoolean(EXPLORE_ICON_VISIBLE_KEY))

        val producerScope = this
        val registration = remoteConfig.addOnConfigUpdateListener(
            object : ConfigUpdateListener {
                override fun onUpdate(configUpdate: ConfigUpdate) {
                    if (EXPLORE_ICON_VISIBLE_KEY !in configUpdate.updatedKeys) return
                    producerScope.launch {
                        /*
                         * safeCall, not a bare `await()`. This launch is an ordinary child of the
                         * callbackFlow producer, so an activate() failure cancels the producer
                         * with that exception and re-throws it at the collector -- and the
                         * collector is HomeViewModel's init block, collecting in viewModelScope
                         * with no `catch`. Under a SupervisorJob that is an uncaught exception and
                         * a dead process, over a config refresh whose only job is to toggle an
                         * icon.
                         *
                         * A Flow-returning repository function is one of the two things that
                         * cannot carry a Result, so it has to absorb its own failures -- which is
                         * what `onError` below already does for the listener's own. The asymmetry
                         * between the two was the bug.
                         *
                         * safeCall rather than runCatching because runCatching swallows the
                         * CancellationException raised when this flow is closed, which would break
                         * cancellation instead of a process; see its KDoc in :core:domain.
                         */
                        safeCall { remoteConfig.activate().await() }
                            .onSuccess {
                                trySend(remoteConfig.getBoolean(EXPLORE_ICON_VISIBLE_KEY))
                            }
                            .onFailure { errorHandler.logError(it) }
                    }
                }

                override fun onError(error: FirebaseRemoteConfigException) {
                    errorHandler.logError(error)
                }
            }
        )

        awaitClose { registration.remove() }
    }
}