package com.jiahan.smartcamera.data.repository

import android.os.Bundle
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAnalyticsRepository @Inject constructor() : AnalyticsRepository {
    private val firebaseAnalytics = Firebase.analytics

    companion object {
        private const val SEARCH_CUSTOM_EVENT = "search_custom"
        private const val SEARCH_TERM_CUSTOM_PARAM = "search_term_custom"
    }

    override fun logSearchEvent(value: String) {
        val params = Bundle().apply {
            putString(FirebaseAnalytics.Param.SEARCH_TERM, value)
        }
        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SEARCH, params)
    }

    override fun logSearchCustomEvent(value: String) {
        val params = Bundle().apply {
            putString(SEARCH_TERM_CUSTOM_PARAM, value)
        }
        firebaseAnalytics.logEvent(SEARCH_CUSTOM_EVENT, params)
    }
}