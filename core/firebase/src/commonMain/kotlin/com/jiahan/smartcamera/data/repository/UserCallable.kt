package com.jiahan.smartcamera.data.repository

import com.jiahan.smartcamera.util.reason
import dev.gitlive.firebase.functions.FirebaseFunctions
import dev.gitlive.firebase.functions.FirebaseFunctionsException
import dev.gitlive.firebase.functions.FunctionsExceptionCode
import dev.gitlive.firebase.functions.code
import kotlinx.serialization.Serializable

/**
 * The three user callables, by name, and how to read their rejections: [DefaultUserRepository]'s
 * seam onto Functions.
 *
 * [rejectionOf] sits here rather than as a cast in the repository because GitLive's
 * `FirebaseFunctionsException` has no constructor a test can call, and folding those rejections is
 * the behaviour the repository's suite exists to pin.
 */
internal interface UserCallable {
    suspend fun call(name: String, args: UserCallArgs)

    /** The code and `details.reason` [error] carries if it is a callable rejection, or null. */
    fun rejectionOf(error: Throwable): CallableRejection?
}

internal class CallableRejection(
    val code: FunctionsExceptionCode,
    val reason: String?,
)

internal class GitLiveUserCallable(
    private val functions: FirebaseFunctions,
) : UserCallable {
    override suspend fun call(name: String, args: UserCallArgs) {
        functions.httpsCallable(name)
            // encodeDefaults off, so each call sends only the keys it sets -- as the Android SDK's
            // hashMapOf did.
            .invoke(UserCallArgs.serializer(), args) { encodeDefaults = false }
    }

    override fun rejectionOf(error: Throwable): CallableRejection? =
        (error as? FirebaseFunctionsException)?.let { CallableRejection(it.code, it.reason()) }
}

@Serializable
internal class UserCallArgs(
    val metadata: String? = null,
    val username: String? = null,
    val activeDay: String? = null,
)