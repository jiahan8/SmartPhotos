package com.jiahan.smartcamera.note

import com.jiahan.smartcamera.util.ErrorHandler
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject

/**
 * Scoped per ViewModel: both [NoteActionsDelegate] and [NoteShareDelegate] report through this
 * single [actionError] stream, so a ViewModel that injects either (or both, directly or
 * transitively) sees every reported error on one flow instead of two separate ones. Without
 * [ViewModelScoped], a ViewModel doing so would get two separate instances with two separate
 * flows, silently dropping errors reported through whichever instance it isn't observing.
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