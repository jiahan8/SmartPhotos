package com.jiahan.smartcamera.data.repository

import dev.gitlive.firebase.analytics.FirebaseAnalytics

/**
 * The two Analytics calls [FirebaseAnalyticsRepository] makes: the seam between it and Firebase,
 * since GitLive's `FirebaseAnalytics` is a final platform class no `commonTest` can fake.
 */
internal interface AnalyticsSink {
    fun setUserId(userId: String?)

    fun logEvent(name: String, parameters: Map<String, String>)
}

internal class GitLiveAnalyticsSink(
    private val analytics: FirebaseAnalytics,
) : AnalyticsSink {

    override fun setUserId(userId: String?) {
        analytics.setUserId(userId)
    }

    override fun logEvent(name: String, parameters: Map<String, String>) {
        analytics.logEvent(name, parameters)
    }
}