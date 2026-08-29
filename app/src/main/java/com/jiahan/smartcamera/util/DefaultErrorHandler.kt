package com.jiahan.smartcamera.util

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.jiahan.smartcamera.BuildConfig
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.domain.AppError
import javax.inject.Inject

/**
 * The Android-bound [ErrorHandler]: records to [FirebaseCrashlytics] in release builds or [Log]
 * in debug, and resolves the fallback message through [ResourceProvider].
 *
 * This is the half of the contract that cannot leave the Android source set, which is why it
 * lives apart from the interface rather than beside it.
 */
class DefaultErrorHandler @Inject constructor(
    private val resourceProvider: ResourceProvider
) : ErrorHandler {

    override fun logError(throwable: Throwable, tag: String) {
        if (BuildConfig.DEBUG) {
            Log.e(tag, throwable.message ?: "Unknown error", throwable)
        } else {
            FirebaseCrashlytics.getInstance().recordException(throwable)
        }
    }

    /**
     * [AppError] is resolved first: those carry a developer-facing message, so falling through to
     * [Throwable.localizedMessage] would show it to the user.
     */
    override fun getErrorMessage(throwable: Throwable): String =
        (throwable as? AppError)?.let { resourceProvider.getString(appErrorMessageResId(it)) }
            ?: throwable.localizedMessage?.takeIf { it.isNotBlank() }
            ?: resourceProvider.getString(R.string.error_occurred)
}