package com.jiahan.smartcamera.fake

import com.jiahan.smartcamera.data.repository.AnalyticsRepository

/**
 * No-op [AnalyticsRepository] test double. Records the last logged value for optional assertions.
 */
class FakeAnalyticsRepository : AnalyticsRepository {

    var lastLoggedText: String? = null

    override fun logSearchEvent(value: String) {}

    override fun logSearchCustomEvent(value: String) {}

    override fun logNoteCustomEvent(value: String) {}

    override fun logFavoriteSearchCustomEvent(value: String) {}

    override fun logTextCustomEvent(value: String) {
        lastLoggedText = value
    }
}