package com.jiahan.smartcamera.data.repository

import com.jiahan.smartcamera.domain.MediaUri
import com.jiahan.smartcamera.domain.ProfilePictureUpdate
import com.jiahan.smartcamera.domain.User
import kotlinx.datetime.LocalDate

interface UserRepository {
    suspend fun getUser(): Result<User?>
    suspend fun getUser(userId: String): Result<User?>
    suspend fun createUserProfile(metadata: String, username: String): Result<Unit>
    suspend fun updateUserProfile(
        displayName: String?,
        username: String?,
        profilePicture: ProfilePictureUpdate = ProfilePictureUpdate.Keep
    ): Result<Unit>

    suspend fun uploadProfilePicture(uri: MediaUri): Result<String?>

    /** Saves the given FCM registration token to the caller's own user doc. No-ops if signed out. */
    suspend fun updateFcmToken(token: String): Result<Unit>

    /** Fetches the current FCM token, saves it, and subscribes the device to the announcements' topic. */
    suspend fun registerForPushNotifications(): Result<Unit>

    /** Unsubscribes the device from the announcements topic and clears the stored FCM token. */
    suspend fun unregisterFromPushNotifications(): Result<Unit>

    /** Records the signed-in user's activity for [activeDay]; safe to call on every app open. */
    suspend fun recordUserActivity(activeDay: LocalDate): Result<Unit>
}