package com.jiahan.smartcamera.fake

import com.jiahan.smartcamera.data.repository.AuthRepository

/**
 * In-memory [AuthRepository] test double.
 *
 * Every result is configurable so individual tests can drive specific UI states without any
 * Firebase dependency or network access. Defaults represent a healthy, signed-in, verified user.
 */
class FakeAuthRepository : AuthRepository {

    override var currentUserId: String? = "test-uid"
    override var isCurrentUserEmailVerified: Boolean = true

    var signInResult: Result<Unit> = Result.success(Unit)
    var signUpResult: Result<Unit> = Result.success(Unit)
    var signOutResult: Result<Unit> = Result.success(Unit)
    var resetPasswordResult: Result<Unit> = Result.success(Unit)
    var checkEmailVerifiedResult: Result<Boolean> = Result.success(true)
    var sendEmailVerificationResult: Result<Unit> = Result.success(Unit)
    var deleteAccountResult: Result<Unit> = Result.success(Unit)
    var usernameAvailableResult: Result<Boolean> = Result.success(true)
    var emailRegisteredResult: Result<Boolean> = Result.success(true)

    var signInCallCount = 0
    var signOutCallCount = 0
    var deleteAccountCallCount = 0
    var lastSignInEmail: String? = null

    override suspend fun signIn(email: String, password: String): Result<Unit> {
        signInCallCount++
        lastSignInEmail = email
        return signInResult
    }

    override suspend fun signUp(
        email: String,
        password: String,
        displayName: String,
        username: String
    ): Result<Unit> = signUpResult

    override suspend fun signOut(): Result<Unit> {
        signOutCallCount++
        return signOutResult
    }

    override suspend fun resetPassword(email: String): Result<Unit> = resetPasswordResult

    override suspend fun checkEmailVerified(): Result<Boolean> = checkEmailVerifiedResult

    override suspend fun sendEmailVerification(): Result<Unit> = sendEmailVerificationResult

    override suspend fun deleteAccount(): Result<Unit> {
        deleteAccountCallCount++
        return deleteAccountResult
    }

    override suspend fun isUsernameAvailable(username: String): Result<Boolean> =
        usernameAvailableResult

    override suspend fun isEmailRegistered(email: String): Result<Boolean> = emailRegisteredResult
}