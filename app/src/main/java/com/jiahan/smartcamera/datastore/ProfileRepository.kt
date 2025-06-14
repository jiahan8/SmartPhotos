package com.jiahan.smartcamera.datastore

import com.google.firebase.auth.FirebaseUser
import com.jiahan.smartcamera.domain.User
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    val userPreferencesFlow: Flow<UserPreferences>
    suspend fun updateIsDarkTheme(isDarkTheme: Boolean)
    val firebaseUser: FirebaseUser?
    suspend fun getUser(): User?
    suspend fun getUser(userId: String): User?
    suspend fun signIn(email: String, password: String): Result<FirebaseUser?>
    suspend fun signUp(
        email: String,
        password: String,
        fullName: String,
        username: String
    ): Result<FirebaseUser?>

    suspend fun updateUserProfile(
        fullName: String?,
        username: String?,
        profilePictureUrl: String?
    )

    suspend fun saveUserProfile(password: String, username: String)
    suspend fun updateFirebaseUserProfile(fullName: String?)
    suspend fun updateDatabaseUserProfile(
        fullName: String?,
        username: String?,
        profilePictureUrl: String?
    )

    suspend fun signOut()
    suspend fun resetPassword(email: String): Result<Unit>
    suspend fun isUsernameAvailable(username: String): Boolean
    suspend fun isEmailRegistered(email: String): Boolean
    suspend fun isEmailVerified(): Boolean
    suspend fun sendEmailVerification()
    suspend fun deleteAccount()
}