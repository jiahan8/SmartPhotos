package com.jiahan.smartcamera.fake

import com.jiahan.smartcamera.domain.AppError
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.ResourceProvider
import com.jiahan.smartcamera.util.appErrorMessageResId

/**
 * [ErrorHandler] test double. Records logged throwables and returns their message verbatim so UI
 * error states are deterministic and assertable.
 *
 * Pass [resourceProvider] (a [FakeResourceProvider] will do) when the code under test can fail
 * with an [AppError]. Production resolves those through a string resource, so without one this
 * fake would return the developer-facing message and the test would certify text the app never
 * shows.
 */
class FakeErrorHandler(
    private val fallbackMessage: String = "An error occurred",
    private val resourceProvider: ResourceProvider? = null
) : ErrorHandler {

    val loggedErrors = mutableListOf<Throwable>()

    override fun logError(throwable: Throwable, tag: String) {
        loggedErrors += throwable
    }

    override fun getErrorMessage(throwable: Throwable): String =
        (throwable as? AppError)?.let { error ->
            resourceProvider?.getString(appErrorMessageResId(error))
        }
            ?: throwable.message?.takeIf { it.isNotBlank() }
            ?: fallbackMessage
}