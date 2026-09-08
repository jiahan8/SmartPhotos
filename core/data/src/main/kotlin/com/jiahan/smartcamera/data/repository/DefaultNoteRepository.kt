package com.jiahan.smartcamera.data.repository

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.HttpsCallableResult
import com.jiahan.smartcamera.database.dao.NoteDao
import com.jiahan.smartcamera.database.data.toDatabaseNote
import com.jiahan.smartcamera.database.data.toNote
import com.jiahan.smartcamera.domain.AppError
import com.jiahan.smartcamera.domain.DetectedLabel
import com.jiahan.smartcamera.domain.DetectedObject
import com.jiahan.smartcamera.domain.MediaDetail
import com.jiahan.smartcamera.domain.Note
import com.jiahan.smartcamera.domain.NoteCursor
import com.jiahan.smartcamera.domain.NotePage
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.reason
import com.jiahan.smartcamera.util.safeCall
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import kotlin.time.Instant

class DefaultNoteRepository @Inject constructor(
    private val authRepository: AuthRepository,
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val noteDao: NoteDao,
    private val errorHandler: ErrorHandler,
) : NoteRepository {

    companion object {
        // Collection names
        private const val COLLECTION_USER = "user"
        private const val COLLECTION_NOTE = "note"

        // Field names
        private const val FIELD_TEXT = "text"
        private const val FIELD_CREATED = "created"
        private const val FIELD_FAVORITE = "favorite"
        private const val FIELD_MEDIA_LIST = "media_list"
        private const val FIELD_USER_ID = "user_id"
        private const val FIELD_USERNAME = "username"
        private const val FIELD_PROFILE_PICTURE = "profile_picture"

        // Media field names
        private const val FIELD_PHOTO_URL = "photoUrl"
        private const val FIELD_VIDEO_URL = "videoUrl"
        private const val FIELD_THUMBNAIL_URL = "thumbnailUrl"
        private const val FIELD_VIDEO = "video"
        private const val FIELD_GENERATED_TEXT = "generatedText"
        private const val FIELD_GENERATED_OBJECTS = "generatedObjects"
        private const val FIELD_GENERATED_LABELS = "generatedLabels"
        private const val FIELD_GENERATED_LANDMARKS = "generatedLandmarks"
        private const val FIELD_GENERATED_LOGOS = "generatedLogos"

        // Detection field names
        private const val FIELD_OBJECT = "object"
        private const val FIELD_LABEL = "label"
        private const val FIELD_SCORE = "score"

        // Cloud Function names / argument keys
        private const val REASON_TEXT_TOO_LONG = "TEXT_TOO_LONG"
        private const val REASON_TOO_MANY_MEDIA = "TOO_MANY_MEDIA_ITEMS"
        private const val REASON_EMPTY_NOTE = "EMPTY_NOTE"
        private const val FUNCTION_CREATE_NOTE = "createNote"
        private const val RESULT_DOCUMENT_PATH = "documentPath"
        private const val FUNCTION_UPDATE_NOTE = "updateNote"
        private const val ARG_NOTE_ID = "noteId"
        private const val ARG_TEXT = "text"
        private const val ARG_MEDIA_LIST = "mediaList"
        private const val ARG_PHOTO_URL = "photoUrl"
        private const val ARG_VIDEO_URL = "videoUrl"
        private const val ARG_THUMBNAIL_URL = "thumbnailUrl"
        private const val ARG_IS_VIDEO = "isVideo"
    }

    /**
     * The Firestore document a page ended on. Carries the account it was issued for so a cursor
     * left over from a previous sign-in is ignored rather than applied to another user's notes.
     */
    private data class FirestoreNoteCursor(
        val userId: String,
        val document: DocumentSnapshot
    ) : NoteCursor

    private val noteCollectionReference: CollectionReference?
        get() = authRepository.currentUserId?.let { id ->
            firestore.collection(COLLECTION_USER)
                .document(id)
                .collection(COLLECTION_NOTE)
        }

    override suspend fun getNotes(cursor: NoteCursor?, pageSize: Int): Result<NotePage> = safeCall {
        val currentUserId = authRepository.currentUserId ?: return@safeCall NotePage(emptyList())

        noteCollectionReference?.let { ref ->
            val baseQuery = ref
                .orderBy(FIELD_CREATED, Query.Direction.DESCENDING)
                .limit(pageSize.toLong())

            val startAfterDocument = (cursor as? FirestoreNoteCursor)
                ?.takeIf { it.userId == currentUserId }
                ?.document
            val snapshot = if (startAfterDocument != null) {
                baseQuery.startAfter(startAfterDocument).get().await()
            } else {
                baseQuery.get().await()
            }

            val userIds = snapshot.documents.mapNotNull { it.getString(FIELD_USER_ID) }.distinct()
            val userDocumentsMap = fetchUserDocuments(userIds)
            val notes = snapshot.documents.mapNotNull { document ->
                val userId = document.getString(FIELD_USER_ID) ?: return@mapNotNull null
                userDocumentsMap[userId]?.let { buildNote(document, it) }
            }
            // The cursor comes from the documents the query returned, not from the mapped
            // notes: fetchUserDocuments tolerates a failed author lookup by dropping that
            // note, so a short mapped list would otherwise be read as "end of feed".
            val lastDocument = snapshot.documents
                .takeIf { it.size >= pageSize }
                ?.lastOrNull()
            cacheNotes(notes)
            NotePage(
                notes = notes,
                nextCursor = lastDocument?.let { FirestoreNoteCursor(currentUserId, it) }
            )
        } ?: NotePage(emptyList())
    }

    // Delegates to the createNote Cloud Function, which enforces
    // MAX_NOTE_TEXT_LENGTH and stamps the true owner into user_id server-side
    // instead of trusting a client-supplied value.
    override suspend fun addNote(note: Note): Result<Unit> = safeCall {
        val mediaListPayload = note.mediaList.orEmpty().map { media ->
            hashMapOf(
                ARG_PHOTO_URL to media.photoUrl,
                ARG_VIDEO_URL to media.videoUrl,
                ARG_THUMBNAIL_URL to media.thumbnailUrl,
                ARG_IS_VIDEO to media.isVideo
            )
        }
        val response = functions.getHttpsCallable(FUNCTION_CREATE_NOTE)
            .call(hashMapOf(ARG_TEXT to note.text, ARG_MEDIA_LIST to mediaListPayload))
            .await()

        mirrorCreatedNote(response)
    }.foldNoteValidationError()

    // Delegates to the updateNote Cloud Function for the same reason addNote
    // delegates to createNote: server-side validation and ownership checks a
    // direct client-side Firestore update couldn't do. Only [note]'s text
    // is sent -- a note's media is fixed at creation time -- but the whole note
    // is taken so the local mirror can be refreshed with it below.
    override suspend fun updateNote(note: Note): Result<Unit> = safeCall {
        functions.getHttpsCallable(FUNCTION_UPDATE_NOTE)
            .call(hashMapOf(ARG_NOTE_ID to note.noteId, ARG_TEXT to note.text))
            .await()
        // Unconditional: this write used to be gated on `note.isFavorite`, back when the table
        // held favorites only. It mirrors the whole feed now, so an edit to any note has to land.
        noteDao.upsertNotes(listOf(note.toDatabaseNote()))
    }.foldNoteValidationError()

    // Reads the whole collection and filters client-side, which is what it has always done. What
    // changed is where the result goes: it is mirrored on the way out, so searchNotesStream covers
    // notes the feed never paged rather than narrowing search to what Home has scrolled.
    override suspend fun searchNotes(query: String): Result<List<Note>> = safeCall {
        noteCollectionReference?.let { ref ->
            val snapshot = ref
                .orderBy(FIELD_CREATED, Query.Direction.DESCENDING)
                .get()
                .await()
            val userIds = snapshot.documents.mapNotNull { it.getString(FIELD_USER_ID) }.distinct()
            val userDocumentsMap = fetchUserDocuments(userIds)
            val results = snapshot.documents
                .filter { document -> matchesSearchQuery(document, query) }
                .mapNotNull { document ->
                    val userId = document.getString(FIELD_USER_ID) ?: return@mapNotNull null
                    userDocumentsMap[userId]?.let { buildNote(document, it) }
                }
            cacheNotes(results)
            results
        } ?: emptyList()
    }

    override suspend fun deleteNote(noteId: String): Result<Unit> = safeCall {
        noteCollectionReference?.document(noteId)?.delete()?.await()
        noteDao.deleteNote(noteId)
    }

    override suspend fun toggleFavorite(note: Note): Result<Unit> = safeCall {
        val newFavoriteStatus = note.isFavorite.not()
        noteCollectionReference?.document(note.noteId)
            ?.update(FIELD_FAVORITE, newFavoriteStatus)?.await()
        // One upsert for both directions. Unfavoriting used to delete the row, which was right
        // when the table was a favorites-only cache and wrong now that it mirrors the feed -- the
        // note still exists, it is just no longer favorited. Upsert rather than the DAO's
        // `updateFavorite` because the row may not be there yet: a note can be favorited straight
        // from a screen that never paged it in, and an UPDATE against a missing row is a silent
        // no-op that would lose it from Favorite.
        noteDao.upsertNotes(
            listOf(note.copy(isFavorite = newFavoriteStatus).toDatabaseNote())
        )
    }

    override suspend fun getNote(noteId: String): Result<Note> = safeCall {
        noteCollectionReference?.let { ref ->
            val noteDocument = ref.document(noteId).get().await()
            if (!noteDocument.exists()) throw AppError.NoteUnavailable()
            val userId = noteDocument.getString(FIELD_USER_ID)
                ?: throw AppError.NoteUnavailable()
            val userDocument = getUserDocumentSnapshot(userId)
            if (!userDocument.exists()) throw AppError.NoteUnavailable()
            buildNote(noteDocument, userDocument).also { cacheNotes(listOf(it)) }
        } ?: throw AppError.NotAuthenticated()
    }

    /**
     * Reads a just-created note back so it lands in the mirror.
     *
     * The client cannot build that row itself -- the id and the `created` timestamp are both
     * stamped server-side -- and until this existed a new note reached the feed only through a
     * `NoteHandler` event telling Home to refetch everything. One extra document read retires that
     * whole mechanism.
     *
     * Deliberately not fatal: the note *was* created, so failing here would report a write that
     * succeeded as an error. A lost read-back costs the user a pull-to-refresh, which is exactly
     * what a failed reload cost them before.
     */
    private suspend fun mirrorCreatedNote(response: HttpsCallableResult) {
        val noteId = (response.data as? Map<*, *>)?.get(RESULT_DOCUMENT_PATH) as? String
        if (noteId == null) {
            errorHandler.logError(AppError.NoteUnavailable())
            return
        }
        getNote(noteId).onFailure(errorHandler::logError)
    }

    /**
     * Folds a createNote/updateNote validation rejection into an [AppError].
     *
     * Both functions signal every validation failure as one `invalid-argument` code and tell them
     * apart in a structured `details.reason` payload, so this reads the payload rather than the
     * code -- the one way it differs from how [AppError.UsernameTaken] is folded. Doing it here
     * rather than in a ViewModel-layer mapper is what keeps `FirebaseFunctionsException` below the
     * repository boundary, and off a feature module's classpath.
     *
     * An unrecognised reason is left alone: it means a malformed request no legitimate client can
     * produce, and it should surface as the generic failure rather than as a specific message that
     * happens to be wrong.
     */
    private fun <T> Result<T>.foldNoteValidationError(): Result<T> {
        val reason = (exceptionOrNull() as? FirebaseFunctionsException)?.reason()
        return when (reason) {
            REASON_TEXT_TOO_LONG -> Result.failure(AppError.NoteTextTooLong())
            REASON_TOO_MANY_MEDIA -> Result.failure(AppError.NoteMediaLimitExceeded())
            REASON_EMPTY_NOTE -> Result.failure(AppError.NoteEmpty())
            else -> this
        }
    }

    /**
     * Mirrors a fetched page into Room.
     *
     * No longer best effort. It was, while nothing read this table for the feed and a failed cache
     * write blanking a screen whose fetch succeeded would have been the worse trade. Home renders
     * the mirror now, so a page that fails to land is a page the user cannot see -- and swallowing
     * that would also let the caller advance its cursor past it, turning a lost write into a
     * permanent hole in the feed. Failing the enclosing [safeCall] leaves the cursor where it was,
     * so the page is retried instead.
     */
    private suspend fun cacheNotes(notes: List<Note>) {
        if (notes.isEmpty()) return
        noteDao.upsertNotes(notes.map { it.toDatabaseNote() })
    }

    // Still favorites-only, and still correct: the DAO's syncFavoriteNotes clears the favorited
    // rows and reinserts what the server says is favorited, leaving the mirrored non-favorites
    // alone. It becomes a full sync when the feed moves onto Room.
    override suspend fun syncFavoriteNotes(): Result<Unit> = safeCall {
        val favorites = fetchAllFavoritesFromFirestore()
        noteDao.syncFavoriteNotes(favorites.map { it.toDatabaseNote() })
    }

    private suspend fun getUserDocumentSnapshot(userId: String) =
        firestore.collection(COLLECTION_USER).document(userId).get().await()

    /**
     * Fetches user documents in parallel.
     * A single failed lookup is logged and skipped (partial-result tolerance).
     */
    private suspend fun fetchUserDocuments(
        userIds: List<String>
    ): Map<String, DocumentSnapshot> {
        if (userIds.isEmpty()) return emptyMap()
        return coroutineScope {
            userIds.map { userId ->
                async {
                    safeCall { userId to getUserDocumentSnapshot(userId) }
                        .onFailure(errorHandler::logError)
                        .getOrNull()
                }
            }.awaitAll().filterNotNull().toMap()
        }
    }

    private suspend fun fetchAllFavoritesFromFirestore(): List<Note> {
        noteCollectionReference?.let { ref ->
            val snapshot = ref
                .whereEqualTo(FIELD_FAVORITE, true)
                .orderBy(FIELD_CREATED, Query.Direction.DESCENDING)
                .get()
                .await()
            val userIds = snapshot.documents.mapNotNull { it.getString(FIELD_USER_ID) }.distinct()
            val userDocumentsMap = fetchUserDocuments(userIds)
            return snapshot.documents.mapNotNull { document ->
                val userId = document.getString(FIELD_USER_ID) ?: return@mapNotNull null
                val userDocument = userDocumentsMap[userId] ?: return@mapNotNull null
                buildNote(document, userDocument)
            }
        }
        return emptyList()
    }

    override fun getNotesStream(limit: Int): Flow<List<Note>> =
        noteDao.getNotes(limit).map { notes -> notes.map { it.toNote() } }

    override fun getNoteStream(noteId: String): Flow<Note?> =
        noteDao.getNote(noteId).map { note -> note?.toNote() }

    override fun searchNotesStream(query: String): Flow<List<Note>> =
        noteDao.getNotes().map { notes ->
            notes.filter { note -> matchesQuery(note.text, note.mediaList, query) }
                .map { it.toNote() }
        }

    override fun getFavoriteNotesStream(query: String): Flow<List<Note>> =
        noteDao.getFavoriteNotes().map { notes ->
            val matches =
                if (query.isBlank()) notes
                else notes.filter { note -> matchesQuery(note.text, note.mediaList, query) }
            matches.map { it.toNote() }
        }

    private fun buildNote(
        noteDocumentSnapshot: DocumentSnapshot,
        userDocumentSnapshot: DocumentSnapshot
    ) = Note(
        noteId = noteDocumentSnapshot.id,
        text = noteDocumentSnapshot.getString(FIELD_TEXT),
        createdDate = noteDocumentSnapshot.getDate(FIELD_CREATED)
            ?.let { Instant.fromEpochMilliseconds(it.time) },
        isFavorite = noteDocumentSnapshot.getBoolean(FIELD_FAVORITE) == true,
        mediaList = (noteDocumentSnapshot.get(FIELD_MEDIA_LIST) as? List<*>)?.mapNotNull { item ->
            (item as? Map<*, *>)?.let { parseMediaDetail(it) }
        },
        username = userDocumentSnapshot.getString(FIELD_USERNAME) ?: "",
        profilePictureUrl = userDocumentSnapshot.getString(FIELD_PROFILE_PICTURE)
    )

    private fun parseMediaDetail(mediaMap: Map<*, *>) = MediaDetail(
        photoUrl = mediaMap[FIELD_PHOTO_URL] as? String,
        videoUrl = mediaMap[FIELD_VIDEO_URL] as? String,
        thumbnailUrl = mediaMap[FIELD_THUMBNAIL_URL] as? String,
        isVideo = mediaMap[FIELD_VIDEO] as? Boolean == true,
        generatedTexts = (mediaMap[FIELD_GENERATED_TEXT] as? List<*>)?.filterIsInstance<String>(),
        generatedObjects = (mediaMap[FIELD_GENERATED_OBJECTS] as? List<*>)?.mapNotNull { objectItem ->
            val map = objectItem as? Map<*, *>
            val name = map?.get(FIELD_OBJECT) as? String
            val score = map?.get(FIELD_SCORE) as? Double
            if (name != null && score != null) DetectedObject(name, score) else null
        },
        generatedLabels = (mediaMap[FIELD_GENERATED_LABELS] as? List<*>)?.mapNotNull { labelItem ->
            val map = labelItem as? Map<*, *>
            val label = map?.get(FIELD_LABEL) as? String
            val score = map?.get(FIELD_SCORE) as? Double
            if (label != null && score != null) DetectedLabel(label, score) else null
        },
        generatedLandmarks = (mediaMap[FIELD_GENERATED_LANDMARKS] as? List<*>)?.mapNotNull { labelItem ->
            val map = labelItem as? Map<*, *>
            val label = map?.get(FIELD_LABEL) as? String
            val score = map?.get(FIELD_SCORE) as? Double
            if (label != null && score != null) DetectedLabel(label, score) else null
        },
        generatedLogos = (mediaMap[FIELD_GENERATED_LOGOS] as? List<*>)?.mapNotNull { labelItem ->
            val map = labelItem as? Map<*, *>
            val label = map?.get(FIELD_LABEL) as? String
            val score = map?.get(FIELD_SCORE) as? Double
            if (label != null && score != null) DetectedLabel(label, score) else null
        }
    )

    private fun matchesSearchQuery(document: DocumentSnapshot, query: String): Boolean {
        val mediaList = (document.get(FIELD_MEDIA_LIST) as? List<*>)
            ?.mapNotNull { (it as? Map<*, *>)?.let(::parseMediaDetail) }
        return matchesQuery(document.getString(FIELD_TEXT), mediaList, query)
    }

    /** Shared text/media match predicate used by both Firestore search and local favorite filtering. */
    private fun matchesQuery(text: String?, mediaList: List<MediaDetail>?, query: String): Boolean =
        text?.contains(query, ignoreCase = true) == true ||
                mediaList?.any { media ->
                    media.generatedTexts?.any { it.contains(query, ignoreCase = true) } == true ||
                            media.generatedObjects?.any {
                                it.objectName.contains(query, ignoreCase = true)
                            } == true ||
                            media.generatedLabels?.any {
                                it.label.contains(query, ignoreCase = true)
                            } == true ||
                            media.generatedLandmarks?.any {
                                it.label.contains(query, ignoreCase = true)
                            } == true ||
                            media.generatedLogos?.any {
                                it.label.contains(query, ignoreCase = true)
                            } == true
                } == true
}