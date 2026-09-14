package com.jiahan.smartcamera.data.repository

import com.jiahan.smartcamera.domain.AppError
import com.jiahan.smartcamera.domain.MediaUri
import com.jiahan.smartcamera.domain.ProfilePictureUpdate
import com.jiahan.smartcamera.domain.User
import com.jiahan.smartcamera.util.FileConstants.EXTENSION_JPG
import com.jiahan.smartcamera.util.safeCall
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.functions.FirebaseFunctions
import dev.gitlive.firebase.functions.FunctionsExceptionCode
import dev.gitlive.firebase.messaging.FirebaseMessaging
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * [UserRepository] on GitLive's multiplatform Auth, Firestore, Functions, Messaging and Storage,
 * behind [AuthClient], [UserStore], [UserCallable], [PushClient] and [ProfilePictureStorage] so its
 * suite runs in `commonTest`.
 */
class DefaultUserRepository internal constructor(
    private val auth: AuthClient,
    private val store: UserStore,
    private val callable: UserCallable,
    private val push: PushClient,
    private val storage: ProfilePictureStorage,
    private val remoteConfigRepository: RemoteConfigRepository,
) : UserRepository {

    constructor(
        auth: FirebaseAuth,
        firestore: FirebaseFirestore,
        functions: FirebaseFunctions,
        messaging: FirebaseMessaging,
        remoteConfigRepository: RemoteConfigRepository,
    ) : this(
        GitLiveAuthClient(auth),
        GitLiveUserStore(firestore),
        GitLiveUserCallable(functions),
        GitLivePushClient(messaging),
        GitLiveProfilePictureStorage(remoteConfigRepository),
        remoteConfigRepository,
    )

    private companion object {
        const val FIELD_DISPLAY_NAME = "display_name"
        const val FIELD_PROFILE_PICTURE = "profile_picture"
        const val FIELD_FCM_TOKEN = "fcm_token"
        const val FUNCTION_CREATE_USER_PROFILE = "createUserProfile"
        const val FUNCTION_UPDATE_USERNAME = "updateUsername"
        const val REASON_USERNAME_RESERVED = "USERNAME_RESERVED"
        const val FUNCTION_RECORD_USER_ACTIVITY = "recordUserActivity"
        const val ANNOUNCEMENTS_TOPIC = "announcements"
    }

    private val storageFolder: String by lazy {
        remoteConfigRepository.getStorageFolderName()
    }

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    override suspend fun getUser(): Result<User?> = safeCall {
        currentUserId?.let { getUserProfile(it) }
    }

    override suspend fun getUser(userId: String): Result<User?> = safeCall {
        getUserProfile(userId)
    }

    // Delegates to the createUserProfile Cloud Function, which reserves the
    // username and creates the user document atomically in a transaction.
    override suspend fun createUserProfile(
        metadata: String,
        username: String
    ): Result<Unit> = safeCall {
        callReservingUsername(
            FUNCTION_CREATE_USER_PROFILE,
            UserCallArgs(metadata = metadata, username = username)
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
        val userId = currentUserId ?: throw AppError.NotAuthenticated()
        val mediaId = Uuid.random().toString()
        storage.upload("$storageFolder/$userId/$mediaId$EXTENSION_JPG", uri)
    }

    // Delegates to the updateUsername Cloud Function, which atomically
    // reserves the new username and releases the previous one.
    private suspend fun updateUsername(username: String) {
        callReservingUsername(FUNCTION_UPDATE_USERNAME, UserCallArgs(username = username))
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
     * follows, and the screen renders it through `appErrorMessageResId` (:core:common) with no code
     * at the call site.
     *
     * `INVALID_ARGUMENT` is told apart by the structured `details.reason` payload, the way
     * `foldNoteValidationError` reads createNote's. It used to map to [AppError.UsernameReserved]
     * on the bare code, which the mapper it replaced also did -- correct only while both functions
     * validated nothing but the username. `createUserProfile` validates `metadata` and the Auth
     * display name under the same code, so each of those reported itself to the user as a reserved
     * username.
     *
     * **A rejection carrying no reason at all is still folded to [AppError.UsernameReserved].**
     * `functions/` deploys separately from the app, so a build shipped ahead of that deploy talks
     * to a backend that sends no payload, and without this arm the reserved case would regress to
     * raw English until someone ran `firebase deploy`. **Delete the `reason == null` clause once
     * the functions in this change are live** -- it is a migration shim, not the contract.
     *
     * A reason this does not recognise is left alone, as in `foldNoteValidationError`: the client
     * checks length, character set and the reserved list before submitting, so the remaining
     * payloads name a request no legitimate client can produce. Such a rejection does reach the
     * user as the server's English text, which is the price of not minting an AppError case per
     * client bug -- the note validation path makes the same trade.
     */
    private suspend fun callReservingUsername(name: String, args: UserCallArgs) {
        try {
            callable.call(name, args)
        } catch (e: Exception) {
            val rejection = callable.rejectionOf(e) ?: throw e
            throw when {
                rejection.code == FunctionsExceptionCode.ALREADY_EXISTS -> AppError.UsernameTaken()
                rejection.code == FunctionsExceptionCode.INVALID_ARGUMENT &&
                        (rejection.reason == REASON_USERNAME_RESERVED || rejection.reason == null) ->
                    AppError.UsernameReserved()

                else -> e
            }
        }
    }

    override suspend fun updateFcmToken(token: String): Result<Unit> = safeCall {
        currentUserId?.let { store.updateUser(it, mapOf(FIELD_FCM_TOKEN to token)) }
    }

    // FirebaseMessaging.getToken() is deprecated in favor of register(), but register()
    // doesn't return a token at all -- it switches to an opt-in Firebase Installation ID
    // model that admin.messaging().send() (used server-side in sendPushToUser) cannot
    // target. Registration tokens remain the only mechanism our Cloud Function can send to.
    // GitLive's getToken() is the Android SDK's deprecated call underneath.
    override suspend fun registerForPushNotifications(): Result<Unit> = safeCall {
        val token = push.getToken()
        currentUserId?.let { store.updateUser(it, mapOf(FIELD_FCM_TOKEN to token)) }
        push.subscribeToTopic(ANNOUNCEMENTS_TOPIC)
    }

    override suspend fun unregisterFromPushNotifications(): Result<Unit> = safeCall {
        push.unsubscribeFromTopic(ANNOUNCEMENTS_TOPIC)
        currentUserId?.let { store.updateUser(it, mapOf(FIELD_FCM_TOKEN to null)) }
    }

    // Delegates to the recordUserActivity Cloud Function, which computes streak
    // continuation atomically in a Firestore transaction.
    override suspend fun recordUserActivity(activeDay: LocalDate): Result<Unit> = safeCall {
        callable.call(FUNCTION_RECORD_USER_ACTIVITY, UserCallArgs(activeDay = activeDay.toString()))
    }

    /**
     * GitLive's `updateProfile` writes both fields, where the Android SDK's change request set only
     * the ones given -- so a field being kept is passed its current value.
     *
     * A new picture sets the Auth photo to its device-local location, not to the uploaded URL the
     * profile document gets. That is what the Android SDK repository did, and the port keeps it.
     */
    private suspend fun updateFirebaseUserProfile(
        displayName: String?,
        profilePicture: ProfilePictureUpdate
    ) {
        val user = auth.currentUser ?: return
        user.updateProfile(
            displayName = displayName ?: user.displayName,
            photoUrl = when (profilePicture) {
                is ProfilePictureUpdate.Set -> profilePicture.uri.value
                ProfilePictureUpdate.Delete -> null
                ProfilePictureUpdate.Keep -> user.photoUrl
            },
        )
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
            currentUserId?.let { store.updateUser(it, updates) }
        }
    }

    private suspend fun getUserProfile(userId: String): User {
        val fields = store.getUser(userId)
        return User(
            userId = userId,
            email = fields.email ?: "",
            metadata = fields.metadata ?: "",
            displayName = fields.displayName ?: "",
            username = fields.username ?: "",
            profilePictureUrl = fields.profilePicture,
            createdDate = fields.created?.toInstant() ?: Clock.System.now(),
        )
    }
}