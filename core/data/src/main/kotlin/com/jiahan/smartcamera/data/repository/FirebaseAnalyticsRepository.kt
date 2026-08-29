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
        private const val NOTE_SEARCH_EVENT = "note_search"
        private const val FAVORITE_SEARCH_EVENT = "favorite_search"
        private const val EXPLORE_SEARCH_EVENT = "explore_search"
        private const val SEARCH_TERM_PARAM = "search_term"
        private const val NOTE_CREATE_EVENT = "note_create"
        private const val NOTE_EDIT_EVENT = "note_edit"
        private const val NOTE_TEXT_PARAM = "note_text"
        private const val TEXT_EVENT = "text"
        private const val TEXT_VALUE_PARAM = "text_value"
        private const val DISPLAY_NAME_EVENT = "display_name"
        private const val DISPLAY_NAME_PARAM = "display_name"
        private const val USERNAME_EVENT = "username"
        private const val USERNAME_PARAM = "username"
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
            putString(SEARCH_TERM_PARAM, value)
        }
        firebaseAnalytics.logEvent(NOTE_SEARCH_EVENT, params)
    }

    override fun logNoteCustomEvent(value: String) {
        val params = Bundle().apply {
            putString(NOTE_TEXT_PARAM, value)
        }
        firebaseAnalytics.logEvent(NOTE_CREATE_EVENT, params)
    }

    override fun logEditNoteCustomEvent(value: String) {
        val params = Bundle().apply {
            putString(NOTE_TEXT_PARAM, value)
        }
        firebaseAnalytics.logEvent(NOTE_EDIT_EVENT, params)
    }

    override fun logFavoriteSearchCustomEvent(value: String) {
        val params = Bundle().apply {
            putString(SEARCH_TERM_PARAM, value)
        }
        firebaseAnalytics.logEvent(FAVORITE_SEARCH_EVENT, params)
    }

    override fun logExploreSearchCustomEvent(value: String) {
        val params = Bundle().apply {
            putString(SEARCH_TERM_PARAM, value)
        }
        firebaseAnalytics.logEvent(EXPLORE_SEARCH_EVENT, params)
    }

    override fun logTextCustomEvent(value: String) {
        val params = Bundle().apply {
            putString(TEXT_VALUE_PARAM, value)
        }
        firebaseAnalytics.logEvent(TEXT_EVENT, params)
    }

    override fun logDisplayNameCustomEvent(value: String) {
        val params = Bundle().apply {
            putString(DISPLAY_NAME_PARAM, value)
        }
        firebaseAnalytics.logEvent(DISPLAY_NAME_EVENT, params)
    }

    override fun logUsernameCustomEvent(value: String) {
        val params = Bundle().apply {
            putString(USERNAME_PARAM, value)
        }
        firebaseAnalytics.logEvent(USERNAME_EVENT, params)
    }
}