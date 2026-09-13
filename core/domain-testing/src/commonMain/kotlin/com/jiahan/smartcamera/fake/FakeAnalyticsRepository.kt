package com.jiahan.smartcamera.fake

import com.jiahan.smartcamera.data.repository.AnalyticsRepository

/**
 * No-op [AnalyticsRepository] test double. Records the last logged value for optional assertions.
 */
class FakeAnalyticsRepository : AnalyticsRepository {

    var lastLoggedText: String? = null
    var lastLoggedDisplayName: String? = null
    var lastLoggedUsername: String? = null

    override fun setUserId(userId: String?) {}

    override fun logSearch(query: String) {}

    override fun logNoteSearch(query: String) {}

    override fun logNoteCreate(text: String) {}

    override fun logNoteEdit(text: String) {}

    override fun logFavoriteSearch(query: String) {}

    override fun logExploreSearch(query: String) {}

    override fun logText(text: String) {
        lastLoggedText = text
    }

    override fun logDisplayName(displayName: String) {
        lastLoggedDisplayName = displayName
    }

    override fun logUsername(username: String) {
        lastLoggedUsername = username
    }
}