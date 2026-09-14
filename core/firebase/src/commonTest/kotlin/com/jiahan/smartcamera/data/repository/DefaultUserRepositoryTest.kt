package com.jiahan.smartcamera.data.repository

import com.jiahan.smartcamera.domain.AppError
import com.jiahan.smartcamera.domain.MediaUri
import com.jiahan.smartcamera.domain.ProfilePictureUpdate
import com.jiahan.smartcamera.fake.FakeRemoteConfigRepository
import dev.gitlive.firebase.functions.FunctionsExceptionCode
import dev.gitlive.firebase.internal.decode
import dev.gitlive.firebase.internal.encode
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers [DefaultUserRepository]: the username rejections it folds into [AppError]s, and what it
 * sends each Firebase service.
 *
 * The fold tests assert the [AppError] identity, which is this layer's whole contract for a failure
 * it raises itself: the string lookup happens in :core:common's `appErrorMessageResId`, pinned there
 * by `ErrorMessagesTest`.
 *
 * Firebase sits behind five seams. A rejection is a [FakeRejection] whose code and reason the fake
 * callable reports, because GitLive's `FirebaseFunctionsException` cannot be built in a test. User
 * documents are raw field maps run through GitLive's `decode`, and callable arguments are checked
 * through its `encode`, as in `DefaultNoteRepositoryTest`.
 */
class DefaultUserRepositoryTest {

    private companion object {
        const val USER_ID = "user-1"
        const val UNUSED = "not called by DefaultUserRepository"
    }

    private class FakeAuthUser : AuthUser {
        override val uid: String = USER_ID
        override val email: String? = "alice@example.com"
        override val isEmailVerified: Boolean = true
        override val displayName: String? = "Alice"
        override val photoUrl: String? = "content://avatar/current"

        val profileUpdates = mutableListOf<Pair<String?, String?>>()

        override suspend fun updateProfile(displayName: String?, photoUrl: String?) {
            profileUpdates += displayName to photoUrl
        }

        override suspend fun updateDisplayName(displayName: String) = error(UNUSED)

        override suspend fun sendEmailVerification() = error(UNUSED)

        override suspend fun delete() = error(UNUSED)

        override suspend fun reload() = error(UNUSED)

        override suspend fun reauthenticate(email: String, password: String) = error(UNUSED)

        override suspend fun updatePassword(password: String) = error(UNUSED)
    }

    private class FakeAuthClient : AuthClient {
        override var currentUser: AuthUser? = null

        override suspend fun signIn(email: String, password: String) = error(UNUSED)

        override suspend fun createUser(email: String, password: String) = error(UNUSED)

        override suspend fun signOut() = error(UNUSED)

        override suspend fun sendPasswordResetEmail(email: String) = error(UNUSED)
    }

    private class FakeUserStore : UserStore {
        /** `user` documents by id, as raw field maps. */
        val documents = mutableMapOf<String, Map<String, Any?>>()
        val reads = mutableListOf<String>()
        val updates = mutableListOf<Pair<String, Map<String, Any?>>>()

        override suspend fun getUser(userId: String): FirestoreUser {
            reads += userId
            return decode(FirestoreUser.serializer(), documents[userId].orEmpty())
        }

        override suspend fun updateUser(userId: String, fields: Map<String, Any?>) {
            updates += userId to fields
        }
    }

    /** A callable rejection carrying the code and reason a Cloud Function attached. */
    private class FakeRejection(
        val code: FunctionsExceptionCode,
        val reason: String? = null,
    ) : Exception("rejected")

    private class FakeUserCallable : UserCallable {
        val calls = mutableListOf<Pair<String, UserCallArgs>>()
        var failure: Throwable? = null

        override suspend fun call(name: String, args: UserCallArgs) {
            calls += name to args
            failure?.let { throw it }
        }

        override fun rejectionOf(error: Throwable): CallableRejection? =
            (error as? FakeRejection)?.let { CallableRejection(it.code, it.reason) }
    }

    private class FakePushClient : PushClient {
        val calls = mutableListOf<String>()
        var subscribeFailure: Throwable? = null

        override suspend fun getToken(): String {
            calls += "getToken"
            return "fcm-token"
        }

        override suspend fun subscribeToTopic(topic: String) {
            calls += "subscribe:$topic"
            subscribeFailure?.let { throw it }
        }

        override suspend fun unsubscribeFromTopic(topic: String) {
            calls += "unsubscribe:$topic"
        }
    }

    private class FakeProfilePictureStorage : ProfilePictureStorage {
        val uploads = mutableListOf<Pair<String, MediaUri>>()

        override suspend fun upload(path: String, file: MediaUri): String {
            uploads += path to file
            return "https://storage.example/$path"
        }
    }

    private val authUser = FakeAuthUser()
    private val auth = FakeAuthClient().apply { currentUser = authUser }
    private val store = FakeUserStore()
    private val callable = FakeUserCallable()
    private val push = FakePushClient()
    private val storage = FakeProfilePictureStorage()
    private val remoteConfigRepository = FakeRemoteConfigRepository().apply {
        storageFolder = "profile"
    }

    private val repository = DefaultUserRepository(
        auth = auth,
        store = store,
        callable = callable,
        push = push,
        storage = storage,
        remoteConfigRepository = remoteConfigRepository,
    )

    // -------------------------------------------------------------------------
    // uploadProfilePicture
    // -------------------------------------------------------------------------

    @Test
    fun `uploadProfilePicture signed out fails as NotAuthenticated`() = runTest {
        auth.currentUser = null

        val result = repository.uploadProfilePicture(MediaUri("file:///tmp/avatar.jpg"))

        assertIs<AppError.NotAuthenticated>(result.exceptionOrNull())
        assertTrue(storage.uploads.isEmpty())
    }

    @Test
    fun `uploadProfilePicture stores under the folder and account and returns the download URL`() =
        runTest {
            val file = MediaUri("file:///tmp/avatar.jpg")

            val url = repository.uploadProfilePicture(file).getOrThrow()

            val (path, uploaded) = storage.uploads.single()
            assertEquals(file, uploaded)
            assertTrue(path.startsWith("profile/$USER_ID/"), path)
            assertTrue(path.endsWith(".jpg"), path)
            assertEquals("https://storage.example/$path", url)
        }

    // -------------------------------------------------------------------------
    // The username rejections
    //
    // The createUserProfile/updateUsername Cloud Functions report a conflict as an HttpsError whose
    // text is hardcoded English on the server. These pin the fold to the app's own vocabulary,
    // which is what keeps that text off the screen -- and what let :feature:auth be extracted
    // without firebase-functions on its classpath, since AuthViewModel no longer inspects the code
    // itself.
    //
    // The reserved case is keyed on the `details.reason` payload rather than the INVALID_ARGUMENT
    // code, and the fourth test is why: createUserProfile raises that code for `metadata` and the
    // Auth display name as well, and reading the code alone showed those to the user as a reserved
    // username.
    // -------------------------------------------------------------------------

    @Test
    fun `createUserProfile ALREADY_EXISTS fails as UsernameTaken`() = runTest {
        callable.failure = FakeRejection(FunctionsExceptionCode.ALREADY_EXISTS)

        val result = repository.createUserProfile(metadata = "secret", username = "taken")

        assertIs<AppError.UsernameTaken>(result.exceptionOrNull())
    }

    @Test
    fun `createUserProfile USERNAME_RESERVED fails as UsernameReserved`() = runTest {
        callable.failure =
            FakeRejection(FunctionsExceptionCode.INVALID_ARGUMENT, reason = "USERNAME_RESERVED")

        val result = repository.createUserProfile(metadata = "secret", username = "admin")

        assertIs<AppError.UsernameReserved>(result.exceptionOrNull())
    }

    /**
     * The compatibility arm, not the contract: `functions/` deploys separately, so a build that
     * ships ahead of that deploy meets a backend sending no payload. Delete this with the arm once
     * the functions are live.
     */
    @Test
    fun `createUserProfile INVALID_ARGUMENT with no reason still fails as UsernameReserved`() =
        runTest {
            callable.failure = FakeRejection(FunctionsExceptionCode.INVALID_ARGUMENT)

            val result = repository.createUserProfile(metadata = "secret", username = "admin")

            assertIs<AppError.UsernameReserved>(result.exceptionOrNull())
        }

    @Test
    fun `createUserProfile INVALID_ARGUMENT for another reason surfaces unchanged`() = runTest {
        callable.failure =
            FakeRejection(FunctionsExceptionCode.INVALID_ARGUMENT, reason = "DISPLAY_NAME_TOO_LONG")

        val result = repository.createUserProfile(metadata = "secret", username = "someone")

        assertIs<FakeRejection>(result.exceptionOrNull())
    }

    @Test
    fun `createUserProfile other codes surface unchanged`() = runTest {
        callable.failure = FakeRejection(FunctionsExceptionCode.UNAVAILABLE)

        val result = repository.createUserProfile(metadata = "secret", username = "someone")

        assertIs<FakeRejection>(result.exceptionOrNull())
    }

    @Test
    fun `updateUserProfile folds a taken username before writing the profile document`() = runTest {
        callable.failure = FakeRejection(FunctionsExceptionCode.ALREADY_EXISTS)

        val result = repository.updateUserProfile(
            displayName = "Alice B",
            username = "taken",
            profilePicture = ProfilePictureUpdate.Keep,
        )

        assertIs<AppError.UsernameTaken>(result.exceptionOrNull())
        assertEquals("updateUsername", callable.calls.single().first)
        // The username is reserved before the document is written, so a rejected one leaves the
        // document as it was.
        assertTrue(store.updates.isEmpty())
    }

    // -------------------------------------------------------------------------
    // The profile
    // -------------------------------------------------------------------------

    @Test
    fun `getUser reads the signed-in account document`() = runTest {
        store.documents[USER_ID] = mapOf(
            "email" to "alice@example.com",
            "metadata" to "meta",
            "display_name" to "Alice",
            "username" to "alice",
            "profile_picture" to "https://pic",
        )

        val user = assertNotNull(repository.getUser().getOrThrow())

        assertEquals(USER_ID, user.userId)
        assertEquals("alice@example.com", user.email)
        assertEquals("meta", user.metadata)
        assertEquals("Alice", user.displayName)
        assertEquals("alice", user.username)
        assertEquals("https://pic", user.profilePictureUrl)
    }

    @Test
    fun `getUser signed out is null without reading Firestore`() = runTest {
        auth.currentUser = null

        assertNull(repository.getUser().getOrThrow())
        assertTrue(store.reads.isEmpty())
    }

    @Test
    fun `updateUserProfile passes Auth the current value of each field it keeps`() = runTest {
        repository.updateUserProfile(
            displayName = null,
            username = null,
            profilePicture = ProfilePictureUpdate.Keep,
        ).getOrThrow()

        // GitLive's updateProfile writes both fields, where the Android SDK's request set only the
        // ones given -- so keeping a field means sending what it already is.
        assertEquals(
            listOf<Pair<String?, String?>>("Alice" to "content://avatar/current"),
            authUser.profileUpdates,
        )
        assertTrue(callable.calls.isEmpty())
        assertTrue(store.updates.isEmpty())
    }

    @Test
    fun `updateUserProfile Delete clears the picture in Auth and in the profile document`() =
        runTest {
            repository.updateUserProfile(
                displayName = "Alice B",
                username = null,
                profilePicture = ProfilePictureUpdate.Delete,
            ).getOrThrow()

            assertEquals(listOf<Pair<String?, String?>>("Alice B" to null), authUser.profileUpdates)
            assertEquals<List<Pair<String, Map<String, Any?>>>>(
                listOf(USER_ID to mapOf("display_name" to "Alice B", "profile_picture" to null)),
                store.updates,
            )
        }

    // -------------------------------------------------------------------------
    // Push notifications
    // -------------------------------------------------------------------------

    @Test
    fun `registerForPushNotifications stores the token and subscribes to announcements`() =
        runTest {
            repository.registerForPushNotifications().getOrThrow()

            assertEquals(listOf("getToken", "subscribe:announcements"), push.calls)
            assertEquals<List<Pair<String, Map<String, Any?>>>>(
                listOf(USER_ID to mapOf("fcm_token" to "fcm-token")),
                store.updates,
            )
        }

    @Test
    fun `registerForPushNotifications fails when the topic subscription fails`() = runTest {
        push.subscribeFailure = IllegalStateException("subscription failed")

        val result = repository.registerForPushNotifications()

        // The contract the topic actuals keep. GitLive's own subscribeToTopic returns before the
        // call lands, which would turn this failure into a success.
        assertTrue(result.isFailure)
    }

    @Test
    fun `unregisterFromPushNotifications unsubscribes and clears the token`() = runTest {
        repository.unregisterFromPushNotifications().getOrThrow()

        assertEquals(listOf("unsubscribe:announcements"), push.calls)
        assertEquals<List<Pair<String, Map<String, Any?>>>>(
            listOf(USER_ID to mapOf("fcm_token" to null)),
            store.updates,
        )
    }

    // -------------------------------------------------------------------------
    // What crosses the wire
    // -------------------------------------------------------------------------

    @Test
    fun `each user callable sends only the keys it sets`() = runTest {
        repository.createUserProfile(metadata = "meta", username = "alice")
        repository.updateUserProfile(
            displayName = null,
            username = "alice2",
            profilePicture = ProfilePictureUpdate.Keep,
        )
        repository.recordUserActivity(LocalDate(2026, 9, 14))

        // The keys are the contract with functions/index.js, which reads each by name.
        assertEquals<List<Pair<String, Any?>>>(
            listOf(
                "createUserProfile" to mapOf("metadata" to "meta", "username" to "alice"),
                "updateUsername" to mapOf("username" to "alice2"),
                "recordUserActivity" to mapOf("activeDay" to "2026-09-14"),
            ),
            callable.calls.map { (name, args) ->
                name to encode(UserCallArgs.serializer(), args) { encodeDefaults = false }
            },
        )
    }
}