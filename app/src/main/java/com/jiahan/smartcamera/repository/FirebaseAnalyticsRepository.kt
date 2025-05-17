package com.jiahan.smartcamera.repository

import android.os.Bundle
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAnalyticsRepository @Inject constructor() : AnalyticsRepository {
    private val firebaseAnalytics = Firebase.analytics

    override fun logSearchEvent(value: String) {
        val params = Bundle().apply {
            putString(FirebaseAnalytics.Param.SEARCH_TERM, value)
        }
        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SEARCH, params)
    }
}