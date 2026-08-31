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

    /**
     * The requested username is already held by another account.
     *
     * Raised by [com.jiahan.smartcamera.data.repository.UserRepository] when the
     * createUserProfile/updateUsername Cloud Function rejects the reservation. The function
     * signals it as an `ALREADY_EXISTS` HttpsError whose text is hardcoded English, so folding it
     * to an identity here is what keeps that text off the screen -- and keeps the Firebase type out
     * of the ViewModel layer, which used to inspect it through `usernameErrorMessageResId`.
     */
    class UsernameTaken : AppError("Username already taken")

    /** The requested username is one the server refuses to reserve. */
    class UsernameReserved : AppError("Username reserved")

    /*
     * The createNote/updateNote validation failures.
     *
     * These fold a Cloud Function rejection the way [UsernameTaken] does, with one difference
     * worth knowing: all of createNote's validation errors share a single `invalid-argument` code,
     * so the repository tells them apart by the structured `details.reason` payload rather than by
     * the code. That is why there is a case per *reason* here rather than per code -- and it is
     * what let `noteErrorMessageResId` be deleted rather than moved when `note/` became a module,
     * since reading a FirebaseFunctionsException above the repository boundary would have put
     * firebase-functions on a feature module's classpath.
     */

    /** The note's text is longer than the server accepts. */
    class NoteTextTooLong : AppError("Note text too long")

    /** The note carries more media items than the server accepts. */
    class NoteMediaLimitExceeded : AppError("Too many media items")

    /** The note has neither text nor media. */
    class NoteEmpty : AppError("Note must have text or media")
}