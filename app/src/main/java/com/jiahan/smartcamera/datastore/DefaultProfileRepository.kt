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
import com.jiahan.smartcamera.util.safeCall
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
    @param:ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val remoteConfigRepository: RemoteConfigRepository,
) : ProfileRepository {

    companion object {
        private const val COLLECTION_USER = "user"
        private const val COLLECTION_MEMBER = "member"
        private const val FIELD_EMAIL = "email"
        private const val FIELD_METADATA = "metadata"
        private const val FIELD_DISPLAY_NAME = "display_name"
        private const val FIELD_USERNAME = "username"
        private const val FIELD_PROFILE_PICTURE = "profile_picture"
        private const val FIELD_CREATED = "created"
        private const val FIELD_USER_ID = "user_id"
    }

    private val userDocumentReference: DocumentReference?
        get() = auth.uid?.let { id -> firestore.collection(COLLECTION_USER).document(id) }
    private val memberDocumentReference: DocumentReference?
        get() = auth.uid?.let { id -> firestore.collection(COLLECTION_MEMBER).document(id) }

    override val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences ->
            UserPreferences(
                isDarkTheme = preferences[PreferencesKeys.IS_DARK_THEME] == true,
                username = preferences[PreferencesKeys.USERNAME].toString(),
                profilePicture = preferences[PreferencesKeys.PROFILE_PICTURE]
            )
        }

    override suspend fun updateDarkThemeVisibility(isDarkTheme: Boolean): Result<Unit> = safeCall {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_DARK_THEME] = isDarkTheme
        }
    }

    override suspend fun updateLocalUserProfile(
        username: String,
        profilePictureUrl: String?
    ): Result<Unit> = safeCall {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.USERNAME] = username
            profilePictureUrl?.let { preferences[PreferencesKeys.PROFILE_PICTURE] = it }
                ?: preferences.remove(PreferencesKeys.PROFILE_PICTURE)
        }
    }

    override val firebaseUser: FirebaseUser?
        get() = auth.currentUser

    override suspend fun getUser(): Result<User?> = safeCall {
        val snapshot = userDocumentReference?.get()?.await()
        snapshot?.let { getUserProfile(it) }
    }

    override suspend fun getUser(userId: String): Result<User?> = safeCall {
        val snapshot = firestore.collection(COLLECTION_USER).document(userId).get().await()
        snapshot?.let { getUserProfile(it) }
    }

    override suspend fun signIn(email: String, metadata: String): Result<FirebaseUser?> = safeCall {
        auth.signInWithEmailAndPassword(email, metadata).await().user
    }

    override suspend fun signUp(
        email: String,
        metadata: String,
        displayName: String,
        username: String
    ): Result<FirebaseUser?> = safeCall {
        val result = auth.createUserWithEmailAndPassword(email, metadata).await()
        updateFirebaseUserProfile(
            displayName = displayName,
            profilePictureUri = null,
            deleteProfilePicture = false
        )
        result.user?.sendEmailVerification()?.await()
        createUserProfile(metadata = metadata, username = username)
        result.user
    }

    override suspend fun createUserProfile(
        metadata: String,
        username: String
    ): Result<Unit> = safeCall {
        val firebaseUser = auth.currentUser
        if (firebaseUser != null && isUsernameAvailable(username).getOrDefault(false)) {
            val userProfile = createUserProfileMap(firebaseUser, metadata, username)
            val memberProfile = createUserProfileMap(firebaseUser, username)
            updateUserAndMemberDocuments(
                userOperation = { userDocumentReference?.set(userProfile)?.await() },
                memberOperation = { memberDocumentReference?.set(memberProfile)?.await() }
            )
        }
    }

    private fun createUserProfileMap(
        firebaseUser: FirebaseUser,
        metadata: String,
        username: String
    ): Map<String, Any?> = hashMapOf(
        FIELD_EMAIL to firebaseUser.email,
        FIELD_METADATA to metadata,
        FIELD_DISPLAY_NAME to firebaseUser.displayName,
        FIELD_USERNAME to username,
        FIELD_PROFILE_PICTURE to null,
        FIELD_CREATED to FieldValue.serverTimestamp(),
        FIELD_USER_ID to firebaseUser.uid
    )

    private fun createUserProfileMap(
        firebaseUser: FirebaseUser,
        username: String
    ): Map<String, Any?> = hashMapOf(
        FIELD_EMAIL to firebaseUser.email,
        FIELD_DISPLAY_NAME to firebaseUser.displayName,
        FIELD_USERNAME to username,
        FIELD_PROFILE_PICTURE to null,
        FIELD_CREATED to FieldValue.serverTimestamp(),
        FIELD_USER_ID to firebaseUser.uid
    )

    override suspend fun updateUserProfile(
        displayName: String?,
        username: String?,
        profilePictureUri: Uri?,
        profilePictureUrl: String?,
        deleteProfilePicture: Boolean
    ): Result<Unit> = safeCall {
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

    override suspend fun updateFirebaseUserProfile(
        displayName: String?,
        profilePictureUri: Uri?,
        deleteProfilePicture: Boolean
    ): Result<Unit> = safeCall {
        auth.currentUser?.updateProfile(
            userProfileChangeRequest {
                displayName?.let { this.displayName = it }
                if (deleteProfilePicture) photoUri = null
                else profilePictureUri?.let { photoUri = it }
            }
        )?.await()
    }

    override suspend fun updateDatabaseUserProfile(
        displayName: String?,
        username: String?,
        profilePictureUrl: String?,
        deleteProfilePicture: Boolean
    ): Result<Unit> = safeCall {
        val updates =
            buildProfileUpdateMap(displayName, username, profilePictureUrl, deleteProfilePicture)
        if (updates.isNotEmpty()) {
            updateUserAndMemberDocuments(
                userOperation = { userDocumentReference?.update(updates)?.await() },
                memberOperation = { memberDocumentReference?.update(updates)?.await() }
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
        if (deleteProfilePicture) updates[FIELD_PROFILE_PICTURE] = null
        else profilePictureUrl?.let { updates[FIELD_PROFILE_PICTURE] = it }
        return updates
    }

    private suspend fun updateUserAndMemberDocuments(
        userOperation: suspend () -> Unit,
        memberOperation: suspend () -> Unit
    ) {
        coroutineScope {
            val userDeferred = async { safeCall { userOperation() } }
            val memberDeferred = async { safeCall { memberOperation() } }
            userDeferred.await()
            memberDeferred.await()
        }
    }

    override suspend fun uploadMediaToFirebase(uri: Uri): Result<String?> = safeCall {
        val storage = Firebase.storage(remoteConfigRepository.getStorageUrl())
        val storageFolder = remoteConfigRepository.getStorageFolderName()
        val mediaId = UUID.randomUUID().toString()
        val storageRef = storage.reference.child("$storageFolder/$mediaId$EXTENSION_JPG")
        storageRef.putFile(uri).await()
        storageRef.downloadUrl.await().toString()
    }

    override suspend fun signOut(): Result<Unit> = safeCall {
        auth.signOut()
    }

    override suspend fun resetPassword(email: String): Result<Unit> = safeCall {
        auth.sendPasswordResetEmail(email).await()
    }

    override suspend fun isUsernameAvailable(username: String): Result<Boolean> = safeCall {
        firestore.collection(COLLECTION_MEMBER)
            .whereEqualTo(FIELD_USERNAME, username)
            .limit(1)
            .get()
            .await()
            .isEmpty
    }

    override suspend fun isEmailRegistered(email: String): Result<Boolean> = safeCall {
        !firestore.collection(COLLECTION_MEMBER)
            .whereEqualTo(FIELD_EMAIL, email)
            .limit(1)
            .get()
            .await()
            .isEmpty
    }

    override suspend fun isEmailVerified(): Result<Boolean> = safeCall {
        auth.currentUser?.reload()?.await()
        auth.currentUser?.isEmailVerified == true
    }

    override suspend fun sendEmailVerification(): Result<Unit> = safeCall {
        auth.currentUser?.sendEmailVerification()?.await()
    }

    override suspend fun deleteAccount(): Result<Unit> = safeCall {
        auth.currentUser?.delete()?.await()
    }

    private fun getUserProfile(userDocumentSnapshot: DocumentSnapshot): User {
        return User(
            email = userDocumentSnapshot.getString(FIELD_EMAIL) ?: "",
            metadata = userDocumentSnapshot.getString(FIELD_METADATA) ?: "",
            displayName = userDocumentSnapshot.getString(FIELD_DISPLAY_NAME) ?: "",
            username = userDocumentSnapshot.getString(FIELD_USERNAME) ?: "",
            profilePicture = userDocumentSnapshot.getString(FIELD_PROFILE_PICTURE),
            createdDate = userDocumentSnapshot.getDate(FIELD_CREATED) ?: Date(),
            documentPath = userDocumentSnapshot.id
        )
    }
}