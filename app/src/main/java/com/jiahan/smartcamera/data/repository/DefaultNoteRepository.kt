package com.jiahan.smartcamera.data.repository

import android.graphics.Bitmap
import android.net.Uri
import com.google.firebase.Firebase
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.storage
import com.jiahan.smartcamera.datastore.UserDataRepository
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.domain.MediaDetail
import com.jiahan.smartcamera.domain.NoteMediaDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.inject.Inject

class DefaultNoteRepository @Inject constructor(
    private val remoteConfigRepository: RemoteConfigRepository,
    private val userDataRepository: UserDataRepository,
    private val firestore: FirebaseFirestore,
) : NoteRepository {

    private val pageToLastVisibleDocument = mutableMapOf<Int, DocumentSnapshot>()
    private val noteCollectionReference: CollectionReference?
        get() = userDataRepository.firebaseUser?.uid?.let { id ->
            firestore.collection("user")
                .document(id)
                .collection("note")
        }

    override suspend fun getNotes(page: Int, pageSize: Int): List<HomeNote> {
        noteCollectionReference?.let { ref ->
            try {
                val baseQuery = ref
                    .orderBy("created", Query.Direction.DESCENDING)
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
                    it.getString("user_id")
                }.distinct()
                val userDocumentsMap = getUserDocumentsInBatch(userIds)
                return snapshot.documents.map { document ->
                    val userId = document.getString("user_id") as String
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
        val userId = userDataRepository.firebaseUser?.uid ?: return
        noteCollectionReference?.add(
            hashMapOf(
                "text" to homeNote.text,
                "created" to FieldValue.serverTimestamp(),
                "favorite" to false,
                "media_list" to homeNote.mediaList,
                "user_id" to userId
            )
        )?.await()
    }

    override suspend fun searchNotes(query: String): List<HomeNote> {
        noteCollectionReference?.let { ref ->
            val snapshot = ref
                .orderBy("created", Query.Direction.DESCENDING)
                .get()
                .await()
            val userIds = snapshot.documents.mapNotNull {
                it.getString("user_id")
            }.distinct()
            val userDocumentsMap = getUserDocumentsInBatch(userIds)
            return snapshot.documents
                .filter { document ->
                    // Check if the main note text contains the query
                    val noteText = document.getString("text") ?: ""
                    val containsInNoteText = noteText.contains(query, ignoreCase = true)

                    // Check if any media item's text contains the query
                    val mediaList = document.get("media_list") as? List<*>
                    val containsInMediaText = mediaList?.any { item ->
                        val mediaMap = item as? Map<*, *>
                        val mediaText = mediaMap?.get("text")?.toString() ?: ""
                        mediaText.contains(query, ignoreCase = true)
                    } == true

                    // Return true if the query is found in either the note text or any media text
                    containsInNoteText || containsInMediaText
                }
                .map { document ->
                    val userId = document.getString("user_id") as String
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
            ?.update("favorite", snapshot?.getBoolean("favorite")?.not())
            ?.await()
    }

    override suspend fun searchFavoriteNotes(query: String): List<HomeNote> {
        noteCollectionReference?.let { ref ->
            val snapshot = ref
                .whereEqualTo("favorite", true)
                .orderBy("created", Query.Direction.DESCENDING)
                .get()
                .await()
            val userIds = snapshot.documents.mapNotNull {
                it.getString("user_id")
            }.distinct()
            val userDocumentsMap = getUserDocumentsInBatch(userIds)
            return snapshot.documents
                .filter { document ->
                    // Check if the main note text contains the query
                    val noteText = document.getString("text") ?: ""
                    val containsInNoteText = noteText.contains(query, ignoreCase = true)

                    // Check if any media item's text contains the query
                    val mediaList = document.data?.get("media_list") as? List<*>
                    val containsInMediaText = mediaList?.any { item ->
                        val mediaMap = item as? Map<*, *>
                        val mediaText = mediaMap?.get("text")?.toString() ?: ""
                        mediaText.contains(query, ignoreCase = true)
                    } == true

                    // Return true if the query is found in either the note text or any media text
                    containsInNoteText || containsInMediaText
                }
                .map { document ->
                    val userId = document.getString("user_id") as String
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
                getUserDocumentSnapshot(noteDocument.getString("user_id") as String)
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
        coroutineScope {
            uriList.forEach { uri ->
                async(Dispatchers.IO) {
                    try {
                        val mediaId = UUID.randomUUID().toString()
                        val storageRef = storage.reference.child("$cacheStorageFolder/$mediaId")
                        storageRef.putFile(uri)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
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
                        val extension = if (noteMediaDetail.isVideo) ".mp4" else ".jpg"
                        val storageRef =
                            storage.reference.child("$storageFolder/$mediaId$extension")

                        val mediaUri = noteMediaDetail.photoUri ?: noteMediaDetail.videoUri
                        if (mediaUri == null) {
                            throw IllegalStateException("No media URI available for upload")
                        }

                        storageRef.putFile(mediaUri).await()
                        val mediaUrl = storageRef.downloadUrl.await().toString()

                        val thumbnailUrl = noteMediaDetail.thumbnailBitmap?.let {
                            val thumbnailId = "thumbnail_" + UUID.randomUUID().toString()
                            val thumbnailRef =
                                storage.reference.child("$storageFolder/$thumbnailId.jpg")

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
        firestore.collection("user").document(userId).get().await()

    private fun getHomeNote(
        noteDocumentSnapshot: DocumentSnapshot,
        userDocumentSnapshot: DocumentSnapshot
    ) = HomeNote(
        text = noteDocumentSnapshot.getString("text"),
        createdDate = noteDocumentSnapshot.getDate("created"),
        documentPath = noteDocumentSnapshot.id,
        favorite = noteDocumentSnapshot.getBoolean("favorite") == true,
        mediaList = (noteDocumentSnapshot.get("media_list") as? List<*>)?.mapNotNull { item ->
            val mediaMap = item as? Map<*, *> ?: return@mapNotNull null
            MediaDetail(
                photoUrl = mediaMap["photoUrl"] as? String,
                videoUrl = mediaMap["videoUrl"] as? String,
                thumbnailUrl = mediaMap["thumbnailUrl"] as? String,
                isVideo = mediaMap["video"] as? Boolean == true,
                text = mediaMap["text"] as? String
            )
        },
        username = userDocumentSnapshot.getString("username") ?: "",
        profilePictureUrl = userDocumentSnapshot.getString("profile_picture")
    )

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