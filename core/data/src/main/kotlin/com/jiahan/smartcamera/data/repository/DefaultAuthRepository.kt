package com.jiahan.smartcamera.data.repository

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.functions.FirebaseFunctions
import com.jiahan.smartcamera.data.LocalUserDataCleaner
import com.jiahan.smartcamera.domain.AppError
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.safeCall
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class DefaultAuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val functions: FirebaseFunctions,
    private val userRepository: UserRepository,
    private val localUserDataCleaner: LocalUserDataCleaner,
    private val errorHandler: ErrorHandler,
) : AuthRepository {

    companion object {
        private const val FIELD_EMAIL = "email"
        private const val FIELD_USERNAME = "username"
        private const val FIELD_REGISTERED = "registered"
        private const val FIELD_AVAILABLE = "available"
        private const val FUNCTION_IS_EMAIL_REGISTERED = "isEmailRegistered"
        private const val FUNCTION_IS_USERNAME_AVAILABLE = "isUsernameAvailable"
    }

    override val currentUserId: String?
        get() = auth.uid
    override val isCurrentUserEmailVerified: Boolean
        get() = auth.currentUser?.isEmailVerified == true

    override suspend fun signIn(email: String, password: String): Result<Unit> = safeCall {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    override suspend fun signUp(
        email: String,
        password: String,
        displayName: String,
        username: String
    ): Result<Unit> = safeCall {
        // The `?.` calls below are not the same shape as the guarded operations above: they run
        // immediately after `createUserWithEmailAndPassword` succeeded, so `currentUser` is the
        // account this call just made, and the rollback's `?.delete()` must not mask the profile
        // failure it is reacting to.
        auth.createUserWithEmailAndPassword(email, password).await()
        auth.currentUser?.updateProfile(
            userProfileChangeRequest { this.displayName = displayName }
        )?.await()
        auth.currentUser?.sendEmailVerification()?.await()
        userRepository.createUserProfile(metadata = password, username = username)
            .onFailure {
                // Profile creation failed after the Auth account was created; delete it
                // so the user isn't left with an orphaned account and can retry signup.
                auth.currentUser?.delete()?.await()
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
        auth.sendPasswordResetEmail(email).await()
    }

    override suspend fun changePassword(
        currentPassword: String,
        newPassword: String
    ): Result<Unit> = safeCall {
        val user = auth.currentUser ?: throw AppError.NotAuthenticated()
        val email = user.email ?: throw AppError.NotAuthenticated()
        val credential = EmailAuthProvider.getCredential(email, currentPassword)
        user.reauthenticate(credential).await()
        user.updatePassword(newPassword).await()
    }

    override suspend fun checkEmailVerified(): Result<Boolean> = safeCall {
        auth.currentUser?.reload()?.await()
        auth.currentUser?.isEmailVerified == true
    }

    override suspend fun sendEmailVerification(): Result<Unit> = safeCall {
        val user = auth.currentUser ?: throw AppError.NotAuthenticated()
        user.sendEmailVerification().await()
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
        user.delete().await()
        localUserDataCleaner.clearLocalUserData()
    }

    override suspend fun isUsernameAvailable(username: String): Result<Boolean> = safeCall {
        // Non-authoritative fast pre-check; createUserProfile/updateUsername
        // enforce uniqueness atomically via the same `username` collection.
        // Routed through a callable rather than a direct Firestore read
        // since that collection is fully locked down in firestore.rules.
        val result = functions.getHttpsCallable(FUNCTION_IS_USERNAME_AVAILABLE)
            .call(hashMapOf(FIELD_USERNAME to username))
            .await()
        (result.data as? Map<*, *>)?.get(FIELD_AVAILABLE) as? Boolean ?: false
    }

    override suspend fun isEmailRegistered(email: String): Result<Boolean> = safeCall {
        // Firebase Auth is the source of truth for email registration, not
        // Firestore: a user document may not exist yet even though the Auth
        // account does (e.g. app killed right after account creation).
        val result = functions.getHttpsCallable(FUNCTION_IS_EMAIL_REGISTERED)
            .call(hashMapOf(FIELD_EMAIL to email))
            .await()
        (result.data as? Map<*, *>)?.get(FIELD_REGISTERED) as? Boolean ?: false
    }
}