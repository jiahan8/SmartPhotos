package com.jiahan.smartcamera.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.jiahan.smartcamera.util.safeCall
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class DefaultAuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val userRepository: UserRepository,
) : AuthRepository {
    companion object {
        private const val COLLECTION_MEMBER = "member"
        private const val FIELD_USERNAME = "username"
        private const val FIELD_EMAIL = "email"
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
        firestore.collection(COLLECTION_MEMBER)
            .whereEqualTo(FIELD_USERNAME, username)
            .limit(1)
            .get()
            .await()
            .isEmpty
    }

    override suspend fun isEmailRegistered(email: String): Result<Boolean> = safeCall {
        !firestore.collection(COLLECTION_MEMBER)
            .whereEqualTo(FIELD_EMAIL, email)
            .limit(1)
            .get()
            .await()
            .isEmpty
    }
}