package com.jiahan.smartcamera.data.repository

/**
 * Data-layer contract for all Firebase Authentication operations.
 *
 * Every fallible operation returns [Result] so callers never need try/catch.
 * Properties [currentUserId] and [isCurrentUserEmailVerified] are synchronous and
 * reflect the in-memory Firebase Auth state; they may return null / false when no
 * user is signed in.
 */
interface AuthRepository {
    val currentUserId: String?
    val isCurrentUserEmailVerified: Boolean
    suspend fun signIn(email: String, password: String): Result<Unit>
    suspend fun signUp(
        email: String,
        password: String,
        displayName: String,
        username: String
    ): Result<Unit>

    suspend fun signOut(): Result<Unit>
    suspend fun resetPassword(email: String): Result<Unit>
    suspend fun checkEmailVerified(): Result<Boolean>
    suspend fun sendEmailVerification(): Result<Unit>
    suspend fun deleteAccount(): Result<Unit>
    suspend fun isUsernameAvailable(username: String): Result<Boolean>
    suspend fun isEmailRegistered(email: String): Result<Boolean>
}