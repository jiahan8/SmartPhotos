package com.jiahan.smartcamera.data.repository

import dev.gitlive.firebase.messaging.FirebaseMessaging

/*
 * GitLive's own calls, not awaited yet, deliberately: waiting means the native SDK's completion
 * handler, which needs the Firebase iOS SDK linked, and no iOS client does. Wire it here, as the
 * Android actual does, when one is linked.
 */

internal actual suspend fun FirebaseMessaging.subscribeToTopicAndAwait(topic: String) {
    subscribeToTopic(topic)
}

internal actual suspend fun FirebaseMessaging.unsubscribeFromTopicAndAwait(topic: String) {
    unsubscribeFromTopic(topic)
}