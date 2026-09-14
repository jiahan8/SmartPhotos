package com.jiahan.smartcamera.data.repository

import dev.gitlive.firebase.messaging.FirebaseMessaging
import dev.gitlive.firebase.messaging.android
import kotlinx.coroutines.tasks.await

/*
 * The Android SDK's topic calls, awaited. Reached through GitLive's own `android` accessor, so it is
 * the same default instance GitLive reads the token from.
 */

internal actual suspend fun FirebaseMessaging.subscribeToTopicAndAwait(topic: String) {
    android.subscribeToTopic(topic).await()
}

internal actual suspend fun FirebaseMessaging.unsubscribeFromTopicAndAwait(topic: String) {
    android.unsubscribeFromTopic(topic).await()
}