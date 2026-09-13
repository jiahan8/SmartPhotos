package com.jiahan.smartcamera.fake

import com.jiahan.smartcamera.util.ErrorHandler

/**
 * [ErrorHandler] test double. Records logged throwables so a test can assert what was logged.
 *
 * It used to resolve messages as well, with an `appErrorMessage` lambda standing in for :app's
 * string mapper so a test would not certify a developer-facing message the app never shows. A
 * ViewModel now puts an `ErrorMessage` identity on its state instead of asking for text, so there
 * is nothing left to fake but the log.
 */
class FakeErrorHandler : ErrorHandler {

    val loggedErrors = mutableListOf<Throwable>()

    override fun logError(throwable: Throwable, tag: String) {
        loggedErrors += throwable
    }
}