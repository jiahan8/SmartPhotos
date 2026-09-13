package com.jiahan.smartcamera.fake

import com.jiahan.smartcamera.data.LocalUserDataCleaner

/**
 * [LocalUserDataCleaner] test double. Counts clears; set [failure] to make them throw, as a store
 * that cannot be cleared does.
 */
class FakeLocalUserDataCleaner : LocalUserDataCleaner {

    var clearCallCount = 0
    var failure: Throwable? = null

    override suspend fun clearLocalUserData() {
        clearCallCount++
        failure?.let { throw it }
    }
}