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
 * [HttpsError] text via [ErrorHandler.getErrorMessage]. Returns null for any
 * other exception type/code, so callers should fall back to
 * [ErrorHandler.getErrorMessage] in that case.
 */
fun usernameErrorMessageResId(throwable: Throwable): Int? =
    when ((throwable as? FirebaseFunctionsException)?.code) {
        FirebaseFunctionsException.Code.ALREADY_EXISTS -> R.string.username_not_available
        FirebaseFunctionsException.Code.INVALID_ARGUMENT -> R.string.username_reserved
        else -> null
    }