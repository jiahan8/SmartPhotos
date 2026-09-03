package com.jiahan.smartcamera.note

import com.jiahan.smartcamera.util.ErrorHandler
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject

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
    private val _actionError = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val actionError = _actionError.asSharedFlow()

    fun reportError(message: String) {
        _actionError.tryEmit(message)
    }

    fun reportError(e: Throwable) {
        errorHandler.logError(e)
        _actionError.tryEmit(errorHandler.getErrorMessage(e))
    }
}