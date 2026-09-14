package com.jiahan.smartcamera.data.repository

import com.jiahan.smartcamera.util.reason
import dev.gitlive.firebase.functions.FirebaseFunctionsException
import dev.gitlive.firebase.functions.FunctionsExceptionCode
import dev.gitlive.firebase.functions.code

/**
 * What a Cloud Function rejection carries: its code, and the `details.reason` it attached, if any.
 *
 * The callable seams -- [NoteCallable] and [UserCallable] -- hand their repositories this rather than
 * the `FirebaseFunctionsException` itself, because GitLive's exception has no constructor a
 * `commonTest` can call, and folding those rejections into `AppError`s is behaviour both suites pin.
 */
internal class CallableRejection(
    val code: FunctionsExceptionCode,
    val reason: String?,
)

/** This throwable as a [CallableRejection] if it is a callable's rejection, or null otherwise. */
internal fun Throwable.toCallableRejection(): CallableRejection? =
    (this as? FirebaseFunctionsException)?.let { CallableRejection(it.code, it.reason()) }