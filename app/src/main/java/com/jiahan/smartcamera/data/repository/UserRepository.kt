package com.jiahan.smartcamera.data.repository

import android.net.Uri
import com.jiahan.smartcamera.domain.User

interface UserRepository {
    suspend fun getUser(): Result<User?>
    suspend fun getUser(userId: String): Result<User?>
    suspend fun createUserProfile(metadata: String, username: String): Result<Unit>
    suspend fun updateUserProfile(
        displayName: String?,
        username: String?,
        profilePictureUri: Uri?,
        profilePictureUrl: String?,
        deleteProfilePicture: Boolean = false
    ): Result<Unit>

    suspend fun uploadProfilePicture(uri: Uri): Result<String?>
}