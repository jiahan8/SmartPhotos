package com.jiahan.smartcamera.fake

import com.jiahan.smartcamera.domain.AppError
import com.jiahan.smartcamera.util.ErrorHandler

/**
 * [ErrorHandler] test double. Records logged throwables and returns their message verbatim so UI
 * error states are deterministic and assertable.
 *
 * Pass [appErrorMessage] when the code under test can fail with an [AppError]. Production
 * resolves those through a string resource, so without it this fake returns the developer-facing
 * message and the test certifies text the app never shows. In `:app` that argument reads
 * `{ resourceProvider.getString(appErrorMessageResId(it)) }`.
 *
 * It is a lambda rather than a [com.jiahan.smartcamera.util.ResourceProvider] because
 * `appErrorMessageResId` resolves `:app`'s `R` -- that is deliberate, the mapper being
 * ViewModel-layer presentation -- so calling it from here would pin this module above the
 * boundary. Hoisting the lookup to the caller is the same move that made `toFormattedDateTime`
 * testable when `:core:ui` was extracted.
 */
class FakeErrorHandler(
    private val fallbackMessage: String = "An error occurred",
    private val appErrorMessage: ((AppError) -> String)? = null
) : ErrorHandler {

    val loggedErrors = mutableListOf<Throwable>()

    override fun logError(throwable: Throwable, tag: String) {
        loggedErrors += throwable
    }

    override fun getErrorMessage(throwable: Throwable): String =
        (throwable as? AppError)?.let { error -> appErrorMessage?.invoke(error) }
            ?: throwable.message?.takeIf { it.isNotBlank() }
            ?: fallbackMessage
}