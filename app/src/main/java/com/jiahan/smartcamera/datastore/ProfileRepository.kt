package com.jiahan.smartcamera.datastore

import android.net.Uri
import com.google.firebase.auth.FirebaseUser
import com.jiahan.smartcamera.domain.User
import kotlinx.coroutines.flow.Flow
import kotlin.Boolean

interface ProfileRepository {
    val userPreferencesFlow: Flow<UserPreferences>
    suspend fun updateDarkThemeVisibility(isDarkTheme: Boolean): Result<Unit>
    suspend fun updateLocalUserProfile(username: String, profilePictureUrl: String?): Result<Unit>
    val firebaseUser: FirebaseUser?
    suspend fun getUser(): Result<User?>
    suspend fun getUser(userId: String): Result<User?>
    suspend fun signIn(email: String, metadata: String): Result<FirebaseUser?>
    suspend fun signUp(
        email: String,
        metadata: String,
        displayName: String,
        username: String
    ): Result<FirebaseUser?>

    suspend fun createUserProfile(metadata: String, username: String): Result<Unit>

    suspend fun updateUserProfile(
        displayName: String?,
        username: String?,
        profilePictureUri: Uri?,
        profilePictureUrl: String?,
        deleteProfilePicture: Boolean = false
    ): Result<Unit>

    suspend fun updateFirebaseUserProfile(
        displayName: String?,
        profilePictureUri: Uri?,
        deleteProfilePicture: Boolean
    ): Result<Unit>

    suspend fun updateDatabaseUserProfile(
        displayName: String?,
        username: String?,
        profilePictureUrl: String?,
        deleteProfilePicture: Boolean = false
    ): Result<Unit>

    suspend fun uploadMediaToFirebase(uri: Uri): Result<String?>

    suspend fun signOut(): Result<Unit>
    suspend fun resetPassword(email: String): Result<Unit>
    suspend fun isUsernameAvailable(username: String): Result<Boolean>
    suspend fun isEmailRegistered(email: String): Result<Boolean>
    suspend fun isEmailVerified(): Result<Boolean>
    suspend fun sendEmailVerification(): Result<Unit>
    suspend fun deleteAccount(): Result<Unit>
}