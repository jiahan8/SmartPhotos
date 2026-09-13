package com.jiahan.smartcamera.data.repository

import dev.gitlive.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * No real-time updates on the JVM: GitLive's JVM Remote Config has no listener to reach. Nothing
 * ships on this target -- it exists so the shared code and its tests run on a plain JVM -- and the
 * Explore icon still gets its value when first observed.
 */
internal actual fun FirebaseRemoteConfig.configUpdates(
    onListenerError: (Throwable) -> Unit,
): Flow<Set<String>> = emptyFlow()