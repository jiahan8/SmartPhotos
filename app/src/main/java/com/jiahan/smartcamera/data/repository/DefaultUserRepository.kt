package com.jiahan.smartcamera.data.repository

import android.net.Uri
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storage
import com.jiahan.smartcamera.domain.User
import com.jiahan.smartcamera.util.FileConstants.EXTENSION_JPG
import com.jiahan.smartcamera.util.safeCall
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class DefaultUserRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val remoteConfigRepository: RemoteConfigRepository,
) : UserRepository {

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

    private val storage: FirebaseStorage by lazy {
        Firebase.storage(remoteConfigRepository.getStorageUrl())
    }
    private val storageFolder: String by lazy {
        remoteConfigRepository.getStorageFolderName()
    }

    private val userDocumentReference: DocumentReference?
        get() = auth.uid?.let { id -> firestore.collection(COLLECTION_USER).document(id) }
    private val memberDocumentReference: DocumentReference?
        get() = auth.uid?.let { id -> firestore.collection(COLLECTION_MEMBER).document(id) }

    override suspend fun getUser(): Result<User?> = safeCall {
        val snapshot = userDocumentReference?.get()?.await()
        snapshot?.let { getUserProfile(it) }
    }

    override suspend fun getUser(userId: String): Result<User?> = safeCall {
        val snapshot = firestore.collection(COLLECTION_USER).document(userId).get().await()
        snapshot?.let { getUserProfile(it) }
    }

    override suspend fun createUserProfile(
        metadata: String,
        username: String
    ): Result<Unit> = safeCall {
        val firebaseUser = auth.currentUser ?: return@safeCall
        val userProfile = createUserProfileMap(firebaseUser, metadata, username)
        val memberProfile = createMemberProfileMap(firebaseUser, username)
        updateUserAndMemberDocuments(
            userOperation = { userDocumentReference?.set(userProfile)?.await() },
            memberOperation = { memberDocumentReference?.set(memberProfile)?.await() }
        )
    }

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

    override suspend fun uploadProfilePicture(uri: Uri): Result<String?> = safeCall {
        val mediaId = UUID.randomUUID().toString()
        val storageRef = storage.reference.child("$storageFolder/$mediaId$EXTENSION_JPG")
        storageRef.putFile(uri).await()
        storageRef.downloadUrl.await().toString()
    }

    private suspend fun updateFirebaseUserProfile(
        displayName: String?,
        profilePictureUri: Uri?,
        deleteProfilePicture: Boolean
    ) {
        auth.currentUser?.updateProfile(
            userProfileChangeRequest {
                displayName?.let { this.displayName = it }
                if (deleteProfilePicture) photoUri = null
                else profilePictureUri?.let { photoUri = it }
            }
        )?.await()
    }

    private suspend fun updateDatabaseUserProfile(
        displayName: String?,
        username: String?,
        profilePictureUrl: String?,
        deleteProfilePicture: Boolean
    ) {
        val updates = mutableMapOf<String, Any?>()
        displayName?.let { updates[FIELD_DISPLAY_NAME] = it }
        username?.let { updates[FIELD_USERNAME] = it }
        if (deleteProfilePicture) updates[FIELD_PROFILE_PICTURE] = null
        else profilePictureUrl?.let { updates[FIELD_PROFILE_PICTURE] = it }
        if (updates.isNotEmpty()) {
            updateUserAndMemberDocuments(
                userOperation = { userDocumentReference?.update(updates)?.await() },
                memberOperation = { memberDocumentReference?.update(updates)?.await() }
            )
        }
    }

    private suspend fun updateUserAndMemberDocuments(
        userOperation: suspend () -> Unit,
        memberOperation: suspend () -> Unit
    ) {
        coroutineScope {
            val userDeferred = async { safeCall { userOperation() } }
            val memberDeferred = async { safeCall { memberOperation() } }
            userDeferred.await().getOrThrow()
            memberDeferred.await().getOrThrow()
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

    private fun createMemberProfileMap(
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

    private fun getUserProfile(snapshot: DocumentSnapshot): User = User(
        email = snapshot.getString(FIELD_EMAIL) ?: "",
        metadata = snapshot.getString(FIELD_METADATA) ?: "",
        displayName = snapshot.getString(FIELD_DISPLAY_NAME) ?: "",
        username = snapshot.getString(FIELD_USERNAME) ?: "",
        profilePicture = snapshot.getString(FIELD_PROFILE_PICTURE),
        createdDate = snapshot.getDate(FIELD_CREATED)?.toInstant() ?: Instant.now(),
        documentPath = snapshot.id
    )
}