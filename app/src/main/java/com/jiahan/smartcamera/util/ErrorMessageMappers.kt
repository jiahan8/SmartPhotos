package com.jiahan.smartcamera.util

import com.google.firebase.functions.FirebaseFunctionsException
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.domain.AppError

/*
 * Throwable -> string resource mappers. Each is tried ahead of ErrorHandler.getErrorMessage and
 * falls back to it when it returns null.
 *
 * These sit at the ViewModel layer: they resolve string resources, which makes their result
 * user-facing presentation rather than data. Repositories must not call them.
 *
 * appErrorMessageResId is the one exception to "tried by the caller": DefaultErrorHandler applies
 * it inside getErrorMessage, because an AppError is the app's own cross-cutting failure
 * vocabulary rather than one feature's Firebase quirk, and every caller wants the same string for
 * it. The two feature mappers below stay opt-in at their call sites.
 */

/**
 * Maps an [AppError] — a failure a repository raised itself — to its localized string.
 *
 * This is what lets repositories throw an identity instead of a message: the repository names the
 * failure, and the resource lookup happens up here.
 *
 * Unlike the two mappers below it takes [AppError] rather than [Throwable] and returns a non-null
 * resource id, because every case has a string by construction. That is what makes the `when`
 * exhaustive-checked: adding an [AppError] case without a string here is a compile error rather
 * than a silent fall through to the developer-facing message. Callers narrow with `as?`.
 */
fun appErrorMessageResId(error: AppError): Int = when (error) {
    is AppError.NotAuthenticated -> R.string.user_not_authenticated
    is AppError.NoteUnavailable -> R.string.note_unavailable
    is AppError.NoMediaAvailable -> R.string.no_media_available
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