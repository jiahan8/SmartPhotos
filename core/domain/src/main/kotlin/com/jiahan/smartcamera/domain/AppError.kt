package com.jiahan.smartcamera.domain

/**
 * A failure the data layer raises itself, as opposed to one it surfaces from Firebase.
 *
 * Repositories must not build user-facing text — resolving a string resource is presentation, and
 * belongs to the ViewModel layer — so these carry an identity rather than a message.
 * `util/ErrorMessageMappers.kt` maps each to its string resource and `DefaultErrorHandler`
 * applies that mapping, so a ViewModel that already routes failures through `ErrorHandler` needs
 * no change to render them.
 *
 * Free of Android types, so it travels with the repository interfaces rather than staying behind
 * with the Android implementations.
 *
 * Each case is a class rather than a `data object` so every throw captures its own stack trace.
 * The constructor messages are developer-facing only; they are never shown to a user.
 */
sealed class AppError(message: String) : Exception(message) {

    /** An operation that requires a signed-in user was attempted without one. */
    class NotAuthenticated : AppError("No authenticated user")

    /** The requested note is missing, or the author record it points at is. */
    class NoteUnavailable : AppError("Note unavailable")

    /** A media item to upload carried neither a photo nor a video location. */
    class NoMediaAvailable : AppError("No media URI available for upload")
}