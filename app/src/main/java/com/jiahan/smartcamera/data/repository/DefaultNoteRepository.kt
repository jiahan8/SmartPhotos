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
import com.google.firebase.storage.storage
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.datastore.ProfileRepository
import com.jiahan.smartcamera.domain.DetectedLabel
import com.jiahan.smartcamera.domain.DetectedObject
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.domain.MediaDetail
import com.jiahan.smartcamera.domain.NoteMediaDetail
import com.jiahan.smartcamera.util.FileConstants.EXTENSION_JPG
import com.jiahan.smartcamera.util.FileConstants.EXTENSION_MP4
import com.jiahan.smartcamera.util.FileConstants.PREFIX_THUMBNAIL
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.inject.Inject

class DefaultNoteRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val remoteConfigRepository: RemoteConfigRepository,
    private val profileRepository: ProfileRepository,
    private val firestore: FirebaseFirestore,
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

    private val pageToLastVisibleDocument = mutableMapOf<Int, DocumentSnapshot>()
    private val noteCollectionReference: CollectionReference?
        get() = profileRepository.firebaseUser?.uid?.let { id ->
            firestore.collection(COLLECTION_USER)
                .document(id)
                .collection(COLLECTION_NOTE)
        }

    override suspend fun getNotes(page: Int, pageSize: Int): List<HomeNote> {
        noteCollectionReference?.let { ref ->
            try {
                val baseQuery = ref
                    .orderBy(FIELD_CREATED, Query.Direction.DESCENDING)
                    .limit(pageSize.toLong())

                val snapshot = if (page == 0) {
                    // First page - reset pagination state
                    pageToLastVisibleDocument.clear()
                    baseQuery.get().await()
                } else {
                    // Get the last document from the previous page
                    val lastVisibleDoc = pageToLastVisibleDocument[page - 1]

                    if (lastVisibleDoc != null) {
                        // Use startAfter with the last document from previous page
                        baseQuery.startAfter(lastVisibleDoc).get().await()
                    } else {
                        // Fallback if we somehow don't have the previous page document
                        baseQuery.get().await()
                    }
                }

                // Store the last visible document for the current page
                if (snapshot.documents.isNotEmpty()) {
                    pageToLastVisibleDocument[page] = snapshot.documents.last()
                }

                val userIds = snapshot.documents.mapNotNull {
                    it.getString(FIELD_USER_ID)
                }.distinct()
                val userDocumentsMap = getUserDocumentsInBatch(userIds)
                return snapshot.documents.map { document ->
                    val userId = document.getString(FIELD_USER_ID) as String
                    val userDocument = userDocumentsMap[userId]
                    if (userDocument != null) {
                        getHomeNote(
                            noteDocumentSnapshot = document,
                            userDocumentSnapshot = userDocument
                        )
                    } else {
                        HomeNote(documentPath = "", username = "")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return emptyList()
            }
        }
        return emptyList()
    }

    override suspend fun addNote(homeNote: HomeNote) {
        val userId = profileRepository.firebaseUser?.uid ?: return
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

    override suspend fun searchNotes(query: String): List<HomeNote> {
        noteCollectionReference?.let { ref ->
            val snapshot = ref
                .orderBy(FIELD_CREATED, Query.Direction.DESCENDING)
                .get()
                .await()
            val userIds = snapshot.documents.mapNotNull {
                it.getString(FIELD_USER_ID)
            }.distinct()
            val userDocumentsMap = getUserDocumentsInBatch(userIds)
            return snapshot.documents
                .filter { document -> matchesSearchQuery(document, query) }
                .map { document ->
                    val userId = document.getString(FIELD_USER_ID) as String
                    val userDocument = userDocumentsMap[userId]
                    if (userDocument != null) {
                        getHomeNote(
                            noteDocumentSnapshot = document,
                            userDocumentSnapshot = userDocument
                        )
                    } else {
                        HomeNote(documentPath = "", username = "")
                    }
                }
        }
        return emptyList()
    }

    override suspend fun deleteNote(documentPath: String) {
        noteCollectionReference?.document(documentPath)?.delete()?.await()
    }

    override suspend fun favoriteNote(homeNote: HomeNote) {
        val snapshot = noteCollectionReference?.document(homeNote.documentPath)?.get()?.await()
        noteCollectionReference?.document(homeNote.documentPath)
            ?.update(FIELD_FAVORITE, snapshot?.getBoolean(FIELD_FAVORITE)?.not())
            ?.await()
    }

    override suspend fun searchFavoriteNotes(query: String): List<HomeNote> {
        noteCollectionReference?.let { ref ->
            val snapshot = ref
                .whereEqualTo(FIELD_FAVORITE, true)
                .orderBy(FIELD_CREATED, Query.Direction.DESCENDING)
                .get()
                .await()
            val userIds = snapshot.documents.mapNotNull {
                it.getString(FIELD_USER_ID)
            }.distinct()
            val userDocumentsMap = getUserDocumentsInBatch(userIds)
            return snapshot.documents
                .filter { document -> matchesSearchQuery(document, query) }
                .map { document ->
                    val userId = document.getString(FIELD_USER_ID) as String
                    val userDocument = userDocumentsMap[userId]
                    if (userDocument != null) {
                        getHomeNote(
                            noteDocumentSnapshot = document,
                            userDocumentSnapshot = userDocument
                        )
                    } else {
                        HomeNote(documentPath = "", username = "")
                    }
                }
        }
        return emptyList()
    }

    override suspend fun getNote(documentPath: String): HomeNote {
        noteCollectionReference?.let { ref ->
            val noteDocument = ref.document(documentPath).get().await()
            val userDocument =
                getUserDocumentSnapshot(noteDocument.getString(FIELD_USER_ID) as String)
            return getHomeNote(
                noteDocumentSnapshot = noteDocument,
                userDocumentSnapshot = userDocument
            )
        }
        return HomeNote(documentPath = "", username = "")
    }

    override suspend fun quickUploadMediaToFirebase(uriList: List<Uri>) {
        val storage = Firebase.storage(remoteConfigRepository.getStorageUrl())
        val cacheStorageFolder = remoteConfigRepository.getStorageCacheFolderName()

        // Fire-and-forget: launches uploads in background without waiting
        uriList.forEach { uri ->
            @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    val mediaId = UUID.randomUUID().toString()
                    val storageRef = storage.reference.child("$cacheStorageFolder/$mediaId")
                    storageRef.putFile(uri).await()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override suspend fun uploadMediaToFirebase(noteMediaDetailList: List<NoteMediaDetail>): List<MediaDetail> {
        val storage = Firebase.storage(remoteConfigRepository.getStorageUrl())
        val storageFolder = remoteConfigRepository.getStorageFolderName()
        return coroutineScope {
            noteMediaDetailList.map { noteMediaDetail ->
                async(Dispatchers.IO) {
                    try {
                        val mediaId = UUID.randomUUID().toString()
                        val extension =
                            if (noteMediaDetail.isVideo) EXTENSION_MP4 else EXTENSION_JPG
                        val storageRef =
                            storage.reference.child("$storageFolder/$mediaId$extension")

                        val mediaUri = noteMediaDetail.photoUri ?: noteMediaDetail.videoUri
                        if (mediaUri == null) {
                            throw IllegalStateException(context.getString(R.string.no_media_available))
                        }

                        storageRef.putFile(mediaUri).await()
                        val mediaUrl = storageRef.downloadUrl.await().toString()

                        val thumbnailUrl = noteMediaDetail.thumbnailBitmap?.let {
                            val thumbnailId = PREFIX_THUMBNAIL + UUID.randomUUID().toString()
                            val thumbnailRef =
                                storage.reference.child("$storageFolder/$thumbnailId$EXTENSION_JPG")

                            ByteArrayOutputStream().use { baos ->
                                it.compress(Bitmap.CompressFormat.JPEG, 90, baos)
                                thumbnailRef.putBytes(baos.toByteArray()).await()
                            }

                            thumbnailRef.downloadUrl.await().toString()
                        }

                        MediaDetail(
                            photoUrl = if (!noteMediaDetail.isVideo) mediaUrl else null,
                            videoUrl = if (noteMediaDetail.isVideo) mediaUrl else null,
                            thumbnailUrl = thumbnailUrl,
                            isVideo = noteMediaDetail.isVideo,
                            text = noteMediaDetail.text
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    private suspend fun getUserDocumentSnapshot(userId: String) =
        firestore.collection(COLLECTION_USER).document(userId).get().await()

    private fun getHomeNote(
        noteDocumentSnapshot: DocumentSnapshot,
        userDocumentSnapshot: DocumentSnapshot
    ) = HomeNote(
        text = noteDocumentSnapshot.getString(FIELD_TEXT),
        createdDate = noteDocumentSnapshot.getDate(FIELD_CREATED),
        documentPath = noteDocumentSnapshot.id,
        favorite = noteDocumentSnapshot.getBoolean(FIELD_FAVORITE) == true,
        mediaList = (noteDocumentSnapshot.get(FIELD_MEDIA_LIST) as? List<*>)?.mapNotNull { item ->
            val mediaMap = item as? Map<*, *> ?: return@mapNotNull null
            parseMediaDetail(mediaMap)
        },
        username = userDocumentSnapshot.getString(FIELD_USERNAME) ?: "",
        profilePictureUrl = userDocumentSnapshot.getString(FIELD_PROFILE_PICTURE)
    )

    private fun parseMediaDetail(mediaMap: Map<*, *>): MediaDetail = MediaDetail(
        photoUrl = mediaMap[FIELD_PHOTO_URL] as? String,
        videoUrl = mediaMap[FIELD_VIDEO_URL] as? String,
        thumbnailUrl = mediaMap[FIELD_THUMBNAIL_URL] as? String,
        isVideo = mediaMap[FIELD_VIDEO] as? Boolean == true,
        text = mediaMap[FIELD_TEXT] as? String,
        generatedText = (mediaMap[FIELD_GENERATED_TEXT] as? List<*>)?.mapNotNull { it as? String },
        generatedObjects = parseDetectedObjects(mediaMap[FIELD_GENERATED_OBJECTS] as? List<*>),
        generatedLabels = parseDetectedLabels(mediaMap[FIELD_GENERATED_LABELS] as? List<*>)
    )

    private fun parseDetectedObjects(objectsList: List<*>?): List<DetectedObject>? {
        return objectsList?.mapNotNull { objectItem ->
            val objectMap = objectItem as? Map<*, *>
            val objectValue = objectMap?.get(FIELD_OBJECT) as? String
            val scoreValue = objectMap?.get(FIELD_SCORE) as? Double
            if (objectValue != null && scoreValue != null) {
                DetectedObject(objectName = objectValue, score = scoreValue)
            } else null
        }
    }

    private fun parseDetectedLabels(labelsList: List<*>?): List<DetectedLabel>? {
        return labelsList?.mapNotNull { labelItem ->
            val labelMap = labelItem as? Map<*, *>
            val labelValue = labelMap?.get(FIELD_LABEL) as? String
            val scoreValue = labelMap?.get(FIELD_SCORE) as? Double
            if (labelValue != null && scoreValue != null) {
                DetectedLabel(label = labelValue, score = scoreValue)
            } else null
        }
    }

    private fun matchesSearchQuery(document: DocumentSnapshot, query: String): Boolean {
        // Check if the main note text contains the query
        val noteText = document.getString(FIELD_TEXT) ?: ""
        if (noteText.contains(query, ignoreCase = true)) {
            return true
        }

        // Check if any media item's generated content contains the query
        val mediaList = document.get(FIELD_MEDIA_LIST) as? List<*>
        return mediaList?.any { item ->
            matchesMediaSearchQuery(item as? Map<*, *>, query)
        } == true
    }

    private fun matchesMediaSearchQuery(mediaMap: Map<*, *>?, query: String): Boolean {
        if (mediaMap == null) return false

        // Check generatedText array
        val generatedText = mediaMap[FIELD_GENERATED_TEXT] as? List<*>
        if (generatedText?.any { text ->
                (text as? String)?.contains(query, ignoreCase = true) == true
            } == true) {
            return true
        }

        // Check generatedObjects - look in "object" field values
        val generatedObjects = mediaMap[FIELD_GENERATED_OBJECTS] as? List<*>
        if (generatedObjects?.any { objectItem ->
                val objectMap = objectItem as? Map<*, *>
                val objectValue = objectMap?.get(FIELD_OBJECT) as? String
                objectValue?.contains(query, ignoreCase = true) == true
            } == true) {
            return true
        }

        // Check generatedLabels - look in "label" field values
        val generatedLabels = mediaMap[FIELD_GENERATED_LABELS] as? List<*>
        return generatedLabels?.any { labelItem ->
            val labelMap = labelItem as? Map<*, *>
            val labelValue = labelMap?.get(FIELD_LABEL) as? String
            labelValue?.contains(query, ignoreCase = true) == true
        } == true
    }

    private suspend fun getUserDocumentsInBatch(userIds: List<String>): Map<String, DocumentSnapshot> {
        if (userIds.isEmpty()) return emptyMap()
        return coroutineScope {
            userIds.map { userId ->
                async {
                    try {
                        val document = getUserDocumentSnapshot(userId)
                        userId to document
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }
                }
            }.awaitAll().filterNotNull().toMap()
        }
    }
}