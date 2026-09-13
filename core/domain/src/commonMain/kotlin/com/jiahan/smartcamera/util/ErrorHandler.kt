package com.jiahan.smartcamera.util

/**
 * Records errors for observability.
 *
 * Deliberately free of Android and Firebase types, so injecting it into a repository imports no
 * platform dependency. How errors are actually recorded is `DefaultErrorHandler`'s business.
 *
 * It used to turn a [Throwable] into user-visible text as well, through `getErrorMessage`. That
 * half is [toErrorMessage] now: a plain function returning an identity the screen resolves, which
 * needs no platform and no fake, so it has no reason to sit behind an interface.
 */
interface ErrorHandler {

    /**
     * Records the exception for observability.
     * Always call this before displaying any error to the user.
     */
    fun logError(throwable: Throwable, tag: String = ErrorTag.DEFAULT)
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