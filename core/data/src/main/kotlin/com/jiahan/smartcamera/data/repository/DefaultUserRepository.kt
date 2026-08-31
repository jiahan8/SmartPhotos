package com.jiahan.smartcamera.data.repository

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storage
import com.jiahan.smartcamera.domain.AppError
import com.jiahan.smartcamera.domain.MediaUri
import com.jiahan.smartcamera.domain.ProfilePictureUpdate
import com.jiahan.smartcamera.domain.User
import com.jiahan.smartcamera.util.FileConstants.EXTENSION_JPG
import com.jiahan.smartcamera.util.safeCall
import com.jiahan.smartcamera.util.toPlatformUri
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.LocalDate
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

class DefaultUserRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val messaging: FirebaseMessaging,
    private val remoteConfigRepository: RemoteConfigRepository,
) : UserRepository {

    companion object {
        private const val COLLECTION_USER = "user"
        private const val FIELD_EMAIL = "email"
        private const val FIELD_METADATA = "metadata"
        private const val FIELD_DISPLAY_NAME = "display_name"
        private const val FIELD_USERNAME = "username"
        private const val FIELD_PROFILE_PICTURE = "profile_picture"
        private const val FIELD_CREATED = "created"
        private const val FIELD_FCM_TOKEN = "fcm_token"
        private const val FUNCTION_CREATE_USER_PROFILE = "createUserProfile"
        private const val FUNCTION_UPDATE_USERNAME = "updateUsername"
        private const val FUNCTION_RECORD_USER_ACTIVITY = "recordUserActivity"
        private const val FIELD_ACTIVE_DAY = "activeDay"
        private const val ANNOUNCEMENTS_TOPIC = "announcements"
    }

    private val storage: FirebaseStorage by lazy {
        Firebase.storage(remoteConfigRepository.getStorageUrl())
    }
    private val storageFolder: String by lazy {
        remoteConfigRepository.getStorageFolderName()
    }

    private val userDocumentReference: DocumentReference?
        get() = auth.uid?.let { id -> firestore.collection(COLLECTION_USER).document(id) }

    override suspend fun getUser(): Result<User?> = safeCall {
        val snapshot = userDocumentReference?.get()?.await()
        snapshot?.let { getUserProfile(it) }
    }

    override suspend fun getUser(userId: String): Result<User?> = safeCall {
        val snapshot = firestore.collection(COLLECTION_USER).document(userId).get().await()
        snapshot?.let { getUserProfile(it) }
    }

    // Delegates to the createUserProfile Cloud Function, which reserves the
    // username and creates the user document atomically in a transaction.
    override suspend fun createUserProfile(
        metadata: String,
        username: String
    ): Result<Unit> = safeCall {
        callReservingUsername(
            FUNCTION_CREATE_USER_PROFILE,
            hashMapOf(FIELD_METADATA to metadata, FIELD_USERNAME to username)
        )
    }

    override suspend fun updateUserProfile(
        displayName: String?,
        username: String?,
        profilePicture: ProfilePictureUpdate
    ): Result<Unit> = safeCall {
        updateFirebaseUserProfile(
            displayName = displayName,
            profilePicture = profilePicture
        )
        username?.let { updateUsername(it) }
        updateDatabaseUserProfile(
            displayName = displayName,
            profilePicture = profilePicture
        )
    }

    override suspend fun uploadProfilePicture(uri: MediaUri): Result<String?> = safeCall {
        val userId = auth.uid ?: throw AppError.NotAuthenticated()
        val mediaId = Uuid.random().toString()
        val storageRef =
            storage.reference.child("$storageFolder/$userId/$mediaId$EXTENSION_JPG")
        storageRef.putFile(uri.toPlatformUri()).await()
        storageRef.downloadUrl.await().toString()
    }

    // Delegates to the updateUsername Cloud Function, which atomically
    // reserves the new username and releases the previous one.
    private suspend fun updateUsername(username: String) {
        callReservingUsername(FUNCTION_UPDATE_USERNAME, hashMapOf(FIELD_USERNAME to username))
    }

    /**
     * Calls one of the two username-reserving Cloud Functions, translating the conflicts they
     * report into [AppError] cases.
     *
     * Both functions raise an `HttpsError` whose message is hardcoded English on the server, so
     * something has to stop that reaching a user. That used to be `usernameErrorMessageResId` in
     * :app, tried by AuthViewModel and ProfileViewModel ahead of `ErrorHandler.getErrorMessage`.
     * Reading a Firebase error code is data-layer knowledge, and leaving it in the ViewModel layer
     * would have put `firebase-functions` on a feature module's classpath when auth was extracted.
     * Raising the app's own identity instead is the rule the rest of the data layer already
     * follows, and `appErrorMessageResId` -- applied inside `getErrorMessage` -- renders it with no
     * code at the call site.
     *
     * `INVALID_ARGUMENT` maps to [AppError.UsernameReserved] unconditionally, which is exactly what
     * the mapper it replaces did. Both functions validate nothing but the username, so the code
     * cannot presently mean anything else; if one of them grows a second argument to validate, this
     * needs the structured `details` payload that `noteErrorMessageResId` reads.
     */
    private suspend fun callReservingUsername(name: String, data: HashMap<String, String>) {
        try {
            functions.getHttpsCallable(name).call(data).await()
        } catch (e: FirebaseFunctionsException) {
            throw when (e.code) {
                FirebaseFunctionsException.Code.ALREADY_EXISTS -> AppError.UsernameTaken()
                FirebaseFunctionsException.Code.INVALID_ARGUMENT -> AppError.UsernameReserved()
                else -> e
            }
        }
    }

    override suspend fun updateFcmToken(token: String): Result<Unit> = safeCall {
        userDocumentReference?.update(FIELD_FCM_TOKEN, token)?.await()
    }

    // FirebaseMessaging.getToken() is deprecated in favor of register(), but register()
    // doesn't return a token at all -- it switches to an opt-in Firebase Installation ID
    // model that admin.messaging().send() (used server-side in sendPushToUser) cannot
    // target. Registration tokens remain the only mechanism our Cloud Function can send to.
    @Suppress("DEPRECATION")
    override suspend fun registerForPushNotifications(): Result<Unit> = safeCall {
        val token = messaging.token.await()
        userDocumentReference?.update(FIELD_FCM_TOKEN, token)?.await()
        messaging.subscribeToTopic(ANNOUNCEMENTS_TOPIC).await()
    }

    override suspend fun unregisterFromPushNotifications(): Result<Unit> = safeCall {
        messaging.unsubscribeFromTopic(ANNOUNCEMENTS_TOPIC).await()
        userDocumentReference?.update(FIELD_FCM_TOKEN, null)?.await()
    }

    // Delegates to the recordUserActivity Cloud Function, which computes streak
    // continuation atomically in a Firestore transaction.
    override suspend fun recordUserActivity(activeDay: LocalDate): Result<Unit> = safeCall {
        functions.getHttpsCallable(FUNCTION_RECORD_USER_ACTIVITY)
            .call(hashMapOf(FIELD_ACTIVE_DAY to activeDay.toString()))
            .await()
    }

    private suspend fun updateFirebaseUserProfile(
        displayName: String?,
        profilePicture: ProfilePictureUpdate
    ) {
        auth.currentUser?.updateProfile(
            userProfileChangeRequest {
                displayName?.let { this.displayName = it }
                when (profilePicture) {
                    is ProfilePictureUpdate.Set -> photoUri = profilePicture.uri.toPlatformUri()
                    ProfilePictureUpdate.Delete -> photoUri = null
                    ProfilePictureUpdate.Keep -> Unit
                }
            }
        )?.await()
    }

    private suspend fun updateDatabaseUserProfile(
        displayName: String?,
        profilePicture: ProfilePictureUpdate
    ) {
        val updates = buildMap {
            displayName?.let { put(FIELD_DISPLAY_NAME, it) }
            when (profilePicture) {
                is ProfilePictureUpdate.Set -> put(FIELD_PROFILE_PICTURE, profilePicture.url)
                ProfilePictureUpdate.Delete -> put(FIELD_PROFILE_PICTURE, null)
                ProfilePictureUpdate.Keep -> Unit
            }
        }
        if (updates.isNotEmpty()) {
            userDocumentReference?.update(updates)?.await()
        }
    }

    private fun getUserProfile(snapshot: DocumentSnapshot): User = User(
        userId = snapshot.id,
        email = snapshot.getString(FIELD_EMAIL) ?: "",
        metadata = snapshot.getString(FIELD_METADATA) ?: "",
        displayName = snapshot.getString(FIELD_DISPLAY_NAME) ?: "",
        username = snapshot.getString(FIELD_USERNAME) ?: "",
        profilePicture = snapshot.getString(FIELD_PROFILE_PICTURE),
        createdDate = snapshot.getDate(FIELD_CREATED)
            ?.let { Instant.fromEpochMilliseconds(it.time) }
            ?: Clock.System.now(),
    )
}