package com.jiahan.smartcamera.note

import android.content.res.Resources
import com.jiahan.smartcamera.core.common.R
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.ErrorMessage
import com.jiahan.smartcamera.util.resolve
import com.jiahan.smartcamera.util.toErrorMessage
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject

/**
 * What a note screen's `actionError` snackbar reports.
 *
 * An identity rather than a message, so no ViewModel exposing it resolves a string resource; the
 * screen calls [resolve]. [ShareFailed] is its own case rather than an [ErrorMessage] because
 * nothing threw -- [NoteShareDelegate] downloaded every file and got none back.
 */
sealed interface NoteActionError {
    data class Failed(val message: ErrorMessage) : NoteActionError
    data object ShareFailed : NoteActionError
}

/** The text this error shows. */
fun NoteActionError.resolve(resources: Resources): String = when (this) {
    is NoteActionError.Failed -> message.resolve(resources)
    NoteActionError.ShareFailed -> resources.getString(R.string.share_note_failure)
}

/**
 * The one error flow a note screen shows, whoever reported onto it.
 *
 * Scoped per ViewModel, and that scope is now load-bearing rather than defensive: a ViewModel
 * exposes this as its own `actionError`, while [NoteShareDelegate] -- which it injects separately
 * -- reports share failures onto it. Without [ViewModelScoped] the two would get different
 * instances with different flows, and every share failure would be silently dropped by the screen
 * observing the other one. It used to be `NoteActionsDelegate` making the same argument; that class
 * inlined into its four callers when the Room mirror made it two lines long.
 */
@ViewModelScoped
class NoteErrorReporter @Inject constructor(
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