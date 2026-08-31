package com.jiahan.smartcamera.data.repository

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storage
import com.jiahan.smartcamera.database.dao.NoteDao
import com.jiahan.smartcamera.database.data.toDatabaseNote
import com.jiahan.smartcamera.database.data.toHomeNote
import com.jiahan.smartcamera.domain.AppError
import com.jiahan.smartcamera.domain.DetectedLabel
import com.jiahan.smartcamera.domain.DetectedObject
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.domain.MediaDetail
import com.jiahan.smartcamera.domain.MediaUri
import com.jiahan.smartcamera.domain.NoteCursor
import com.jiahan.smartcamera.domain.NoteMediaDetail
import com.jiahan.smartcamera.domain.NotePage
import com.jiahan.smartcamera.util.FileConstants.EXTENSION_JPG
import com.jiahan.smartcamera.util.FileConstants.EXTENSION_MP4
import com.jiahan.smartcamera.util.FileConstants.PREFIX_THUMBNAIL
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.createVideoThumbnail
import com.jiahan.smartcamera.util.safeCall
import com.jiahan.smartcamera.util.toMediaUri
import com.jiahan.smartcamera.util.toPlatformUri
import com.jiahan.smartcamera.di.ApplicationScope
import com.jiahan.smartcamera.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.time.Instant
import kotlin.uuid.Uuid

class DefaultNoteRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val remoteConfigRepository: RemoteConfigRepository,
    private val authRepository: AuthRepository,
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val noteDao: NoteDao,
    private val mediaFileRepository: MediaFileRepository,
    private val errorHandler: ErrorHandler,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
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
        private const val FUNCTION_CREATE_NOTE = "createNote"
        private const val FUNCTION_UPDATE_NOTE = "updateNote"
        private const val ARG_NOTE_ID = "noteId"
        private const val ARG_TEXT = "text"
        private const val ARG_MEDIA_LIST = "mediaList"
        private const val ARG_PHOTO_URL = "photoUrl"
        private const val ARG_VIDEO_URL = "videoUrl"
        private const val ARG_THUMBNAIL_URL = "thumbnailUrl"
        private const val ARG_IS_VIDEO = "isVideo"
    }

    private val storage: FirebaseStorage by lazy {
        Firebase.storage(remoteConfigRepository.getStorageUrl())
    }
    private val storageFolder: String by lazy { remoteConfigRepository.getStorageFolderName() }
    private val cacheStorageFolder: String by lazy { remoteConfigRepository.getStorageCacheFolderName() }
    private fun userScopedPath(folder: String, userId: String, fileName: String) =
        "$folder/$userId/$fileName"

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
            val userDocumentsMap = getUserDocumentsInBatch(userIds)
            val notes = snapshot.documents.mapNotNull { document ->
                val userId = document.getString(FIELD_USER_ID) ?: return@mapNotNull null
                userDocumentsMap[userId]?.let { getHomeNote(document, it) }
            }
            // The cursor comes from the documents the query returned, not from the mapped
            // notes: getUserDocumentsInBatch tolerates a failed author lookup by dropping that
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
    // MAX_POST_TEXT_LENGTH and stamps the true owner into user_id server-side
    // instead of trusting a client-supplied value.
    override suspend fun addNote(homeNote: HomeNote): Result<Unit> = safeCall {
        val mediaListPayload = homeNote.mediaList.orEmpty().map { media ->
            hashMapOf(
                ARG_PHOTO_URL to media.photoUrl,
                ARG_VIDEO_URL to media.videoUrl,
                ARG_THUMBNAIL_URL to media.thumbnailUrl,
                ARG_IS_VIDEO to media.isVideo
            )
        }
        functions.getHttpsCallable(FUNCTION_CREATE_NOTE)
            .call(hashMapOf(ARG_TEXT to homeNote.text, ARG_MEDIA_LIST to mediaListPayload))
            .await()
    }

    // Delegates to the updateNote Cloud Function for the same reason addNote
    // delegates to createNote: server-side validation and ownership checks a
    // direct client-side Firestore update couldn't do. Only [homeNote]'s text
    // is sent -- a note's media is fixed at creation time -- but the whole note
    // is taken so the local mirror can be refreshed with it below.
    override suspend fun updateNote(homeNote: HomeNote): Result<Unit> = safeCall {
        functions.getHttpsCallable(FUNCTION_UPDATE_NOTE)
            .call(hashMapOf(ARG_NOTE_ID to homeNote.noteId, ARG_TEXT to homeNote.text))
            .await()
        // Unconditional: this write used to be gated on `homeNote.favorite`, back when the table
        // held favorites only. It mirrors the whole feed now, so an edit to any note has to land.
        noteDao.upsertNotes(listOf(homeNote.toDatabaseNote()))
    }

    override suspend fun searchNotes(query: String): Result<List<HomeNote>> = safeCall {
        noteCollectionReference?.let { ref ->
            val snapshot = ref
                .orderBy(FIELD_CREATED, Query.Direction.DESCENDING)
                .get()
                .await()
            val userIds = snapshot.documents.mapNotNull { it.getString(FIELD_USER_ID) }.distinct()
            val userDocumentsMap = getUserDocumentsInBatch(userIds)
            snapshot.documents
                .filter { document -> matchesSearchQuery(document, query) }
                .mapNotNull { document ->
                    val userId = document.getString(FIELD_USER_ID) ?: return@mapNotNull null
                    userDocumentsMap[userId]?.let { getHomeNote(document, it) }
                }
        } ?: emptyList()
    }

    override suspend fun deleteNote(noteId: String): Result<Unit> = safeCall {
        noteCollectionReference?.document(noteId)?.delete()?.await()
        noteDao.deleteNote(noteId)
    }

    override suspend fun favoriteNote(homeNote: HomeNote): Result<Unit> = safeCall {
        val newFavoriteStatus = homeNote.favorite.not()
        noteCollectionReference?.document(homeNote.noteId)
            ?.update(FIELD_FAVORITE, newFavoriteStatus)?.await()
        // One upsert for both directions. Unfavoriting used to delete the row, which was right
        // when the table was a favorites-only cache and wrong now that it mirrors the feed -- the
        // note still exists, it is just no longer favorited. Upsert rather than the DAO's
        // `updateFavorite` because the row may not be there yet: a note can be favorited straight
        // from a screen that never paged it in, and an UPDATE against a missing row is a silent
        // no-op that would lose it from Favorite.
        noteDao.upsertNotes(
            listOf(homeNote.copy(favorite = newFavoriteStatus).toDatabaseNote())
        )
    }

    override suspend fun getNote(noteId: String): Result<HomeNote> = safeCall {
        noteCollectionReference?.let { ref ->
            val noteDocument = ref.document(noteId).get().await()
            if (!noteDocument.exists()) throw AppError.NoteUnavailable()
            val userId = noteDocument.getString(FIELD_USER_ID)
                ?: throw AppError.NoteUnavailable()
            val userDocument = getUserDocumentSnapshot(userId)
            if (!userDocument.exists()) throw AppError.NoteUnavailable()
            getHomeNote(noteDocument, userDocument)
        } ?: throw AppError.NotAuthenticated()
    }

    override suspend fun uploadMediaToFirebase(
        noteMediaDetailList: List<NoteMediaDetail>
    ): Result<List<MediaDetail>> = safeCall {
        val userId = authRepository.currentUserId
            ?: throw AppError.NotAuthenticated()
        coroutineScope {
            noteMediaDetailList.map { noteMediaDetail ->
                async(ioDispatcher) {
                    safeCall {
                        val mediaId = Uuid.random().toString()
                        val extension =
                            if (noteMediaDetail.isVideo) EXTENSION_MP4 else EXTENSION_JPG
                        val storageRef =
                            storage.reference.child(
                                userScopedPath(storageFolder, userId, "$mediaId$extension")
                            )

                        val mediaUri = noteMediaDetail.photoUri ?: noteMediaDetail.videoUri
                        ?: throw AppError.NoMediaAvailable()

                        storageRef.putFile(mediaUri.toPlatformUri()).await()
                        val mediaUrl = storageRef.downloadUrl.await().toString()

                        val thumbnailUrl = noteMediaDetail.thumbnailUri?.let { thumbUri ->
                            val thumbnailId = PREFIX_THUMBNAIL + Uuid.random().toString()
                            val thumbnailRef =
                                storage.reference.child(
                                    userScopedPath(
                                        storageFolder,
                                        userId,
                                        "$thumbnailId$EXTENSION_JPG"
                                    )
                                )
                            thumbnailRef.putFile(thumbUri.toPlatformUri()).await()
                            thumbnailRef.downloadUrl.await().toString()
                        }

                        MediaDetail(
                            photoUrl = if (!noteMediaDetail.isVideo) mediaUrl else null,
                            videoUrl = if (noteMediaDetail.isVideo) mediaUrl else null,
                            thumbnailUrl = thumbnailUrl,
                            isVideo = noteMediaDetail.isVideo
                        )
                    }.onFailure { e -> errorHandler.logError(e) }.getOrNull()
                }
            }.awaitAll().filterNotNull()
        }
    }

    /**
     * Mirrors a fetched page into Room.
     *
     * Best effort, and deliberately so *for now*: nothing reads this table for the feed yet, so a
     * failed cache write must not turn a successful fetch into a failed one and blank the screen.
     * It is logged rather than swallowed silently, which is what [ErrorHandler.logError] is for at
     * this layer. **Revisit when the feed starts observing Room** -- at that point a lost write is
     * a note the user cannot see, and it should surface rather than be logged.
     */
    private suspend fun cacheNotes(notes: List<HomeNote>) {
        if (notes.isEmpty()) return
        safeCall { noteDao.upsertNotes(notes.map { it.toDatabaseNote() }) }
            .onFailure { e -> errorHandler.logError(e) }
    }

    // Still favorites-only, and still correct: the DAO's syncFavoriteNotes clears the favorited
    // rows and reinserts what the server says is favorited, leaving the mirrored non-favorites
    // alone. It becomes a full sync when the feed moves onto Room.
    override suspend fun syncFavoriteNotes(): Result<Unit> = safeCall {
        val favorites = fetchAllFavoritesFromFirestore()
        noteDao.syncFavoriteNotes(favorites.map { it.toDatabaseNote() })
    }

    override suspend fun buildLocalMediaDetails(
        uriList: List<MediaUri>
    ): Result<List<NoteMediaDetail>> =
        safeCall {
            withContext(ioDispatcher) {
                uriList.mapNotNull { mediaUri ->
                    safeCall {
                        val uri = mediaUri.toPlatformUri()
                        val isVideo = mediaFileRepository.isVideoUri(uri)
                        val thumbnailUri = if (isVideo) {
                            createVideoThumbnail(context, uri)
                                ?.let { mediaFileRepository.saveBitmapAsTempFile(it) }
                                ?.toMediaUri()
                        } else null
                        NoteMediaDetail(
                            photoUri = if (!isVideo) mediaUri else null,
                            videoUri = if (isVideo) mediaUri else null,
                            thumbnailUri = thumbnailUri,
                            isVideo = isVideo
                        )
                    }.onFailure { e -> errorHandler.logError(e) }.getOrNull()
                }
            }
        }

    override suspend fun quickUploadMediaToFirebase(
        uriList: List<MediaUri>,
        deleteAfterUpload: Boolean
    ) {
        val userId = authRepository.currentUserId
        uriList.forEach { mediaUri ->
            applicationScope.launch(ioDispatcher) {
                val uri = mediaUri.toPlatformUri()
                if (userId != null && mediaFileRepository.hasContent(uri)) {
                    safeCall {
                        val mediaId = Uuid.random().toString()
                        val storageRef = storage.reference.child(
                            userScopedPath(cacheStorageFolder, userId, mediaId)
                        )
                        storageRef.putFile(uri).await()
                    }.onFailure { e -> errorHandler.logError(e) }
                }
                if (deleteAfterUpload) mediaFileRepository.deleteUri(uri)
            }
        }
    }

    private suspend fun getUserDocumentSnapshot(userId: String) =
        firestore.collection(COLLECTION_USER).document(userId).get().await()

    /**
     * Fetches user documents in parallel.
     * A single failed lookup is logged and skipped (partial-result tolerance).
     */
    private suspend fun getUserDocumentsInBatch(
        userIds: List<String>
    ): Map<String, DocumentSnapshot> {
        if (userIds.isEmpty()) return emptyMap()
        return coroutineScope {
            userIds.map { userId ->
                async {
                    safeCall { userId to getUserDocumentSnapshot(userId) }
                        .onFailure { e -> errorHandler.logError(e) }
                        .getOrNull()
                }
            }.awaitAll().filterNotNull().toMap()
        }
    }

    private suspend fun fetchAllFavoritesFromFirestore(): List<HomeNote> {
        noteCollectionReference?.let { ref ->
            val snapshot = ref
                .whereEqualTo(FIELD_FAVORITE, true)
                .orderBy(FIELD_CREATED, Query.Direction.DESCENDING)
                .get()
                .await()
            val userIds = snapshot.documents.mapNotNull { it.getString(FIELD_USER_ID) }.distinct()
            val userDocumentsMap = getUserDocumentsInBatch(userIds)
            return snapshot.documents.mapNotNull { document ->
                val userId = document.getString(FIELD_USER_ID) ?: return@mapNotNull null
                val userDocument = userDocumentsMap[userId] ?: return@mapNotNull null
                getHomeNote(document, userDocument)
            }
        }
        return emptyList()
    }

    override fun getNotesStream(): Flow<List<HomeNote>> =
        noteDao.getNotes().map { notes -> notes.map { it.toHomeNote() } }

    override fun getFavoriteNotesStream(query: String): Flow<List<HomeNote>> =
        noteDao.getFavoriteNotes().map { notes ->
            val homeNotes = notes.map { it.toHomeNote() }
            if (query.isEmpty()) homeNotes
            else homeNotes.filter { note -> matchesQuery(note.text, note.mediaList, query) }
        }

    private fun getHomeNote(
        noteDocumentSnapshot: DocumentSnapshot,
        userDocumentSnapshot: DocumentSnapshot
    ) = HomeNote(
        noteId = noteDocumentSnapshot.id,
        text = noteDocumentSnapshot.getString(FIELD_TEXT),
        createdDate = noteDocumentSnapshot.getDate(FIELD_CREATED)
            ?.let { Instant.fromEpochMilliseconds(it.time) },
        favorite = noteDocumentSnapshot.getBoolean(FIELD_FAVORITE) == true,
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
        generatedText = (mediaMap[FIELD_GENERATED_TEXT] as? List<*>)?.filterIsInstance<String>(),
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
                    media.generatedText?.any { it.contains(query, ignoreCase = true) } == true ||
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