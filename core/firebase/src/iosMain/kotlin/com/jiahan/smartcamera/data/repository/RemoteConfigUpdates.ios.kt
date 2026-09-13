package com.jiahan.smartcamera.data.repository

import dev.gitlive.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * No real-time updates on iOS yet, deliberately: the native SDK's `addOnConfigUpdateListener` needs
 * the Firebase iOS SDK linked, which no iOS client does. The Explore icon still gets its value when
 * first observed. Wire the native listener here, as the Android actual does, when one is linked.
 */
internal actual fun FirebaseRemoteConfig.configUpdates(
    onListenerError: (Throwable) -> Unit,
): Flow<Set<String>> = emptyFlow()