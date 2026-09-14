package com.jiahan.smartcamera.data.repository

import dev.gitlive.firebase.messaging.FirebaseMessaging

/** The Cloud Messaging calls [DefaultUserRepository] makes: its seam onto the token and topics. */
internal interface PushClient {
    suspend fun getToken(): String

    suspend fun subscribeToTopic(topic: String)

    suspend fun unsubscribeFromTopic(topic: String)
}

internal class GitLivePushClient(
    private val messaging: FirebaseMessaging,
) : PushClient {
    override suspend fun getToken(): String = messaging.getToken()

    override suspend fun subscribeToTopic(topic: String) {
        messaging.subscribeToTopicAndAwait(topic)
    }

    override suspend fun unsubscribeFromTopic(topic: String) {
        messaging.unsubscribeFromTopicAndAwait(topic)
    }
}

/*
 * Topic calls that finish before they return. GitLive 2.7.0's common subscribeToTopic and
 * unsubscribeFromTopic start the call and return at once, so a failed subscription would read as a
 * successful registerForPushNotifications -- where the Android SDK repository awaited both. Each
 * platform waits where it can reach its SDK.
 */

internal expect suspend fun FirebaseMessaging.subscribeToTopicAndAwait(topic: String)

internal expect suspend fun FirebaseMessaging.unsubscribeFromTopicAndAwait(topic: String)