package com.jiahan.smartcamera.util

/**
 * Central error handler that:
 *  1. Records errors for observability.
 *  2. Converts a [Throwable] into a user-visible error message string.
 *
 * Deliberately free of Android and Firebase types, so injecting it into a repository imports no
 * platform dependency. How errors are actually recorded, and where the fallback string comes
 * from, is [DefaultErrorHandler]'s business.
 */
interface ErrorHandler {

    /**
     * Records the exception for observability.
     * Always call this before displaying any error to the user.
     */
    fun logError(throwable: Throwable, tag: String = ErrorTag.DEFAULT)

    /**
     * Returns a user-friendly string for the given [throwable].
     * Prefer this over accessing [Throwable.localizedMessage] directly.
     *
     * ViewModel layer only — the result is user-facing presentation, not data. Repositories log
     * and then either propagate or fold the failure into a `Result`, and the ViewModel turns that
     * into an error field on its `UiState`.
     */
    fun getErrorMessage(throwable: Throwable): String
}

/**
 * Well-known [ErrorHandler.logError] tags shared across features, kept in one place
 * so call sites don't drift on ad-hoc string literals.
 */
object ErrorTag {
    /**
     * Default for [ErrorHandler.logError]. Deliberately not "AppError": that now names the
     * `domain.AppError` type, and a logcat filter on it would return every untagged error in the
     * app rather than that failure vocabulary.
     */
    const val DEFAULT = "SmartPhotos"
    const val IMAGE_LOAD = "ImageLoad"
    const val VIDEO_LOAD = "VideoLoad"
}