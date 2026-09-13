package com.jiahan.smartcamera.fake

import com.jiahan.smartcamera.data.repository.UserRepository
import com.jiahan.smartcamera.domain.MediaUri
import com.jiahan.smartcamera.domain.ProfilePictureUpdate
import com.jiahan.smartcamera.domain.User
import kotlinx.datetime.LocalDate

/**
 * In-memory [UserRepository] test double. Returns [user] for lookups and records profile updates.
 */
class FakeUserRepository : UserRepository {

    var user: User? = null

    /** Answers [getUser] in place of [user] when set -- e.g. a failed load. */
    var getUserResult: Result<User?>? = null
    var uploadProfilePictureResult: Result<String?> =
        Result.success("https://example.com/profile.jpg")
    var updateUserProfileResult: Result<Unit> = Result.success(Unit)

    var getUserCallCount = 0
    var updateUserProfileCallCount = 0
    var lastUpdatedDisplayName: String? = null
    var lastUpdatedUsername: String? = null
    var lastUpdatedProfilePicture: ProfilePictureUpdate? = null
    var lastUploadedProfilePictureUri: MediaUri? = null

    override suspend fun getUser(): Result<User?> {
        getUserCallCount++
        return getUserResult ?: Result.success(user)
    }

    override suspend fun getUser(userId: String): Result<User?> = Result.success(user)

    override suspend fun createUserProfile(metadata: String, username: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun updateUserProfile(
        displayName: String?,
        username: String?,
        profilePicture: ProfilePictureUpdate
    ): Result<Unit> {
        updateUserProfileCallCount++
        lastUpdatedDisplayName = displayName
        lastUpdatedUsername = username
        lastUpdatedProfilePicture = profilePicture
        return updateUserProfileResult
    }

    override suspend fun uploadProfilePicture(uri: MediaUri): Result<String?> {
        lastUploadedProfilePictureUri = uri
        return uploadProfilePictureResult
    }

    override suspend fun updateFcmToken(token: String): Result<Unit> = Result.success(Unit)

    override suspend fun registerForPushNotifications(): Result<Unit> = Result.success(Unit)

    override suspend fun unregisterFromPushNotifications(): Result<Unit> = Result.success(Unit)

    override suspend fun recordUserActivity(activeDay: LocalDate): Result<Unit> =
        Result.success(Unit)
}