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
        private const val FAVORITE_SEARCH_CUSTOM_EVENT = "favorite_search_custom"
        private const val FAVORITE_SEARCH_TERM_CUSTOM_PARAM = "favorite_search_term_custom"
        private const val NOTE_CUSTOM_EVENT = "note_custom"
        private const val NOTE_TERM_CUSTOM_PARAM = "note_term_custom"
        private const val TEXT_CUSTOM_EVENT = "text_custom"
        private const val TEXT_TERM_CUSTOM_PARAM = "text_term_custom"
        private const val DISPLAY_NAME_CUSTOM_EVENT = "display_name_custom"
        private const val DISPLAY_NAME_TERM_CUSTOM_PARAM = "display_name_term_custom"
        private const val USERNAME_CUSTOM_EVENT = "username_custom"
        private const val USERNAME_TERM_CUSTOM_PARAM = "username_term_custom"
    }

    override fun setUserId(userId: String?) {
        firebaseAnalytics.setUserId(userId)
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

    override fun logFavoriteSearchCustomEvent(value: String) {
        val params = Bundle().apply {
            putString(FAVORITE_SEARCH_TERM_CUSTOM_PARAM, value)
        }
        firebaseAnalytics.logEvent(FAVORITE_SEARCH_CUSTOM_EVENT, params)
    }

    override fun logTextCustomEvent(value: String) {
        val params = Bundle().apply {
            putString(TEXT_TERM_CUSTOM_PARAM, value)
        }
        firebaseAnalytics.logEvent(TEXT_CUSTOM_EVENT, params)
    }

    override fun logDisplayNameCustomEvent(value: String) {
        val params = Bundle().apply {
            putString(DISPLAY_NAME_TERM_CUSTOM_PARAM, value)
        }
        firebaseAnalytics.logEvent(DISPLAY_NAME_CUSTOM_EVENT, params)
    }

    override fun logUsernameCustomEvent(value: String) {
        val params = Bundle().apply {
            putString(USERNAME_TERM_CUSTOM_PARAM, value)
        }
        firebaseAnalytics.logEvent(USERNAME_CUSTOM_EVENT, params)
    }
}