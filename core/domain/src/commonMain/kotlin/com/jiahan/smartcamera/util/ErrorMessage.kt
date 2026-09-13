package com.jiahan.smartcamera.util

import com.jiahan.smartcamera.domain.AppError

/**
 * What a failure should say to the user, as an identity rather than as text.
 *
 * A ViewModel turns a caught [Throwable] into one of these with [toErrorMessage] and puts it on its
 * `UiState`; the screen resolves it to a string (`ErrorMessage.resolve`, :core:common). It used to
 * be the other way round -- `ErrorHandler.getErrorMessage` returned a finished `String`, looked up
 * through an injected `ResourceProvider` -- which put Android string resources inside every
 * ViewModel and froze the text at the moment of failure: switch the app's language with an error on
 * screen and the error stayed in the old one. A screen resolving it reads the resources current
 * when it draws.
 *
 * The same identity/mapper split [AppError] and [ValidationError] already follow, one layer up --
 * and what lets a ViewModel's error state compile in `commonMain`.
 */
sealed interface ErrorMessage {

    /** A failure the app named itself; the screen shows that case's own string. */
    data class Known(val error: AppError) : ErrorMessage

    /**
     * A failure that arrived with text of its own -- in practice a Firebase SDK exception's
     * message.
     *
     * Shown verbatim, so **not localized**: it is in whatever language the SDK wrote it. That was
     * equally true of the `localizedMessage` fallback this replaces; the name keeps the limitation
     * visible rather than new.
     */
    data class Unlocalized(val text: String) : ErrorMessage

    /** A failure with nothing to say; the screen shows the generic "an error occurred". */
    data object Generic : ErrorMessage
}

/**
 * Names the message for this failure.
 *
 * [AppError] is checked first: those carry a developer-facing message, so falling through to
 * [Throwable.message] would show it to the user.
 *
 * [Throwable.message] rather than `localizedMessage`, which exists only on the JVM. There it
 * returns `message` unless a subclass overrides it.
 */
fun Throwable.toErrorMessage(): ErrorMessage {
    if (this is AppError) return ErrorMessage.Known(this)
    val text = message
    return if (text.isNullOrBlank()) ErrorMessage.Generic else ErrorMessage.Unlocalized(text)
}