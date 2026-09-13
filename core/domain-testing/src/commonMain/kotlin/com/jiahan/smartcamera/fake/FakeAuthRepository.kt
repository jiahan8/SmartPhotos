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
    var changePasswordResult: Result<Unit> = Result.success(Unit)
    var checkEmailVerifiedResult: Result<Boolean> = Result.success(true)
    var sendEmailVerificationResult: Result<Unit> = Result.success(Unit)
    var deleteAccountResult: Result<Unit> = Result.success(Unit)
    var usernameAvailableResult: Result<Boolean> = Result.success(true)
    var emailRegisteredResult: Result<Boolean> = Result.success(true)

    /**
     * Per-call answers for the calls a test holds in flight -- a sign-in, an availability check or
     * a password change still suspended while the test looks at the Loading state. Null falls back
     * to the matching result above.
     */
    var signInAnswer: (suspend (email: String, password: String) -> Result<Unit>)? = null
    var usernameAvailableAnswer: (suspend (username: String) -> Result<Boolean>)? = null
    var changePasswordAnswer:
        (suspend (currentPassword: String, newPassword: String) -> Result<Unit>)? = null

    var signInCallCount = 0
    var signUpCallCount = 0
    var signOutCallCount = 0
    var deleteAccountCallCount = 0
    var changePasswordCallCount = 0
    var lastSignInEmail: String? = null
    var lastChangePasswordArgs: Pair<String, String>? = null

    override suspend fun signIn(email: String, password: String): Result<Unit> {
        signInCallCount++
        lastSignInEmail = email
        return signInAnswer?.invoke(email, password) ?: signInResult
    }

    override suspend fun signUp(
        email: String,
        password: String,
        displayName: String,
        username: String
    ): Result<Unit> {
        signUpCallCount++
        return signUpResult
    }

    override suspend fun signOut(): Result<Unit> {
        signOutCallCount++
        return signOutResult
    }

    override suspend fun resetPassword(email: String): Result<Unit> = resetPasswordResult

    override suspend fun changePassword(
        currentPassword: String,
        newPassword: String
    ): Result<Unit> {
        changePasswordCallCount++
        lastChangePasswordArgs = currentPassword to newPassword
        return changePasswordAnswer?.invoke(currentPassword, newPassword) ?: changePasswordResult
    }

    override suspend fun checkEmailVerified(): Result<Boolean> = checkEmailVerifiedResult

    override suspend fun sendEmailVerification(): Result<Unit> = sendEmailVerificationResult

    override suspend fun deleteAccount(): Result<Unit> {
        deleteAccountCallCount++
        return deleteAccountResult
    }

    /** Each username [isUsernameAvailable] was asked about, in order. */
    val checkedUsernames = mutableListOf<String>()

    override suspend fun isUsernameAvailable(username: String): Result<Boolean> {
        checkedUsernames += username
        return usernameAvailableAnswer?.invoke(username) ?: usernameAvailableResult
    }

    override suspend fun isEmailRegistered(email: String): Result<Boolean> = emailRegisteredResult
}