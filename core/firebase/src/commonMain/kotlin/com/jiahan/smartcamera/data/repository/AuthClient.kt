package com.jiahan.smartcamera.data.repository

import dev.gitlive.firebase.auth.EmailAuthProvider
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser

/**
 * The Firebase Auth operations [DefaultAuthRepository] uses: the seam between it and Firebase, since
 * GitLive's `FirebaseAuth` and `FirebaseUser` are final platform classes no `commonTest` can fake.
 */
internal interface AuthClient {
    /** The signed-in user, read fresh on every access -- as `FirebaseAuth.currentUser` is. */
    val currentUser: AuthUser?

    suspend fun signIn(email: String, password: String)

    suspend fun createUser(email: String, password: String)

    suspend fun signOut()

    suspend fun sendPasswordResetEmail(email: String)
}

internal interface AuthUser {
    val uid: String
    val email: String?
    val isEmailVerified: Boolean

    suspend fun updateDisplayName(displayName: String)

    suspend fun sendEmailVerification()

    suspend fun delete()

    suspend fun reload()

    /** Reauthenticates with an email/password credential for [email]. */
    suspend fun reauthenticate(email: String, password: String)

    suspend fun updatePassword(password: String)
}

internal class GitLiveAuthClient(private val auth: FirebaseAuth) : AuthClient {

    override val currentUser: AuthUser?
        get() = auth.currentUser?.let(::GitLiveAuthUser)

    override suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
    }

    override suspend fun createUser(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password)
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    override suspend fun sendPasswordResetEmail(email: String) {
        auth.sendPasswordResetEmail(email)
    }
}

private class GitLiveAuthUser(private val user: FirebaseUser) : AuthUser {

    override val uid: String
        get() = user.uid

    override val email: String?
        get() = user.email

    override val isEmailVerified: Boolean
        get() = user.isEmailVerified

    // Display name only: the photo URL defaults to the current one, as the Android SDK's
    // displayName-only profile change request left it.
    override suspend fun updateDisplayName(displayName: String) {
        user.updateProfile(displayName = displayName)
    }

    override suspend fun sendEmailVerification() {
        user.sendEmailVerification()
    }

    override suspend fun delete() {
        user.delete()
    }

    override suspend fun reload() {
        user.reload()
    }

    override suspend fun reauthenticate(email: String, password: String) {
        user.reauthenticate(EmailAuthProvider.credential(email, password))
    }

    override suspend fun updatePassword(password: String) {
        user.updatePassword(password)
    }
}