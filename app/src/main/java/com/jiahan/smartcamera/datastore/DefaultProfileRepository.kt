package com.jiahan.smartcamera.datastore

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.storage
import com.jiahan.smartcamera.data.repository.RemoteConfigRepository
import com.jiahan.smartcamera.domain.User
import com.jiahan.smartcamera.util.FileConstants.EXTENSION_JPG
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.io.IOException
import java.util.Date
import java.util.UUID
import javax.inject.Inject

private const val USER_PREFERENCES_NAME = "user_preferences"

private val Context.dataStore by preferencesDataStore(name = USER_PREFERENCES_NAME)

private object PreferencesKeys {
    val IS_DARK_THEME = booleanPreferencesKey(name = "is_dark_theme")
    val USERNAME = stringPreferencesKey(name = "username")
    val PROFILE_PICTURE = stringPreferencesKey(name = "profile_picture")
}

class DefaultProfileRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val remoteConfigRepository: RemoteConfigRepository,
) : ProfileRepository {

    companion object {
        // Collection names
        private const val COLLECTION_USER = "user"
        private const val COLLECTION_MEMBER = "member"

        // Field names
        private const val FIELD_EMAIL = "email"
        private const val FIELD_PASSWORD = "password"
        private const val FIELD_DISPLAY_NAME = "display_name"
        private const val FIELD_USERNAME = "username"
        private const val FIELD_PROFILE_PICTURE = "profile_picture"
        private const val FIELD_CREATED = "created"
        private const val FIELD_USER_ID = "user_id"
    }

    private val userDocumentReference: DocumentReference?
        get() = auth.uid?.let { id ->
            firestore.collection(COLLECTION_USER).document(id)
        }
    private val memberDocumentReference: DocumentReference?
        get() = auth.uid?.let { id ->
            firestore.collection(COLLECTION_MEMBER).document(id)
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
            val username = preferences[PreferencesKeys.USERNAME].toString()
            val profilePicture = preferences[PreferencesKeys.PROFILE_PICTURE]
            UserPreferences(
                isDarkTheme = isDarkTheme,
                username = username,
                profilePicture = profilePicture
            )
        }

    override suspend fun updateDarkThemeVisibility(isDarkTheme: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_DARK_THEME] = isDarkTheme
        }
    }

    override suspend fun updateLocalUserProfile(username: String, profilePictureUrl: String?) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.USERNAME] = username
            profilePictureUrl?.let {
                preferences[PreferencesKeys.PROFILE_PICTURE] = profilePictureUrl
            } ?: run {
                preferences.remove(PreferencesKeys.PROFILE_PICTURE)
            }
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
            val snapshot = firestore.collection(COLLECTION_USER).document(userId).get().await()
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
        displayName: String,
        username: String
    ): Result<FirebaseUser?> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            updateFirebaseUserProfile(
                displayName = displayName,
                profilePictureUri = null,
                deleteProfilePicture = false
            )
            result.user?.sendEmailVerification()?.await()
            createUserProfile(password = password, username = username)
            Result.success(result.user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateUserProfile(
        displayName: String?,
        username: String?,
        profilePictureUri: Uri?,
        profilePictureUrl: String?,
        deleteProfilePicture: Boolean
    ) {
        updateFirebaseUserProfile(
            displayName = displayName,
            profilePictureUri = profilePictureUri,
            deleteProfilePicture = deleteProfilePicture
        )
        updateDatabaseUserProfile(
            displayName = displayName,
            username = username,
            profilePictureUrl = profilePictureUrl,
            deleteProfilePicture = deleteProfilePicture
        )
    }

    override suspend fun createUserProfile(
        password: String,
        username: String
    ) {
        val firebaseUser = auth.currentUser
        if (firebaseUser != null && isUsernameAvailable(username)) {
            val userProfile = createUserProfileMap(firebaseUser, password, username)
            val memberProfile = createUserProfileMap(firebaseUser, username)
            updateUserAndMemberDocuments(
                userOperation = {
                    userDocumentReference?.set(userProfile)?.await()
                    Unit
                },
                memberOperation = {
                    memberDocumentReference?.set(memberProfile)?.await()
                    Unit
                }
            )
        }
    }

    private fun createUserProfileMap(
        firebaseUser: FirebaseUser,
        password: String,
        username: String
    ): Map<String, Any?> {
        return hashMapOf(
            FIELD_EMAIL to firebaseUser.email,
            FIELD_PASSWORD to password,
            FIELD_DISPLAY_NAME to firebaseUser.displayName,
            FIELD_USERNAME to username,
            FIELD_PROFILE_PICTURE to null,
            FIELD_CREATED to FieldValue.serverTimestamp(),
            FIELD_USER_ID to firebaseUser.uid
        )
    }

    private fun createUserProfileMap(
        firebaseUser: FirebaseUser,
        username: String
    ): Map<String, Any?> {
        return hashMapOf(
            FIELD_EMAIL to firebaseUser.email,
            FIELD_DISPLAY_NAME to firebaseUser.displayName,
            FIELD_USERNAME to username,
            FIELD_PROFILE_PICTURE to null,
            FIELD_CREATED to FieldValue.serverTimestamp(),
            FIELD_USER_ID to firebaseUser.uid
        )
    }

    override suspend fun updateFirebaseUserProfile(
        displayName: String?,
        profilePictureUri: Uri?,
        deleteProfilePicture: Boolean
    ) {
        auth.currentUser?.updateProfile(
            userProfileChangeRequest {
                displayName?.let {
                    this.displayName = displayName
                }
                if (deleteProfilePicture) {
                    photoUri = null
                } else {
                    profilePictureUri?.let {
                        photoUri = profilePictureUri
                    }
                }
            }
        )?.await()
    }

    override suspend fun updateDatabaseUserProfile(
        displayName: String?,
        username: String?,
        profilePictureUrl: String?,
        deleteProfilePicture: Boolean
    ) {
        val updates = buildProfileUpdateMap(
            displayName = displayName,
            username = username,
            profilePictureUrl = profilePictureUrl,
            deleteProfilePicture = deleteProfilePicture
        )

        if (updates.isNotEmpty()) {
            updateUserAndMemberDocuments(
                userOperation = {
                    userDocumentReference?.update(updates)?.await()
                    Unit
                },
                memberOperation = {
                    memberDocumentReference?.update(updates)?.await()
                    Unit
                }
            )
        }
    }

    private fun buildProfileUpdateMap(
        displayName: String?,
        username: String?,
        profilePictureUrl: String?,
        deleteProfilePicture: Boolean
    ): Map<String, Any?> {
        val updates = mutableMapOf<String, Any?>()
        displayName?.let { updates[FIELD_DISPLAY_NAME] = it }
        username?.let { updates[FIELD_USERNAME] = it }
        if (deleteProfilePicture) {
            updates[FIELD_PROFILE_PICTURE] = null
        } else {
            profilePictureUrl?.let { updates[FIELD_PROFILE_PICTURE] = it }
        }
        return updates
    }

    private suspend fun updateUserAndMemberDocuments(
        userOperation: suspend () -> Unit?,
        memberOperation: suspend () -> Unit?
    ) {
        coroutineScope {
            val userDeferred = async {
                runCatching { userOperation() }
            }
            val memberDeferred = async {
                runCatching { memberOperation() }
            }
            userDeferred.await()
            memberDeferred.await()
        }
    }

    override suspend fun uploadMediaToFirebase(uri: Uri): String? {
        val storage = Firebase.storage(remoteConfigRepository.getStorageUrl())
        val storageFolder = remoteConfigRepository.getStorageFolderName()
        return coroutineScope {
            try {
                val mediaId = UUID.randomUUID().toString()
                val extension = EXTENSION_JPG
                val storageRef =
                    storage.reference.child("$storageFolder/$mediaId$extension")

                storageRef.putFile(uri).await()
                val mediaUrl = storageRef.downloadUrl.await().toString()
                mediaUrl
            } catch (e: Exception) {
                e.printStackTrace()
                null
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
        return firestore.collection(COLLECTION_MEMBER)
            .whereEqualTo(FIELD_USERNAME, username)
            .limit(1)
            .get()
            .await()
            .isEmpty
    }

    override suspend fun isEmailRegistered(email: String): Boolean {
        return !firestore.collection(COLLECTION_MEMBER)
            .whereEqualTo(FIELD_EMAIL, email)
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
            email = userDocumentSnapshot.getString(FIELD_EMAIL) ?: "",
            password = userDocumentSnapshot.getString(FIELD_PASSWORD) ?: "",
            displayName = userDocumentSnapshot.getString(FIELD_DISPLAY_NAME) ?: "",
            username = userDocumentSnapshot.getString(FIELD_USERNAME) ?: "",
            profilePicture = userDocumentSnapshot.getString(FIELD_PROFILE_PICTURE),
            createdDate = userDocumentSnapshot.getDate(FIELD_CREATED) ?: Date(),
            documentPath = userDocumentSnapshot.id
        )
    }
}