package com.jiahan.smartcamera.fake

import com.jiahan.smartcamera.util.ErrorHandler

/**
 * [ErrorHandler] test double. Records logged throwables and returns their message verbatim so UI
 * error states are deterministic and assertable.
 */
class FakeErrorHandler(
    private val fallbackMessage: String = "An error occurred"
) : ErrorHandler {

    val loggedErrors = mutableListOf<Throwable>()

    override fun logError(throwable: Throwable, tag: String) {
        loggedErrors += throwable
    }

    override fun getErrorMessage(throwable: Throwable): String =
        throwable.message?.takeIf { it.isNotBlank() } ?: fallbackMessage
}