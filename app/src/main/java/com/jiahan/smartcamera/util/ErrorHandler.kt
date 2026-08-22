package com.jiahan.smartcamera.util

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.functions.FirebaseFunctionsException
import com.jiahan.smartcamera.BuildConfig
import com.jiahan.smartcamera.R
import javax.inject.Inject

/**
 * Central error handler that:
 *  1. Logs errors to [FirebaseCrashlytics] in release builds or [Log] in debug.
 *  2. Converts a [Throwable] into a user-visible error message string.
 */
interface ErrorHandler {

    /**
     * Records the exception for observability.
     * Always call this before displaying any error to the user.
     */
    fun logError(throwable: Throwable, tag: String = "AppError")

    /**
     * Returns a user-friendly string for the given [throwable].
     * Prefer this over accessing [Throwable.localizedMessage] directly.
     */
    fun getErrorMessage(throwable: Throwable): String
}

/**
 * Well-known [ErrorHandler.logError] tags shared across features, kept in one place
 * so call sites don't drift on ad-hoc string literals.
 */
object ErrorTag {
    const val IMAGE_LOAD = "ImageLoad"
    const val VIDEO_LOAD = "VideoLoad"
}

class DefaultErrorHandler @Inject constructor(
    private val resourceProvider: ResourceProvider
) : ErrorHandler {

    override fun logError(throwable: Throwable, tag: String) {
        if (BuildConfig.DEBUG) {
            Log.e(tag, throwable.message ?: "Unknown error", throwable)
        } else {
            FirebaseCrashlytics.getInstance().recordException(throwable)
        }
    }

    override fun getErrorMessage(throwable: Throwable): String =
        throwable.localizedMessage?.takeIf { it.isNotBlank() }
            ?: resourceProvider.getString(R.string.error_occurred)
}

/**
 * Maps a username-conflict [FirebaseFunctionsException] thrown by the
 * createUserProfile/updateUsername Cloud Functions to the matching localized
 * string resource, so callers don't leak the raw hardcoded English
 * `HttpsError` text via [ErrorHandler.getErrorMessage]. Returns null for any
 * other exception type/code, so callers should fall back to
 * [ErrorHandler.getErrorMessage] in that case.
 */
fun usernameErrorMessageResId(throwable: Throwable): Int? =
    when ((throwable as? FirebaseFunctionsException)?.code) {
        FirebaseFunctionsException.Code.ALREADY_EXISTS -> R.string.username_not_available
        FirebaseFunctionsException.Code.INVALID_ARGUMENT -> R.string.username_reserved
        else -> null
    }

/**
 * Maps the `reason` detail of an `invalid-argument` [FirebaseFunctionsException.Code]
 * error thrown by the createNote Cloud Function to the matching localized
 * string resource. All of createNote's validation errors share that single
 * code, so unlike [usernameErrorMessageResId], this reads the structured
 * `details` payload rather than the error code to tell them apart. Returns
 * null for reasons with no user-facing string (they indicate a malformed
 * request no legitimate client can produce) or any other exception type, so
 * callers should fall back to [ErrorHandler.getErrorMessage] in that case.
 */
fun noteErrorMessageResId(throwable: Throwable): Int? {
    val reason = ((throwable as? FirebaseFunctionsException)?.details as? Map<*, *>)
        ?.get("reason") as? String
    return when (reason) {
        "TEXT_TOO_LONG" -> R.string.note_validation
        "TOO_MANY_MEDIA_ITEMS" -> R.string.note_media_limit
        "EMPTY_NOTE" -> R.string.note_empty
        else -> null
    }
}