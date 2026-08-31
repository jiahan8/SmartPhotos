package com.jiahan.smartcamera.util

import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.core.common.R as CommonR
import com.jiahan.smartcamera.domain.AppError

/*
 * AppError -> string resource mapping.
 *
 * This sits at the ViewModel layer: it resolves string resources, which makes its result
 * user-facing presentation rather than data. Repositories must not call it.
 *
 * appErrorMessageResId is applied by DefaultErrorHandler inside getErrorMessage, because an
 * AppError is the app's own failure vocabulary and every caller wants the same string for it.
 *
 * There were three, and now there is one. `usernameErrorMessageResId` read an
 * ALREADY_EXISTS/INVALID_ARGUMENT code off a FirebaseFunctionsException; `noteErrorMessageResId`
 * read a structured `details.reason` payload off the same type. Both are gone, folded into
 * AppError by the repositories that raise them, and the `when` below renders the result -- so five
 * ViewModel call sites shrank to a plain getErrorMessage. The reason to prefer that shape is not
 * tidiness: reading a Firebase error code is data-layer knowledge, and leaving it up here would
 * have put firebase-functions on :feature:auth's and :feature:note's classpaths for the sake of a
 * few lines. **A Firebase type read above the repository boundary is a module boundary waiting to
 * be violated.**
 *
 * What is left is appErrorMessageResId alone, which is not a feature mapper at all -- it is the
 * app's own failure vocabulary, applied inside getErrorMessage rather than at a call site.
 */

/**
 * Maps an [AppError] — a failure a repository raised itself — to its localized string.
 *
 * This is what lets repositories throw an identity instead of a message: the repository names the
 * failure, and the resource lookup happens up here.
 *
 * It takes [AppError] rather than [Throwable] and returns a non-null resource id, because every
 * case has a string by construction. That is what makes the `when`
 * exhaustive-checked: adding an [AppError] case without a string here is a compile error rather
 * than a silent fall through to the developer-facing message.
 */
fun appErrorMessageResId(error: AppError): Int = when (error) {
    is AppError.NotAuthenticated -> R.string.user_not_authenticated
    is AppError.NoteUnavailable -> R.string.note_unavailable
    is AppError.NoMediaAvailable -> R.string.no_media_available
    // :core:common's R, not :app's -- `username_not_available` is also read by AuthViewModel and
    // ProfileViewModel for their own pre-checks, and `username_reserved` by validateUsername in
    // :core:common itself, so both sit in the module every reader can see.
    is AppError.UsernameTaken -> CommonR.string.username_not_available
    is AppError.UsernameReserved -> CommonR.string.username_reserved
    // Also :core:common's, and for the same reason: note/'s own client-side validation shows the
    // first two before a request is ever sent, so they sit where both readers can see them.
    is AppError.NoteTextTooLong -> CommonR.string.note_validation
    is AppError.NoteMediaLimitExceeded -> CommonR.string.note_media_limit
    is AppError.NoteEmpty -> CommonR.string.note_empty
}