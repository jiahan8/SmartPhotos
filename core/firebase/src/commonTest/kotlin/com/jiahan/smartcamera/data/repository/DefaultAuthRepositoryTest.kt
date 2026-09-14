package com.jiahan.smartcamera.data.repository

import com.jiahan.smartcamera.domain.AppError
import com.jiahan.smartcamera.fake.FakeLocalUserDataCleaner
import com.jiahan.smartcamera.fake.FakeUserRepository
import com.jiahan.smartcamera.util.ErrorHandler
import dev.gitlive.firebase.internal.decode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.nullable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the [DefaultAuthRepository] behaviour that is not a straight pass-through to Firebase.
 *
 * Most of this class forwards a call and wraps it in `safeCall`, which is worth no test of its own.
 * Three things here are not that, and they are what this file pins:
 *
 * - **The signup rollback.** `signUp` creates an Auth account before it creates the profile
 *   document, so a failure of the second leaves an account with no profile -- an address the user
 *   can neither sign into usefully nor re-register with. The repository deletes it; nothing else in
 *   the app would notice if that stopped happening.
 * - **The callable payload readers.** `isUsernameAvailable`/`isEmailRegistered` fall back to
 *   `false`. That fallback is a decision, not a formality: it makes an unreachable or reshaped
 *   backend read as "taken"/"not registered", which blocks signup rather than letting it through.
 *   The fake runs each raw payload through GitLive's own `decode`, so the real decoder is under test.
 * - **The local wipe on the way out.** `signOut` and `deleteAccount` clear the local user data, so
 *   the next account does not open onto the previous one's notes.
 */
class DefaultAuthRepositoryTest {

    private class FakeAuthUser(
        override val uid: String = "uid-1",
        override val email: String? = "user@example.com",
    ) : AuthUser {
        override var isEmailVerified: Boolean = false
        override val displayName: String? = null
        override val photoUrl: String? = null

        /** What [reload] sets the verified flag to, as a reload fetches the server's value. */
        var verifiedAfterReload: Boolean? = null
        val calls = mutableListOf<String>()

        override suspend fun updateDisplayName(displayName: String) {
            calls += "updateDisplayName:$displayName"
        }

        override suspend fun updateProfile(displayName: String?, photoUrl: String?) {
            calls += "updateProfile:$displayName:$photoUrl"
        }

        override suspend fun sendEmailVerification() {
            calls += "sendEmailVerification"
        }

        override suspend fun delete() {
            calls += "delete"
        }

        override suspend fun reload() {
            calls += "reload"
            verifiedAfterReload?.let { isEmailVerified = it }
        }

        override suspend fun reauthenticate(email: String, password: String) {
            calls += "reauthenticate:$email:$password"
        }

        override suspend fun updatePassword(password: String) {
            calls += "updatePassword:$password"
        }
    }

    private class FakeAuthClient : AuthClient {
        override var currentUser: AuthUser? = null
        val calls = mutableListOf<String>()

        override suspend fun signIn(email: String, password: String) {
            calls += "signIn"
        }

        override suspend fun createUser(email: String, password: String) {
            calls += "createUser"
        }

        override suspend fun signOut() {
            calls += "signOut"
        }

        override suspend fun sendPasswordResetEmail(email: String) {
            calls += "sendPasswordResetEmail"
        }
    }

    private class FakeAuthCallable : AuthCallable {
        val names = mutableListOf<String>()
        var payload: Any? = null
        var failure: Throwable? = null

        override suspend fun call(name: String, args: AuthCheckArgs): AuthCheckResult? {
            names += name
            failure?.let { throw it }
            return decode(AuthCheckResult.serializer().nullable, payload)
        }
    }

    private class RecordingErrorHandler : ErrorHandler {
        val logged = mutableListOf<Throwable>()

        override fun logError(throwable: Throwable, tag: String) {
            logged += throwable
        }
    }

    private val auth = FakeAuthClient()
    private val callable = FakeAuthCallable()
    private val userRepository = FakeUserRepository()
    private val localUserDataCleaner = FakeLocalUserDataCleaner()
    private val errorHandler = RecordingErrorHandler()

    private val repository = DefaultAuthRepository(
        auth = auth,
        callable = callable,
        userRepository = userRepository,
        localUserDataCleaner = localUserDataCleaner,
        errorHandler = errorHandler,
    )

    private fun signedInUser(): FakeAuthUser = FakeAuthUser().also { auth.currentUser = it }

    // -------------------------------------------------------------------------
    // Signup rollback
    // -------------------------------------------------------------------------

    @Test
    fun `signUp deletes the auth account when profile creation fails`() = runTest {
        val user = signedInUser()
        userRepository.createUserProfileResult = Result.failure(IllegalStateException("username taken"))

        val result = repository.signUp("a@b.com", "pw", "Display", "username")

        assertTrue(result.isFailure)
        assertTrue("delete" in user.calls)
    }

    @Test
    fun `signUp keeps the auth account when profile creation succeeds`() = runTest {
        val user = signedInUser()

        val result = repository.signUp("a@b.com", "pw", "Display", "username")

        assertTrue(result.isSuccess)
        assertFalse("delete" in user.calls)
    }

    /** The display name reaches Auth, not just the profile document the Cloud Function writes. */
    @Test
    fun `signUp sends a verification email and sets the display name`() = runTest {
        val user = signedInUser()

        repository.signUp("a@b.com", "pw", "Display", "username")

        assertTrue("updateDisplayName:Display" in user.calls)
        assertTrue("sendEmailVerification" in user.calls)
    }

    // -------------------------------------------------------------------------
    // Callable payload readers
    // -------------------------------------------------------------------------

    @Test
    fun `isUsernameAvailable reports what the callable returned`() = runTest {
        callable.payload = mapOf("available" to true)

        assertEquals(true, repository.isUsernameAvailable("free").getOrNull())
    }

    @Test
    fun `isUsernameAvailable reports a taken username`() = runTest {
        callable.payload = mapOf("available" to false)

        assertEquals(false, repository.isUsernameAvailable("taken").getOrNull())
    }

    /*
     * The three shapes a backend that is absent, older or reshaped can produce. Each has to read as
     * "not available" rather than as an availability: signup is gated on this, so failing open
     * would let a user through to a createUserProfile that then rejects them.
     */

    @Test
    fun `isUsernameAvailable is false when the payload has no available key`() = runTest {
        callable.payload = mapOf("other" to true)

        assertEquals(false, repository.isUsernameAvailable("who").getOrNull())
    }

    @Test
    fun `isUsernameAvailable is false when the payload is not a map`() = runTest {
        callable.payload = "available"

        assertEquals(false, repository.isUsernameAvailable("who").getOrNull())
    }

    @Test
    fun `isUsernameAvailable is false when the payload is null`() = runTest {
        callable.payload = null

        assertEquals(false, repository.isUsernameAvailable("who").getOrNull())
    }

    @Test
    fun `isUsernameAvailable fails when the callable fails`() = runTest {
        callable.failure = IllegalStateException("offline")

        assertTrue(repository.isUsernameAvailable("who").isFailure)
    }

    @Test
    fun `isUsernameAvailable calls the isUsernameAvailable function`() = runTest {
        callable.payload = mapOf("available" to true)

        repository.isUsernameAvailable("who")

        assertEquals(listOf("isUsernameAvailable"), callable.names)
    }

    @Test
    fun `isEmailRegistered calls the isEmailRegistered function`() = runTest {
        callable.payload = mapOf("registered" to true)

        repository.isEmailRegistered("a@b.com")

        assertEquals(listOf("isEmailRegistered"), callable.names)
    }

    @Test
    fun `isEmailRegistered reports what the callable returned`() = runTest {
        callable.payload = mapOf("registered" to true)

        assertEquals(true, repository.isEmailRegistered("a@b.com").getOrNull())
    }

    @Test
    fun `isEmailRegistered is false when the payload is malformed`() = runTest {
        callable.payload = mapOf("registered" to "yes")

        assertEquals(false, repository.isEmailRegistered("a@b.com").getOrNull())
    }

    // -------------------------------------------------------------------------
    // Clearing local state on the way out
    // -------------------------------------------------------------------------

    @Test
    fun `signOut clears local user data`() = runTest {
        val result = repository.signOut()

        assertTrue(result.isSuccess)
        assertTrue("signOut" in auth.calls)
        assertEquals(1, localUserDataCleaner.clearCallCount)
    }

    /**
     * The token unregister is best-effort: it talks to the network, and a user who taps sign out
     * with no connection still has to end up signed out locally.
     */
    @Test
    fun `signOut still signs out when unregistering push fails`() = runTest {
        userRepository.unregisterFromPushNotificationsResult =
            Result.failure(IllegalStateException("offline"))

        val result = repository.signOut()

        assertTrue(result.isSuccess)
        assertTrue("signOut" in auth.calls)
        assertEquals(1, localUserDataCleaner.clearCallCount)
        assertTrue(errorHandler.logged.isNotEmpty())
    }

    /*
     * The signed-out arms of the two operations that used to succeed silently. `?.delete()` and
     * `?.sendEmailVerification()` no-opped when nobody was signed in and `safeCall` wrapped the
     * nothing that happened in `Result.success` -- so the UI navigated away reporting a deleted
     * account while the account, and its profile document, survived. deleteAccount also has to
     * leave the local data alone: wiping the cache for a deletion that did not happen loses notes
     * for an account that still exists.
     */

    @Test
    fun `deleteAccount raises NotAuthenticated when nobody is signed in`() = runTest {
        val result = repository.deleteAccount()

        assertTrue(result.exceptionOrNull() is AppError.NotAuthenticated)
        assertEquals(0, localUserDataCleaner.clearCallCount)
    }

    @Test
    fun `sendEmailVerification raises NotAuthenticated when nobody is signed in`() = runTest {
        assertTrue(repository.sendEmailVerification().exceptionOrNull() is AppError.NotAuthenticated)
    }

    @Test
    fun `deleteAccount clears local user data`() = runTest {
        val user = signedInUser()

        val result = repository.deleteAccount()

        assertTrue(result.isSuccess)
        assertTrue("delete" in user.calls)
        assertEquals(1, localUserDataCleaner.clearCallCount)
    }

    // -------------------------------------------------------------------------
    // Signed-out edges
    // -------------------------------------------------------------------------

    /*
     * Both arms assert the identity rather than just `isFailure`, because the identity is the whole
     * point: `appErrorMessageResId` renders [AppError.NotAuthenticated] as "not signed in", while
     * anything else falls through `toErrorMessage`'s blank-message guard to a generic "an
     * error occurred". A bare `isFailure` passes either way.
     */

    @Test
    fun `changePassword raises NotAuthenticated when nobody is signed in`() = runTest {
        val result = repository.changePassword("old", "new")

        assertTrue(result.exceptionOrNull() is AppError.NotAuthenticated)
    }

    @Test
    fun `changePassword raises NotAuthenticated when the account has no email`() = runTest {
        auth.currentUser = FakeAuthUser(email = null)

        val result = repository.changePassword("old", "new")

        assertTrue(result.exceptionOrNull() is AppError.NotAuthenticated)
    }

    /** Pinned in order: the update only runs on a reauthenticated session. */
    @Test
    fun `changePassword reauthenticates before updating the password`() = runTest {
        val user = signedInUser()

        val result = repository.changePassword("old", "new")

        assertTrue(result.isSuccess)
        assertEquals(listOf("reauthenticate:user@example.com:old", "updatePassword:new"), user.calls)
    }

    /** The flag is read after the reload, which is what fetches the server's value. */
    @Test
    fun `checkEmailVerified reflects the reloaded flag`() = runTest {
        val user = signedInUser().apply { verifiedAfterReload = true }

        assertEquals(true, repository.checkEmailVerified().getOrNull())
        assertTrue("reload" in user.calls)
    }

    @Test
    fun `checkEmailVerified is false when nobody is signed in`() = runTest {
        assertEquals(false, repository.checkEmailVerified().getOrNull())
    }

    @Test
    fun `currentUserId and email-verified read through to auth`() {
        auth.currentUser = FakeAuthUser(uid = "uid-1").apply { isEmailVerified = false }

        assertEquals("uid-1", repository.currentUserId)
        assertFalse(repository.isCurrentUserEmailVerified)
    }
}