package com.jiahan.smartcamera.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.firebase.Firebase
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storage
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.database.dao.NoteDao
import com.jiahan.smartcamera.database.data.toDatabaseNote
import com.jiahan.smartcamera.database.data.toHomeNote
import com.jiahan.smartcamera.domain.DetectedLabel
import com.jiahan.smartcamera.domain.DetectedObject
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.domain.MediaDetail
import com.jiahan.smartcamera.domain.NoteMediaDetail
import com.jiahan.smartcamera.util.FileConstants.EXTENSION_JPG
import com.jiahan.smartcamera.util.FileConstants.EXTENSION_MP4
import com.jiahan.smartcamera.util.FileConstants.PREFIX_THUMBNAIL
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.createVideoThumbnail
import com.jiahan.smartcamera.util.safeCall
import com.jiahan.smartcamera.data.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

class DefaultNoteRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val remoteConfigRepository: RemoteConfigRepository,
    private val authRepository: AuthRepository,
    private val firestore: FirebaseFirestore,
    private val noteDao: NoteDao,
    private val errorHandler: ErrorHandler,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
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

        // Detection field names
        private const val FIELD_OBJECT = "object"
        private const val FIELD_LABEL = "label"
        private const val FIELD_SCORE = "score"
    }

    private val storage: FirebaseStorage by lazy {
        Firebase.storage(remoteConfigRepository.getStorageUrl())
    }
    private val storageFolder: String by lazy { remoteConfigRepository.getStorageFolderName() }
    private val cacheStorageFolder: String by lazy { remoteConfigRepository.getStorageCacheFolderName() }

    private val paginationMutex = Mutex()
    private val pageToLastVisibleDocument = ConcurrentHashMap<Int, DocumentSnapshot>()

    @Volatile
    private var lastKnownUserId: String? = null

    private val noteCollectionReference: CollectionReference?
        get() = authRepository.currentUserId?.let { id ->
            firestore.collection(COLLECTION_USER)
                .document(id)
                .collection(COLLECTION_NOTE)
        }

    override suspend fun getNotes(page: Int, pageSize: Int): Result<List<HomeNote>> = safeCall {
        val currentUserId = authRepository.currentUserId
        paginationMutex.withLock {
            if (currentUserId != lastKnownUserId) {
                pageToLastVisibleDocument.clear()
                lastKnownUserId = currentUserId
            }
        }

        noteCollectionReference?.let { ref ->
            val baseQuery = ref
                .orderBy(FIELD_CREATED, Query.Direction.DESCENDING)
                .limit(pageSize.toLong())

            val snapshot = if (page == 0) {
                // First page - reset pagination state
                paginationMutex.withLock { pageToLastVisibleDocument.clear() }
                baseQuery.get().await()
            } else {
                val lastVisibleDoc = pageToLastVisibleDocument[page - 1]
                if (lastVisibleDoc != null) {
                    baseQuery.startAfter(lastVisibleDoc).get().await()
                } else {
                    baseQuery.get().await()
                }
            }

            if (snapshot.documents.isNotEmpty()) {
                pageToLastVisibleDocument[page] = snapshot.documents.last()
            }

            val userIds = snapshot.documents.mapNotNull { it.getString(FIELD_USER_ID) }.distinct()
            val userDocumentsMap = getUserDocumentsInBatch(userIds)
            snapshot.documents.map { document ->
                val userId = document.getString(FIELD_USER_ID) as String
                userDocumentsMap[userId]?.let { getHomeNote(document, it) }
                    ?: HomeNote(documentPath = "", username = "")
            }
        } ?: emptyList()
    }

    override suspend fun addNote(homeNote: HomeNote): Result<Unit> = safeCall {
        val userId = authRepository.currentUserId
            ?: throw IllegalStateException("User is not authenticated")
        noteCollectionReference?.add(
            hashMapOf(
                FIELD_TEXT to homeNote.text,
                FIELD_CREATED to FieldValue.serverTimestamp(),
                FIELD_FAVORITE to false,
                FIELD_MEDIA_LIST to homeNote.mediaList,
                FIELD_USER_ID to userId
            )
        )?.await()
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
                .map { document ->
                    val userId = document.getString(FIELD_USER_ID) as String
                    userDocumentsMap[userId]?.let { getHomeNote(document, it) }
                        ?: HomeNote(documentPath = "", username = "")
                }
        } ?: emptyList()
    }

    override suspend fun deleteNote(documentPath: String): Result<Unit> = safeCall {
        noteCollectionReference?.document(documentPath)?.delete()?.await()
        noteDao.deleteNote(documentPath)
    }

    override suspend fun favoriteNote(homeNote: HomeNote): Result<Unit> = safeCall {
        val newFavoriteStatus = homeNote.favorite.not()
        noteCollectionReference?.document(homeNote.documentPath)
            ?.update(FIELD_FAVORITE, newFavoriteStatus)?.await()
        if (newFavoriteStatus) {
            noteDao.upsertNotes(listOf(homeNote.copy(favorite = true).toDatabaseNote()))
        } else {
            noteDao.deleteNote(homeNote.documentPath)
        }
    }


    override suspend fun getNote(documentPath: String): Result<HomeNote> = safeCall {
        noteCollectionReference?.let { ref ->
            val noteDocument = ref.document(documentPath).get().await()
            val userDocument =
                getUserDocumentSnapshot(noteDocument.getString(FIELD_USER_ID) as String)
            getHomeNote(noteDocument, userDocument)
        } ?: HomeNote(documentPath = "", username = "")
    }

    override suspend fun uploadMediaToFirebase(
        noteMediaDetailList: List<NoteMediaDetail>
    ): Result<List<MediaDetail>> = safeCall {
        coroutineScope {
            noteMediaDetailList.map { noteMediaDetail ->
                async(Dispatchers.IO) {
                    safeCall {
                        val mediaId = UUID.randomUUID().toString()
                        val extension =
                            if (noteMediaDetail.isVideo) EXTENSION_MP4 else EXTENSION_JPG
                        val storageRef =
                            storage.reference.child("$storageFolder/$mediaId$extension")

                        val mediaUri = noteMediaDetail.photoUri ?: noteMediaDetail.videoUri
                        ?: throw IllegalStateException(
                            context.getString(R.string.no_media_available)
                        )

                        storageRef.putFile(mediaUri).await()
                        val mediaUrl = storageRef.downloadUrl.await().toString()

                        val thumbnailUrl = noteMediaDetail.thumbnailUri?.let { thumbUri ->
                            val thumbnailId = PREFIX_THUMBNAIL + UUID.randomUUID().toString()
                            val thumbnailRef =
                                storage.reference.child("$storageFolder/$thumbnailId$EXTENSION_JPG")
                            thumbnailRef.putFile(thumbUri).await()
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

    override suspend fun syncFavoriteNotes(): Result<Unit> = safeCall {
        val favorites = fetchAllFavoritesFromFirestore()
        noteDao.syncFavoriteNotes(favorites.map { it.toDatabaseNote() })
    }

    override suspend fun buildLocalMediaDetails(uriList: List<Uri>): Result<List<NoteMediaDetail>> =
        safeCall {
            withContext(Dispatchers.IO) {
                uriList.mapNotNull { uri ->
                    safeCall {
                        val isVideo =
                            context.contentResolver.getType(uri)?.startsWith("video/") == true
                        val thumbnailUri = if (isVideo) {
                            createVideoThumbnail(context, uri)?.let { saveBitmapAsTempFile(it) }
                        } else null
                        NoteMediaDetail(
                            photoUri = if (!isVideo) uri else null,
                            videoUri = if (isVideo) uri else null,
                            thumbnailUri = thumbnailUri,
                            isVideo = isVideo
                        )
                    }.onFailure { e -> errorHandler.logError(e) }.getOrNull()
                }
            }
        }

    private fun saveBitmapAsTempFile(bitmap: Bitmap): Uri? {
        return try {
            val file = File.createTempFile("thumbnail_", ".jpg", context.cacheDir)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            Uri.fromFile(file)
        } catch (e: Exception) {
            errorHandler.logError(e)
            null
        } finally {
            bitmap.recycle()
        }
    }

    /** Fire-and-forget — no Result returned; errors logged internally. */
    override suspend fun quickUploadMediaToFirebase(uriList: List<Uri>) {
        uriList.forEach { uri ->
            applicationScope.launch(Dispatchers.IO) {
                safeCall {
                    val mediaId = UUID.randomUUID().toString()
                    val storageRef = storage.reference.child("$cacheStorageFolder/$mediaId")
                    storageRef.putFile(uri).await()
                }.onFailure { e -> errorHandler.logError(e) }
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

    override fun getFavoriteNotesStream(query: String): Flow<List<HomeNote>> =
        noteDao.getFavoriteNotes().map { notes ->
            val homeNotes = notes.map { it.toHomeNote() }
            if (query.isEmpty()) homeNotes
            else homeNotes.filter { note ->
                note.text?.contains(query, ignoreCase = true) == true ||
                        note.mediaList?.any { media ->
                            media.generatedText?.any {
                                it.contains(
                                    query,
                                    ignoreCase = true
                                )
                            } == true ||
                                    media.generatedObjects?.any {
                                        it.objectName.contains(query, ignoreCase = true)
                                    } == true ||
                                    media.generatedLabels?.any {
                                        it.label.contains(query, ignoreCase = true)
                                    } == true
                        } == true
            }
        }

    private fun getHomeNote(
        noteDocumentSnapshot: DocumentSnapshot,
        userDocumentSnapshot: DocumentSnapshot
    ) = HomeNote(
        text = noteDocumentSnapshot.getString(FIELD_TEXT),
        createdDate = noteDocumentSnapshot.getDate(FIELD_CREATED)?.toInstant(),
        documentPath = noteDocumentSnapshot.id,
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
        generatedText = (mediaMap[FIELD_GENERATED_TEXT] as? List<*>)?.mapNotNull { it as? String },
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
        }
    )

    private fun matchesSearchQuery(document: DocumentSnapshot, query: String): Boolean {
        if (document.getString(FIELD_TEXT)?.contains(query, ignoreCase = true) == true) return true
        val mediaList = document.get(FIELD_MEDIA_LIST) as? List<*>
        return mediaList?.any { item ->
            val mediaMap = item as? Map<*, *> ?: return@any false
            val generatedText = mediaMap[FIELD_GENERATED_TEXT] as? List<*>
            if (generatedText?.any {
                    (it as? String)?.contains(
                        query,
                        ignoreCase = true
                    ) == true
                } == true) return@any true
            val generatedObjects = mediaMap[FIELD_GENERATED_OBJECTS] as? List<*>
            if (generatedObjects?.any {
                    (it as? Map<*, *>)?.get(FIELD_OBJECT)?.toString()
                        ?.contains(query, ignoreCase = true) == true
                } == true) return@any true
            val generatedLabels = mediaMap[FIELD_GENERATED_LABELS] as? List<*>
            generatedLabels?.any {
                (it as? Map<*, *>)?.get(FIELD_LABEL)?.toString()
                    ?.contains(query, ignoreCase = true) == true
            } == true
        } == true
    }
}