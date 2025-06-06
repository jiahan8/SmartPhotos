package com.jiahan.smartcamera.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import com.jiahan.smartcamera.domain.User
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.io.IOException
import java.util.Date
import javax.inject.Inject

private const val USER_PREFERENCES_NAME = "user_preferences"

private val Context.dataStore by preferencesDataStore(name = USER_PREFERENCES_NAME)

private object PreferencesKeys {
    val IS_DARK_THEME = booleanPreferencesKey(name = "is_dark_theme")
}

class ProfileRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : UserDataRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = Firebase.firestore

    override val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data
        .catch { exception ->
            // dataStore.data throws an IOException when an error is encountered when reading data
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }.map { preferences ->
            // Get our dark theme value, defaulting to false if not set:
            val isDarkTheme = preferences[PreferencesKeys.IS_DARK_THEME] == true
            UserPreferences(isDarkTheme)
        }

    override suspend fun updateIsDarkTheme(isDarkTheme: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_DARK_THEME] = isDarkTheme
        }
    }

    override val firebaseUser: FirebaseUser?
        get() = auth.currentUser

    override suspend fun getUser(): User? {
        return try {
            val snapshot = firestore.collection("user").get().await()
            val document = snapshot.documents.find {
                it.get("user_id") == auth.currentUser?.uid
            }
            document?.let {
                return User(
                    email = it.get("email")?.toString() ?: "",
                    password = it.get("password")?.toString() ?: "",
                    fullName = it.get("full_name")?.toString() ?: "",
                    username = it.get("username")?.toString() ?: "",
                    profilePicture = it.get("profile_picture")?.toString(),
                    createdDate = it.getDate("created") ?: Date(),
                    documentPath = it.id
                )
            }
        } catch (e: Exception) {
            throw Exception("Failed to fetch user: ${e.message}", e)
        }
    }

    override suspend fun signIn(email: String, password: String): Result<FirebaseUser?> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            Result.success(result.user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signUp(
        email: String,
        password: String,
        fullName: String,
        username: String
    ): Result<FirebaseUser?> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            updateUserProfile(fullName = fullName)
            result.user?.sendEmailVerification()?.await()
            saveUserProfile(password = password, username = username)
            Result.success(result.user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun saveUserProfile(
        password: String,
        username: String
    ) {
        try {
            val firebaseUser = auth.currentUser
            if (firebaseUser != null && isUsernameAvailable(username)) {
                firestore.collection("user")
                    .add(
                        hashMapOf(
                            "email" to firebaseUser.email,
                            "password" to password,
                            "full_name" to firebaseUser.displayName,
                            "username" to username,
                            "profile_picture" to null,
                            "created" to FieldValue.serverTimestamp(),
                            "user_id" to firebaseUser.uid
                        )
                    ).await()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun updateUserProfile(fullName: String) {
        auth.currentUser?.updateProfile(
            userProfileChangeRequest {
                displayName = fullName
            }
        )?.await()
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    override suspend fun resetPassword(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun isUsernameAvailable(username: String): Boolean {
        return firestore.collection("user")
            .whereEqualTo("username", username)
            .limit(1)
            .get()
            .await()
            .isEmpty
    }

    override suspend fun isEmailRegistered(email: String): Boolean {
        return !firestore.collection("user")
            .whereEqualTo("email", email)
            .limit(1)
            .get()
            .await()
            .isEmpty
    }

    override fun isEmailVerified(): Boolean {
        auth.currentUser?.reload()
        return auth.currentUser?.isEmailVerified == true
    }

    override suspend fun sendEmailVerification() {
        auth.currentUser?.sendEmailVerification()?.await()
    }

    override suspend fun deleteAccount() {
        try {
            auth.currentUser?.delete()?.await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}