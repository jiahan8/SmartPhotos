package com.jiahan.smartcamera.data.repository

import dev.gitlive.firebase.analytics.FirebaseAnalytics

/**
 * [AnalyticsRepository] on GitLive's multiplatform Analytics.
 *
 * Every event carries one string parameter, handed to GitLive as a map, which its Android
 * implementation turns into the same `Bundle` the Android-SDK repository built.
 *
 * The event and parameter names are a wire contract: they are what the Analytics console groups and
 * reports by, so renaming one splits an event's history in two. `search`/`search_term` are
 * Firebase's recommended search event and parameter -- the values behind the Android SDK's
 * `FirebaseAnalytics.Event.SEARCH` and `Param.SEARCH_TERM`, which common code cannot name.
 */
class FirebaseAnalyticsRepository internal constructor(
    private val sink: AnalyticsSink,
) : AnalyticsRepository {

    constructor(analytics: FirebaseAnalytics) : this(GitLiveAnalyticsSink(analytics))

    private companion object {
        const val SEARCH_EVENT = "search"
        const val NOTE_SEARCH_EVENT = "note_search"
        const val FAVORITE_SEARCH_EVENT = "favorite_search"
        const val EXPLORE_SEARCH_EVENT = "explore_search"
        const val SEARCH_TERM_PARAM = "search_term"
        const val NOTE_CREATE_EVENT = "note_create"
        const val NOTE_EDIT_EVENT = "note_edit"
        const val NOTE_TEXT_PARAM = "note_text"
        const val TEXT_EVENT = "text"
        const val TEXT_VALUE_PARAM = "text_value"
        const val DISPLAY_NAME_EVENT = "display_name"
        const val DISPLAY_NAME_PARAM = "display_name"
        const val USERNAME_EVENT = "username"
        const val USERNAME_PARAM = "username"
    }

    override fun setUserId(userId: String?) {
        sink.setUserId(userId)
    }

    override fun logSearch(query: String) {
        sink.logEvent(SEARCH_EVENT, mapOf(SEARCH_TERM_PARAM to query))
    }

    override fun logNoteSearch(query: String) {
        sink.logEvent(NOTE_SEARCH_EVENT, mapOf(SEARCH_TERM_PARAM to query))
    }

    override fun logNoteCreate(text: String) {
        sink.logEvent(NOTE_CREATE_EVENT, mapOf(NOTE_TEXT_PARAM to text))
    }

    override fun logNoteEdit(text: String) {
        sink.logEvent(NOTE_EDIT_EVENT, mapOf(NOTE_TEXT_PARAM to text))
    }

    override fun logFavoriteSearch(query: String) {
        sink.logEvent(FAVORITE_SEARCH_EVENT, mapOf(SEARCH_TERM_PARAM to query))
    }

    override fun logExploreSearch(query: String) {
        sink.logEvent(EXPLORE_SEARCH_EVENT, mapOf(SEARCH_TERM_PARAM to query))
    }

    override fun logText(text: String) {
        sink.logEvent(TEXT_EVENT, mapOf(TEXT_VALUE_PARAM to text))
    }

    override fun logDisplayName(displayName: String) {
        sink.logEvent(DISPLAY_NAME_EVENT, mapOf(DISPLAY_NAME_PARAM to displayName))
    }

    override fun logUsername(username: String) {
        sink.logEvent(USERNAME_EVENT, mapOf(USERNAME_PARAM to username))
    }
}