package com.jiahan.smartcamera.data.repository

import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import dev.gitlive.firebase.remoteconfig.FirebaseRemoteConfig
import dev.gitlive.firebase.remoteconfig.android
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * The Android SDK's real-time listener, which GitLive does not wrap. Reached through GitLive's own
 * `android` accessor, so it is the same default instance GitLive fetches and activates, and
 * unregistered when the collector goes away -- the listener the Android-SDK repository used before.
 */
internal actual fun FirebaseRemoteConfig.configUpdates(
    onListenerError: (Throwable) -> Unit,
): Flow<Set<String>> = callbackFlow {
    val registration = android.addOnConfigUpdateListener(
        object : ConfigUpdateListener {
            override fun onUpdate(configUpdate: ConfigUpdate) {
                trySend(configUpdate.updatedKeys)
            }

            override fun onError(error: FirebaseRemoteConfigException) {
                onListenerError(error)
            }
        }
    )

    awaitClose { registration.remove() }
}