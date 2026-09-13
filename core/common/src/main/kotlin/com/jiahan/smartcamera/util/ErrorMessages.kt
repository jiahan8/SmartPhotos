package com.jiahan.smartcamera.util

import android.content.res.Resources
import com.jiahan.smartcamera.core.common.R
import com.jiahan.smartcamera.domain.AppError

/*
 * ErrorMessage -> text.
 *
 * The presentation half of `ErrorMessage`: a ViewModel names the failure, and the screen calls
 * `resolve` with `LocalResources.current` to get the string. This was `ErrorMessageMappers.kt` in
 * :app, applied for every feature inside `DefaultErrorHandler.getErrorMessage`, and it came down
 * here -- with the four strings it read -- once screens rather than ViewModels resolved the text.
 * Every feature screen can see this module; none can see :app.
 *
 * `Resources` rather than a composable, for two reasons. This module is deliberately not Compose,
 * and a snackbar shown from a `LaunchedEffect` needs its string outside composition anyway. A
 * screen reads `LocalResources.current` during composition, so a configuration change still
 * recomposes it against the new resources.
 */

/** The text this message shows. */
fun ErrorMessage.resolve(resources: Resources): String = when (this) {
    is ErrorMessage.Known -> resources.getString(appErrorMessageResId(error))
    is ErrorMessage.Unlocalized -> text
    ErrorMessage.Generic -> resources.getString(R.string.error_occurred)
}

/**
 * Maps an [AppError] — a failure a repository raised itself — to its localized string.
 *
 * This is what lets repositories throw an identity instead of a message: the repository names the
 * failure, and the resource lookup happens at the screen.
 *
 * It takes [AppError] rather than [Throwable] and returns a non-null resource id, because every
 * case has a string by construction. That is what makes the `when` exhaustive-checked: adding an
 * [AppError] case without a string here is a compile error rather than a silent fall-through to the
 * developer-facing message.
 */
fun appErrorMessageResId(error: AppError): Int = when (error) {
    is AppError.NotAuthenticated -> R.string.user_not_authenticated
    is AppError.NoteUnavailable -> R.string.note_unavailable
    is AppError.NoMediaAvailable -> R.string.no_media_available
    // Also rendered by AuthScreen and ProfileScreen for their own availability pre-checks, and by
    // `validationErrorMessageResId` for the reserved-name rule -- the same copy for the same
    // failure, whichever side of the request caught it.
    is AppError.UsernameTaken -> R.string.username_not_available
    is AppError.UsernameReserved -> R.string.username_reserved
    // The first two are also note/'s own client-side checks, shown before a request is sent.
    is AppError.NoteTextTooLong -> R.string.note_validation
    is AppError.NoteMediaLimitExceeded -> R.string.note_media_limit
    is AppError.NoteEmpty -> R.string.note_empty
}