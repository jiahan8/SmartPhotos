package com.jiahan.smartcamera.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.functions.FirebaseFunctions
import com.jiahan.smartcamera.util.safeCall
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class DefaultAuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val functions: FirebaseFunctions,
    private val userRepository: UserRepository,
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
        auth.createUserWithEmailAndPassword(email, password).await()
        auth.currentUser?.updateProfile(
            userProfileChangeRequest { this.displayName = displayName }
        )?.await()
        auth.currentUser?.sendEmailVerification()?.await()
        userRepository.createUserProfile(metadata = password, username = username).getOrThrow()
    }

    override suspend fun signOut(): Result<Unit> = safeCall {
        auth.signOut()
    }

    override suspend fun resetPassword(email: String): Result<Unit> = safeCall {
        auth.sendPasswordResetEmail(email).await()
    }

    override suspend fun checkEmailVerified(): Result<Boolean> = safeCall {
        auth.currentUser?.reload()?.await()
        auth.currentUser?.isEmailVerified == true
    }

    override suspend fun sendEmailVerification(): Result<Unit> = safeCall {
        auth.currentUser?.sendEmailVerification()?.await()
    }

    override suspend fun deleteAccount(): Result<Unit> = safeCall {
        auth.currentUser?.delete()?.await()
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