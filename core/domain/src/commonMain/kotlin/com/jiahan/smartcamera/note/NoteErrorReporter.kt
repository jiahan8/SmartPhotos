package com.jiahan.smartcamera.note

import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.ErrorMessage
import com.jiahan.smartcamera.util.toErrorMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * What a note screen's `actionError` snackbar reports.
 *
 * An identity rather than a message, so no ViewModel exposing it resolves a string resource; the
 * screen calls `resolve`, which stays in :core:common beside the string it reads. [ShareFailed] is
 * its own case rather than an [ErrorMessage] because nothing threw -- [NoteShareDelegate]
 * downloaded every file and got none back.
 */
sealed interface NoteActionError {
    data class Failed(val message: ErrorMessage) : NoteActionError
    data object ShareFailed : NoteActionError
}

/**
 * The one error flow a note screen shows, whoever reported onto it.
 *
 * One instance per ViewModel, and that is load-bearing rather than defensive: a ViewModel exposes
 * this as its own `actionError`, while [NoteShareDelegate] -- which it receives separately --
 * reports share failures onto it. Two instances would mean two flows, and every share failure
 * silently dropped by the screen observing the other one. It used to be `NoteActionsDelegate`
 * making the same argument; that class inlined into its four callers when the Room mirror made it
 * two lines long.
 *
 * In `commonMain`, so it carries no DI annotations. On Android the one-per-ViewModel guarantee is
 * `NoteDelegateModule`'s, in :core:common; anything else building a ViewModel hands the same
 * instance to both, as the screen tests do.
 */
class NoteErrorReporter(
    private val errorHandler: ErrorHandler
) {
    private val _actionError = MutableSharedFlow<NoteActionError>(extraBufferCapacity = 1)
    val actionError = _actionError.asSharedFlow()

    fun reportError(throwable: Throwable) {
        errorHandler.logError(throwable)
        _actionError.tryEmit(NoteActionError.Failed(throwable.toErrorMessage()))
    }

    fun reportShareFailure() {
        _actionError.tryEmit(NoteActionError.ShareFailed)
    }
}