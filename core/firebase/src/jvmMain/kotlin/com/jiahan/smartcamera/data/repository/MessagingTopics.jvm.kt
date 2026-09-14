package com.jiahan.smartcamera.data.repository

import dev.gitlive.firebase.messaging.FirebaseMessaging

/*
 * GitLive's own calls, which its JVM Messaging leaves unimplemented. Nothing ships on this target --
 * it exists so the shared code and its tests run on a plain JVM, and the tests use a fake.
 */

internal actual suspend fun FirebaseMessaging.subscribeToTopicAndAwait(topic: String) {
    subscribeToTopic(topic)
}

internal actual suspend fun FirebaseMessaging.unsubscribeFromTopicAndAwait(topic: String) {
    unsubscribeFromTopic(topic)
}