package com.jiahan.smartcamera.data.repository

import dev.gitlive.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.flow.Flow

/**
 * The Remote Config operations [FirebaseRemoteConfigRepository] uses: the seam between it and
 * Firebase, since GitLive's `FirebaseRemoteConfig` is a final platform class no `commonTest` can
 * fake.
 */
internal interface RemoteConfigSource {
    suspend fun configure(minimumFetchIntervalSeconds: Long, defaults: Map<String, Any>)

    suspend fun fetchAndActivate()

    suspend fun activate()

    fun getString(key: String): String

    fun getBoolean(key: String): Boolean

    /**
     * The keys each real-time config update changed. Listener errors go to [onListenerError] rather
     * than ending the flow, as the Android SDK's listener reports them without unregistering.
     */
    fun configUpdates(onListenerError: (Throwable) -> Unit): Flow<Set<String>>
}

internal class GitLiveRemoteConfigSource(
    private val remoteConfig: FirebaseRemoteConfig,
) : RemoteConfigSource {

    override suspend fun configure(
        minimumFetchIntervalSeconds: Long,
        defaults: Map<String, Any>,
    ) {
        remoteConfig.settings { this.minimumFetchIntervalInSeconds = minimumFetchIntervalSeconds }
        remoteConfig.setDefaults(*defaults.toList().toTypedArray())
    }

    override suspend fun fetchAndActivate() {
        remoteConfig.fetchAndActivate()
    }

    override suspend fun activate() {
        remoteConfig.activate()
    }

    override fun getString(key: String): String = remoteConfig.getValue(key).asString()

    override fun getBoolean(key: String): Boolean = remoteConfig.getValue(key).asBoolean()

    override fun configUpdates(onListenerError: (Throwable) -> Unit): Flow<Set<String>> =
        remoteConfig.configUpdates(onListenerError)
}

/**
 * Real-time config updates, per platform, because GitLive 2.7.0's common `FirebaseRemoteConfig`
 * exposes no update listener at all.
 */
internal expect fun FirebaseRemoteConfig.configUpdates(
    onListenerError: (Throwable) -> Unit,
): Flow<Set<String>>