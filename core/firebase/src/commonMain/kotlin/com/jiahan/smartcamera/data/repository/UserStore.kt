package com.jiahan.smartcamera.data.repository

import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Timestamp
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The `user/{uid}` reads and writes [DefaultUserRepository] makes: its seam onto Firestore, for the
 * reason [NoteStore] gives.
 */
internal interface UserStore {
    /**
     * `user/{userId}`, decoded. A document that does not exist reads as every field absent rather
     * than as null, which is what the Android SDK reader's snapshot gave it.
     */
    suspend fun getUser(userId: String): FirestoreUser

    /** Writes [fields] into `user/{userId}`, a null value clearing its field. */
    suspend fun updateUser(userId: String, fields: Map<String, Any?>)
}

/** A `user/{uid}` document. Every field is defaulted, as in [FirestoreNote]. */
@Serializable
internal class FirestoreUser(
    val email: String? = null,
    val metadata: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    val username: String? = null,
    @SerialName("profile_picture") val profilePicture: String? = null,
    val created: Timestamp? = null,
)

internal class GitLiveUserStore(private val firestore: FirebaseFirestore) : UserStore {

    private companion object {
        const val COLLECTION_USER = "user"
    }

    override suspend fun getUser(userId: String): FirestoreUser =
        firestore.collection(COLLECTION_USER).document(userId).get()
            .takeIf { it.exists }
            ?.data(FirestoreUser.serializer())
            ?: FirestoreUser()

    override suspend fun updateUser(userId: String, fields: Map<String, Any?>) {
        firestore.collection(COLLECTION_USER).document(userId).updateFields {
            fields.forEach { (field, value) -> field to value }
        }
    }
}