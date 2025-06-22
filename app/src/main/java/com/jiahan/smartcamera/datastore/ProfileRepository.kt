package com.jiahan.smartcamera.datastore

import android.net.Uri
import com.google.firebase.auth.FirebaseUser
import com.jiahan.smartcamera.domain.User
import kotlinx.coroutines.flow.Flow
import kotlin.Boolean

interface ProfileRepository {
    val userPreferencesFlow: Flow<UserPreferences>
    suspend fun updateDarkThemeVisibility(isDarkTheme: Boolean)
    suspend fun updateUsername(username: String)
    val firebaseUser: FirebaseUser?
    suspend fun getUser(): User?
    suspend fun getUser(userId: String): User?
    suspend fun signIn(email: String, password: String): Result<FirebaseUser?>
    suspend fun signUp(
        email: String,
        password: String,
        displayName: String,
        username: String
    ): Result<FirebaseUser?>

    suspend fun createUserProfile(password: String, username: String)

    suspend fun updateUserProfile(
        displayName: String?,
        username: String?,
        profilePictureUri: Uri?,
        profilePictureUrl: String?,
        deleteProfilePicture: Boolean = false
    )

    suspend fun updateFirebaseUserProfile(
        displayName: String?,
        profilePictureUri: Uri?,
        deleteProfilePicture: Boolean
    )

    suspend fun updateDatabaseUserProfile(
        displayName: String?,
        username: String?,
        profilePictureUrl: String?,
        deleteProfilePicture: Boolean = false
    )

    suspend fun uploadMediaToFirebase(uri: Uri): String?

    suspend fun signOut()
    suspend fun resetPassword(email: String): Result<Unit>
    suspend fun isUsernameAvailable(username: String): Boolean
    suspend fun isEmailRegistered(email: String): Boolean
    suspend fun isEmailVerified(): Boolean
    suspend fun sendEmailVerification()
    suspend fun deleteAccount()
}