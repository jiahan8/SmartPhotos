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

    override fun logSearchEvent(value: String) {}

    override fun logSearchCustomEvent(value: String) {}

    override fun logNoteCustomEvent(value: String) {}

    override fun logEditNoteCustomEvent(value: String) {}

    override fun logFavoriteSearchCustomEvent(value: String) {}

    override fun logExploreSearchCustomEvent(value: String) {}

    override fun logTextCustomEvent(value: String) {
        lastLoggedText = value
    }

    override fun logDisplayNameCustomEvent(value: String) {
        lastLoggedDisplayName = value
    }

    override fun logUsernameCustomEvent(value: String) {
        lastLoggedUsername = value
    }
}