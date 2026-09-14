package com.jiahan.smartcamera.data.repository

import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.Query

/**
 * The Firestore reads and writes [DefaultNoteRepository] makes: the seam between it and Firebase,
 * since GitLive's `DocumentSnapshot` and `Query` are platform classes no `commonTest` can build.
 *
 * Documents come back decoded into [FirestoreNote] and [FirestoreAuthor] rather than as snapshots, so
 * a fake can answer with raw field maps run through GitLive's own `decode` and keep the readers under
 * test.
 */
internal interface NoteStore {
    /**
     * `user/{userId}/note`, newest first: the [limit] documents after [startAfter], or all of them
     * when [limit] is null, narrowed to favorited notes when [favoritesOnly].
     */
    suspend fun getNotes(
        userId: String,
        limit: Int? = null,
        startAfter: NotePosition? = null,
        favoritesOnly: Boolean = false,
    ): List<NoteDocument>

    /** One of [userId]'s notes, or null when the document does not exist. */
    suspend fun getNote(userId: String, noteId: String): NoteDocument?

    /** The `user/{userId}` document a note's author fields come from, or null when it does not exist. */
    suspend fun getAuthor(userId: String): FirestoreAuthor?

    suspend fun deleteNote(userId: String, noteId: String)

    suspend fun setFavorite(userId: String, noteId: String, isFavorite: Boolean)
}

/**
 * Where a document sits in the query that returned it, to resume a page after. Opaque outside the
 * store that issued it: on Firestore it is the snapshot itself.
 */
internal interface NotePosition

internal class NoteDocument(
    val id: String,
    val fields: FirestoreNote,
    val position: NotePosition,
)

internal class GitLiveNoteStore(private val firestore: FirebaseFirestore) : NoteStore {

    private companion object {
        const val COLLECTION_USER = "user"
        const val COLLECTION_NOTE = "note"
        const val FIELD_CREATED = "created"
        const val FIELD_FAVORITE = "favorite"
    }

    private class SnapshotPosition(val snapshot: DocumentSnapshot) : NotePosition

    private fun noteCollection(userId: String) =
        firestore.collection(COLLECTION_USER).document(userId).collection(COLLECTION_NOTE)

    override suspend fun getNotes(
        userId: String,
        limit: Int?,
        startAfter: NotePosition?,
        favoritesOnly: Boolean,
    ): List<NoteDocument> {
        var query: Query = noteCollection(userId)
        if (favoritesOnly) query = query.where { FIELD_FAVORITE equalTo true }
        query = query.orderBy(FIELD_CREATED, Direction.DESCENDING)
        if (limit != null) query = query.limit(limit)
        // Every position the repository passes back came from a page this store returned.
        if (startAfter != null) query = query.startAfter((startAfter as SnapshotPosition).snapshot)
        return query.get().documents.map { it.toNoteDocument() }
    }

    override suspend fun getNote(userId: String, noteId: String): NoteDocument? =
        noteCollection(userId).document(noteId).get()
            .takeIf { it.exists }
            ?.toNoteDocument()

    override suspend fun getAuthor(userId: String): FirestoreAuthor? =
        firestore.collection(COLLECTION_USER).document(userId).get()
            .takeIf { it.exists }
            ?.data(FirestoreAuthor.serializer())

    override suspend fun deleteNote(userId: String, noteId: String) {
        noteCollection(userId).document(noteId).delete()
    }

    override suspend fun setFavorite(userId: String, noteId: String, isFavorite: Boolean) {
        noteCollection(userId).document(noteId).updateFields { FIELD_FAVORITE to isFavorite }
    }

    private fun DocumentSnapshot.toNoteDocument() = NoteDocument(
        id = id,
        fields = data(FirestoreNote.serializer()),
        position = SnapshotPosition(this),
    )
}