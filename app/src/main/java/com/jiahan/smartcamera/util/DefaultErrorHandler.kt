package com.jiahan.smartcamera.util

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.jiahan.smartcamera.BuildConfig
import javax.inject.Inject

/**
 * The Android-bound [ErrorHandler]: records to [FirebaseCrashlytics] in release builds or [Log]
 * in debug.
 *
 * This is the half of the contract that cannot leave the Android source set, which is why it
 * lives apart from the interface rather than beside it. It no longer resolves messages -- a
 * ViewModel names the failure with `toErrorMessage` and the screen resolves the text -- so it needs
 * neither a `ResourceProvider` nor this module's strings.
 */
class DefaultErrorHandler @Inject constructor() : ErrorHandler {

    override fun logError(throwable: Throwable, tag: String) {
        if (BuildConfig.DEBUG) {
            Log.e(tag, throwable.message ?: "Unknown error", throwable)
        } else {
            FirebaseCrashlytics.getInstance().recordException(throwable)
        }
    }
}