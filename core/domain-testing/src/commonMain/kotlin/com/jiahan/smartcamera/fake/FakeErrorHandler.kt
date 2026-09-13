package com.jiahan.smartcamera.fake

import com.jiahan.smartcamera.util.ErrorHandler

/**
 * [ErrorHandler] test double. Records what was logged and under which tag, so a test can assert
 * both.
 *
 * It used to resolve messages as well, with an `appErrorMessage` lambda standing in for :app's
 * string mapper so a test would not certify a developer-facing message the app never shows. A
 * ViewModel now puts an `ErrorMessage` identity on its state instead of asking for text, so there
 * is nothing left to fake but the log.
 */
class FakeErrorHandler : ErrorHandler {

    val loggedErrors = mutableListOf<Throwable>()

    /** The tag each [loggedErrors] entry was logged under, at the same index. */
    val loggedTags = mutableListOf<String>()

    override fun logError(throwable: Throwable, tag: String) {
        loggedErrors += throwable
        loggedTags += tag
    }
}