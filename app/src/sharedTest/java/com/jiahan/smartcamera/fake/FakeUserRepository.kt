package com.jiahan.smartcamera.fake

import android.net.Uri
import com.jiahan.smartcamera.data.repository.UserRepository
import com.jiahan.smartcamera.domain.User

/**
 * In-memory [UserRepository] test double. Returns [user] for lookups and records profile updates.
 */
class FakeUserRepository : UserRepository {

    var user: User? = null
    var uploadedUrl: String? = "https://example.com/profile.jpg"
    var updateUserProfileResult: Result<Unit> = Result.success(Unit)

    var updateUserProfileCallCount = 0
    var lastUpdatedDisplayName: String? = null
    var lastUpdatedUsername: String? = null

    override suspend fun getUser(): Result<User?> = Result.success(user)

    override suspend fun getUser(userId: String): Result<User?> = Result.success(user)

    override suspend fun createUserProfile(metadata: String, username: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun updateUserProfile(
        displayName: String?,
        username: String?,
        profilePictureUri: Uri?,
        profilePictureUrl: String?,
        deleteProfilePicture: Boolean
    ): Result<Unit> {
        updateUserProfileCallCount++
        lastUpdatedDisplayName = displayName
        lastUpdatedUsername = username
        return updateUserProfileResult
    }

    override suspend fun uploadProfilePicture(uri: Uri): Result<String?> =
        Result.success(uploadedUrl)
}