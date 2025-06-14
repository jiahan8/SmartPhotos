package com.jiahan.smartcamera.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.jiahan.smartcamera.domain.User
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

class DefaultProfileRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ProfileRepository {

    private val userDocumentReference: DocumentReference?
        get() = auth.uid?.let { id ->
            firestore.collection("user").document(id)
        }
    private val memberDocumentReference: DocumentReference?
        get() = auth.uid?.let { id ->
            firestore.collection("member").document(id)
        }

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
            val snapshot = userDocumentReference?.get()?.await()
            snapshot?.let {
                getUserProfile(it)
            }
        } catch (e: Exception) {
            throw Exception(e.localizedMessage)
        }
    }

    override suspend fun getUser(userId: String): User? {
        return try {
            val snapshot = firestore.collection("user").document(userId).get().await()
            snapshot?.let {
                getUserProfile(it)
            }
        } catch (e: Exception) {
            throw Exception(e.localizedMessage)
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
            updateFirebaseUserProfile(fullName = fullName)
            result.user?.sendEmailVerification()?.await()
            saveUserProfile(password = password, username = username)
            Result.success(result.user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateUserProfile(
        fullName: String?,
        username: String?,
        profilePictureUrl: String?
    ) {
        updateFirebaseUserProfile(fullName = fullName)
        updateDatabaseUserProfile(
            fullName = fullName,
            username = username,
            profilePictureUrl = null
        )
    }

    override suspend fun saveUserProfile(
        password: String,
        username: String
    ) {
        val firebaseUser = auth.currentUser
        if (firebaseUser != null && isUsernameAvailable(username)) {
            val userProfile = createUserProfileMap(firebaseUser, password, username)
            val memberProfile = createUserProfileMap(firebaseUser, username)
            coroutineScope {
                val userDeferred = async {
                    runCatching {
                        userDocumentReference?.set(userProfile)?.await()
                    }
                }
                val memberDeferred = async {
                    runCatching {
                        memberDocumentReference?.set(memberProfile)?.await()
                    }
                }
                userDeferred.await()
                memberDeferred.await()
            }
        }
    }

    private fun createUserProfileMap(
        firebaseUser: FirebaseUser,
        password: String,
        username: String
    ): Map<String, Any?> {
        return hashMapOf(
            "email" to firebaseUser.email,
            "password" to password,
            "full_name" to firebaseUser.displayName,
            "username" to username,
            "profile_picture" to null,
            "created" to FieldValue.serverTimestamp(),
            "user_id" to firebaseUser.uid
        )
    }

    private fun createUserProfileMap(
        firebaseUser: FirebaseUser,
        username: String
    ): Map<String, Any?> {
        return hashMapOf(
            "email" to firebaseUser.email,
            "full_name" to firebaseUser.displayName,
            "username" to username,
            "profile_picture" to null,
            "created" to FieldValue.serverTimestamp(),
            "user_id" to firebaseUser.uid
        )
    }

    override suspend fun updateFirebaseUserProfile(
        fullName: String?,
    ) {
        auth.currentUser?.updateProfile(
            userProfileChangeRequest {
                fullName?.let {
                    displayName = fullName
                }
            }
        )?.await()
    }

    override suspend fun updateDatabaseUserProfile(
        fullName: String?,
        username: String?,
        profilePictureUrl: String?
    ) {
        val updates = mutableMapOf<String, Any>()

        fullName?.let { updates["full_name"] = it }
        username?.let { updates["username"] = it }
        profilePictureUrl?.let { updates["profile_picture"] = it.toString() }

        if (updates.isNotEmpty()) {
            coroutineScope {
                val userDeferred = async {
                    runCatching {
                        userDocumentReference?.update(updates)?.await()
                    }
                }
                val memberDeferred = async {
                    runCatching {
                        memberDocumentReference?.update(updates)?.await()
                    }
                }
                userDeferred.await()
                memberDeferred.await()
            }
        }
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
        return firestore.collection("member")
            .whereEqualTo("username", username)
            .limit(1)
            .get()
            .await()
            .isEmpty
    }

    override suspend fun isEmailRegistered(email: String): Boolean {
        return !firestore.collection("member")
            .whereEqualTo("email", email)
            .limit(1)
            .get()
            .await()
            .isEmpty
    }

    override suspend fun isEmailVerified(): Boolean {
        return try {
            auth.currentUser?.reload()?.await()
            return auth.currentUser?.isEmailVerified == true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun sendEmailVerification() {
        auth.currentUser?.sendEmailVerification()?.await()
    }

    override suspend fun deleteAccount() {
        auth.currentUser?.delete()?.await()
    }

    private fun getUserProfile(
        userDocumentSnapshot: DocumentSnapshot
    ): User {
        return User(
            email = userDocumentSnapshot.getString("email") ?: "",
            password = userDocumentSnapshot.getString("password") ?: "",
            fullName = userDocumentSnapshot.getString("full_name") ?: "",
            username = userDocumentSnapshot.getString("username") ?: "",
            profilePicture = userDocumentSnapshot.getString("profile_picture"),
            createdDate = userDocumentSnapshot.getDate("created") ?: Date(),
            documentPath = userDocumentSnapshot.id
        )
    }
}