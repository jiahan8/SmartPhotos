package com.jiahan.smartcamera.data.repository

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.functions.FirebaseFunctions
import com.jiahan.smartcamera.data.LocalUserDataCleaner
import com.jiahan.smartcamera.domain.AppError
import com.jiahan.smartcamera.util.ErrorHandler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

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
 * - **The callable payload readers.** `isUsernameAvailable`/`isEmailRegistered` reach into an
 *   untyped `Map` from a Cloud Function and fall back to `false`. That fallback is a decision, not
 *   a formality: it makes an unreachable or reshaped backend read as "taken"/"not registered",
 *   which blocks signup rather than letting it through.
 * - **The local wipe on the way out.** `signOut` and `deleteAccount` clear the Room mirror, so the
 *   next account does not open onto the previous one's notes.
 *
 * Robolectric for the same reason as [DefaultUserRepositoryTest]: the Firebase types these mocks
 * stand in for are Android-bound, and `Tasks` needs a Looper.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class DefaultAuthRepositoryTest {

    private val auth: FirebaseAuth = mockk(relaxed = true)
    private val functions: FirebaseFunctions = mockk(relaxed = true)
    private val userRepository: UserRepository = mockk(relaxed = true)
    private val localUserDataCleaner: LocalUserDataCleaner = mockk(relaxed = true)
    private val errorHandler: ErrorHandler = mockk(relaxed = true)

    private val repository = DefaultAuthRepository(
        auth = auth,
        functions = functions,
        userRepository = userRepository,
        localUserDataCleaner = localUserDataCleaner,
        errorHandler = errorHandler,
    )

    /**
     * Stubs the callable this repository invokes, returning [payload] as its `data`.
     *
     * The returned slot captures the function *name*, which the two tests below assert. Without
     * that, every payload case here passes against any name -- swapping the two `FUNCTION_*`
     * constants would leave this whole file green while both checks called the wrong backend.
     */
    private fun functionsReturn(payload: Any?) = stubCallable(functions, payload)

    /** A signed-in user whose every Task-returning call succeeds. */
    private fun signedInUser(): FirebaseUser {
        val user: FirebaseUser = mockk(relaxed = true)
        every { user.email } returns "user@example.com"
        every { user.updateProfile(any()) } returns Tasks.forResult(null)
        every { user.sendEmailVerification() } returns Tasks.forResult(null)
        every { user.delete() } returns Tasks.forResult(null)
        every { user.reload() } returns Tasks.forResult(null)
        every { auth.currentUser } returns user
        return user
    }

    // -------------------------------------------------------------------------
    // Signup rollback
    // -------------------------------------------------------------------------

    @Test
    fun `signUp deletes the auth account when profile creation fails`() = runTest {
        val user = signedInUser()
        every { auth.createUserWithEmailAndPassword(any(), any()) } returns
                Tasks.forResult(mockk<AuthResult>())
        coEvery { userRepository.createUserProfile(any(), any()) } returns
                Result.failure(IllegalStateException("username taken"))

        val result = repository.signUp("a@b.com", "pw", "Display", "username")

        assertTrue(result.isFailure)
        verify { user.delete() }
    }

    @Test
    fun `signUp keeps the auth account when profile creation succeeds`() = runTest {
        val user = signedInUser()
        every { auth.createUserWithEmailAndPassword(any(), any()) } returns
                Tasks.forResult(mockk<AuthResult>())
        coEvery { userRepository.createUserProfile(any(), any()) } returns Result.success(Unit)

        val result = repository.signUp("a@b.com", "pw", "Display", "username")

        assertTrue(result.isSuccess)
        verify(exactly = 0) { user.delete() }
    }

    /** The display name reaches Auth, not just the profile document the Cloud Function writes. */
    @Test
    fun `signUp sends a verification email and sets the display name`() = runTest {
        val user = signedInUser()
        every { auth.createUserWithEmailAndPassword(any(), any()) } returns
                Tasks.forResult(mockk<AuthResult>())
        coEvery { userRepository.createUserProfile(any(), any()) } returns Result.success(Unit)

        repository.signUp("a@b.com", "pw", "Display", "username")

        verify { user.updateProfile(any()) }
        verify { user.sendEmailVerification() }
    }

    // -------------------------------------------------------------------------
    // Callable payload readers
    // -------------------------------------------------------------------------

    @Test
    fun `isUsernameAvailable reports what the callable returned`() = runTest {
        functionsReturn(mapOf("available" to true))

        assertEquals(true, repository.isUsernameAvailable("free").getOrNull())
    }

    @Test
    fun `isUsernameAvailable reports a taken username`() = runTest {
        functionsReturn(mapOf("available" to false))

        assertEquals(false, repository.isUsernameAvailable("taken").getOrNull())
    }

    /*
     * The three shapes a backend that is absent, older or reshaped can produce. Each has to read as
     * "not available" rather than as an availability: signup is gated on this, so failing open
     * would let a user through to a createUserProfile that then rejects them.
     */

    @Test
    fun `isUsernameAvailable is false when the payload has no available key`() = runTest {
        functionsReturn(mapOf("other" to true))

        assertEquals(false, repository.isUsernameAvailable("who").getOrNull())
    }

    @Test
    fun `isUsernameAvailable is false when the payload is not a map`() = runTest {
        functionsReturn("available")

        assertEquals(false, repository.isUsernameAvailable("who").getOrNull())
    }

    @Test
    fun `isUsernameAvailable is false when the payload is null`() = runTest {
        functionsReturn(null)

        assertEquals(false, repository.isUsernameAvailable("who").getOrNull())
    }

    @Test
    fun `isUsernameAvailable fails when the callable fails`() = runTest {
        stubCallableFailure(functions, IllegalStateException("offline"))

        assertTrue(repository.isUsernameAvailable("who").isFailure)
    }

    @Test
    fun `isUsernameAvailable calls the isUsernameAvailable function`() = runTest {
        val name = functionsReturn(mapOf("available" to true))

        repository.isUsernameAvailable("who")

        assertEquals("isUsernameAvailable", name.captured)
    }

    @Test
    fun `isEmailRegistered calls the isEmailRegistered function`() = runTest {
        val name = functionsReturn(mapOf("registered" to true))

        repository.isEmailRegistered("a@b.com")

        assertEquals("isEmailRegistered", name.captured)
    }

    @Test
    fun `isEmailRegistered reports what the callable returned`() = runTest {
        functionsReturn(mapOf("registered" to true))

        assertEquals(true, repository.isEmailRegistered("a@b.com").getOrNull())
    }

    @Test
    fun `isEmailRegistered is false when the payload is malformed`() = runTest {
        functionsReturn(mapOf("registered" to "yes"))

        assertEquals(false, repository.isEmailRegistered("a@b.com").getOrNull())
    }

    // -------------------------------------------------------------------------
    // Clearing local state on the way out
    // -------------------------------------------------------------------------

    @Test
    fun `signOut clears local user data`() = runTest {
        coEvery { userRepository.unregisterFromPushNotifications() } returns Result.success(Unit)

        val result = repository.signOut()

        assertTrue(result.isSuccess)
        verify { auth.signOut() }
        coVerify { localUserDataCleaner.clearLocalUserData() }
    }

    /**
     * The token unregister is best-effort: it talks to the network, and a user who taps sign out
     * with no connection still has to end up signed out locally.
     */
    @Test
    fun `signOut still signs out when unregistering push fails`() = runTest {
        coEvery { userRepository.unregisterFromPushNotifications() } returns
                Result.failure(IllegalStateException("offline"))

        val result = repository.signOut()

        assertTrue(result.isSuccess)
        verify { auth.signOut() }
        coVerify { localUserDataCleaner.clearLocalUserData() }
        verify { errorHandler.logError(any(), any()) }
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
        every { auth.currentUser } returns null

        val result = repository.deleteAccount()

        assertTrue(result.exceptionOrNull() is AppError.NotAuthenticated)
        coVerify(exactly = 0) { localUserDataCleaner.clearLocalUserData() }
    }

    @Test
    fun `sendEmailVerification raises NotAuthenticated when nobody is signed in`() = runTest {
        every { auth.currentUser } returns null

        assertTrue(repository.sendEmailVerification().exceptionOrNull() is AppError.NotAuthenticated)
    }

    @Test
    fun `deleteAccount clears local user data`() = runTest {
        val user = signedInUser()

        val result = repository.deleteAccount()

        assertTrue(result.isSuccess)
        verify { user.delete() }
        coVerify { localUserDataCleaner.clearLocalUserData() }
    }

    // -------------------------------------------------------------------------
    // Signed-out edges
    // -------------------------------------------------------------------------

    /*
     * Both arms assert the identity rather than just `isFailure`, because the identity is the whole
     * point: `appErrorMessageResId` renders [AppError.NotAuthenticated] as "not signed in", while
     * anything else falls through `DefaultErrorHandler`'s blank-message guard to a generic "an
     * error occurred". A bare `isFailure` passes either way -- which is how these two lines spent
     * their life throwing `IllegalArgumentException("")` from a `requireNotNull` instead.
     */

    @Test
    fun `changePassword raises NotAuthenticated when nobody is signed in`() = runTest {
        every { auth.currentUser } returns null

        val result = repository.changePassword("old", "new")

        assertTrue(result.exceptionOrNull() is AppError.NotAuthenticated)
    }

    @Test
    fun `changePassword raises NotAuthenticated when the account has no email`() = runTest {
        val user: FirebaseUser = mockk(relaxed = true)
        every { user.email } returns null
        every { auth.currentUser } returns user

        val result = repository.changePassword("old", "new")

        assertTrue(result.exceptionOrNull() is AppError.NotAuthenticated)
    }

    /** The reauthenticate/update pair only runs once both are present. */
    @Test
    fun `changePassword reauthenticates before updating the password`() = runTest {
        val user = signedInUser()
        every { user.reauthenticate(any()) } returns Tasks.forResult(null)
        every { user.updatePassword(any()) } returns Tasks.forResult(null)

        val result = repository.changePassword("old", "new")

        assertTrue(result.isSuccess)
        verify { user.reauthenticate(any()) }
        verify { user.updatePassword("new") }
    }

    @Test
    fun `checkEmailVerified reflects the reloaded flag`() = runTest {
        val user = signedInUser()
        every { user.isEmailVerified } returns true

        assertEquals(true, repository.checkEmailVerified().getOrNull())
        verify { user.reload() }
    }

    @Test
    fun `checkEmailVerified is false when nobody is signed in`() = runTest {
        every { auth.currentUser } returns null

        assertEquals(false, repository.checkEmailVerified().getOrNull())
    }

    @Test
    fun `currentUserId and email-verified read through to auth`() {
        every { auth.uid } returns "uid-1"
        val user = signedInUser()
        every { user.isEmailVerified } returns false

        assertEquals("uid-1", repository.currentUserId)
        assertFalse(repository.isCurrentUserEmailVerified)
    }
}