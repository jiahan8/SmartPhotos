package com.jiahan.smartcamera.data.repository

import com.jiahan.smartcamera.data.LocalUserDataCleaner
import com.jiahan.smartcamera.domain.AppError
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.safeCall
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.functions.FirebaseFunctions

/**
 * [AuthRepository] on GitLive's multiplatform Auth and Functions, behind [AuthClient] and
 * [AuthCallable] so its suite runs in `commonTest`.
 */
class DefaultAuthRepository internal constructor(
    private val auth: AuthClient,
    private val callable: AuthCallable,
    private val userRepository: UserRepository,
    private val localUserDataCleaner: LocalUserDataCleaner,
    private val errorHandler: ErrorHandler,
) : AuthRepository {

    constructor(
        auth: FirebaseAuth,
        functions: FirebaseFunctions,
        userRepository: UserRepository,
        localUserDataCleaner: LocalUserDataCleaner,
        errorHandler: ErrorHandler,
    ) : this(
        GitLiveAuthClient(auth),
        GitLiveAuthCallable(functions),
        userRepository,
        localUserDataCleaner,
        errorHandler,
    )

    private companion object {
        const val FUNCTION_IS_EMAIL_REGISTERED = "isEmailRegistered"
        const val FUNCTION_IS_USERNAME_AVAILABLE = "isUsernameAvailable"
    }

    override val currentUserId: String?
        get() = auth.currentUser?.uid
    override val isCurrentUserEmailVerified: Boolean
        get() = auth.currentUser?.isEmailVerified == true

    override suspend fun signIn(email: String, password: String): Result<Unit> = safeCall {
        auth.signIn(email, password)
    }

    override suspend fun signUp(
        email: String,
        password: String,
        displayName: String,
        username: String
    ): Result<Unit> = safeCall {
        // The `?.` calls below are not the same shape as the guarded operations above: they run
        // immediately after `createUser` succeeded, so `currentUser` is the account this call just
        // made, and the rollback's `?.delete()` must not mask the profile failure it is reacting to.
        auth.createUser(email, password)
        auth.currentUser?.updateDisplayName(displayName)
        auth.currentUser?.sendEmailVerification()
        userRepository.createUserProfile(metadata = password, username = username)
            .onFailure {
                // Profile creation failed after the Auth account was created; delete it
                // so the user isn't left with an orphaned account and can retry signup.
                auth.currentUser?.delete()
            }
            .getOrThrow()
    }

    override suspend fun signOut(): Result<Unit> = safeCall {
        userRepository.unregisterFromPushNotifications()
            .onFailure(errorHandler::logError)
        auth.signOut()
        localUserDataCleaner.clearLocalUserData()
    }

    override suspend fun resetPassword(email: String): Result<Unit> = safeCall {
        auth.sendPasswordResetEmail(email)
    }

    override suspend fun changePassword(
        currentPassword: String,
        newPassword: String
    ): Result<Unit> = safeCall {
        val user = auth.currentUser ?: throw AppError.NotAuthenticated()
        val email = user.email ?: throw AppError.NotAuthenticated()
        user.reauthenticate(email, currentPassword)
        user.updatePassword(newPassword)
    }

    override suspend fun checkEmailVerified(): Result<Boolean> = safeCall {
        auth.currentUser?.reload()
        auth.currentUser?.isEmailVerified == true
    }

    override suspend fun sendEmailVerification(): Result<Unit> = safeCall {
        val user = auth.currentUser ?: throw AppError.NotAuthenticated()
        user.sendEmailVerification()
    }

    /**
     * Deletes the account, then the local user data.
     *
     * The guard is the whole point of the ordering. `auth.currentUser?.delete()` used to no-op when
     * nobody was signed in and still fall through to the clear, so an expired token turned
     * "delete my account" into a success the caller navigated on: the Firebase account and its
     * profile document survived, and every cached note was thrown away. Failing here leaves both
     * intact.
     */
    override suspend fun deleteAccount(): Result<Unit> = safeCall {
        val user = auth.currentUser ?: throw AppError.NotAuthenticated()
        user.delete()
        localUserDataCleaner.clearLocalUserData()
    }

    override suspend fun isUsernameAvailable(username: String): Result<Boolean> = safeCall {
        // Non-authoritative fast pre-check; createUserProfile/updateUsername
        // enforce uniqueness atomically via the same `username` collection.
        // Routed through a callable rather than a direct Firestore read
        // since that collection is fully locked down in firestore.rules.
        callable.call(FUNCTION_IS_USERNAME_AVAILABLE, AuthCheckArgs(username = username))
            ?.available ?: false
    }

    override suspend fun isEmailRegistered(email: String): Result<Boolean> = safeCall {
        // Firebase Auth is the source of truth for email registration, not
        // Firestore: a user document may not exist yet even though the Auth
        // account does (e.g. app killed right after account creation).
        callable.call(FUNCTION_IS_EMAIL_REGISTERED, AuthCheckArgs(email = email))
            ?.registered ?: false
    }
}