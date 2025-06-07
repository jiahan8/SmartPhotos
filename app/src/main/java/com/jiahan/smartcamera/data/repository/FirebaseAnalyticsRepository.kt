package com.jiahan.smartcamera.data.repository

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAnalyticsRepository @Inject constructor(
    private val firebaseAnalytics: FirebaseAnalytics
) : AnalyticsRepository {

    companion object {
        private const val SEARCH_CUSTOM_EVENT = "search_custom"
        private const val SEARCH_TERM_CUSTOM_PARAM = "search_term_custom"
        private const val NOTE_CUSTOM_EVENT = "note_custom"
        private const val NOTE_TERM_CUSTOM_PARAM = "note_term_custom"
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

    override fun logNoteCustomEvent(value: String) {
        val params = Bundle().apply {
            putString(NOTE_TERM_CUSTOM_PARAM, value)
        }
        firebaseAnalytics.logEvent(NOTE_CUSTOM_EVENT, params)
    }
}