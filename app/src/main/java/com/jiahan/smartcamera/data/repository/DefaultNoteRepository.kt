package com.jiahan.smartcamera.data.repository

import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.domain.MediaDetail
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class DefaultNoteRepository @Inject constructor() : NoteRepository {
    private val firestore = Firebase.firestore
    private val pageToLastVisibleDocument = mutableMapOf<Int, DocumentSnapshot>()

    override suspend fun getNotes(page: Int, pageSize: Int): List<HomeNote> {
        try {
            val baseQuery = firestore.collection("note")
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

            return snapshot.documents.map { document ->
                HomeNote(
                    text = document.data?.get("text")?.toString(),
                    createdDate = document.getDate("created"),
                    documentPath = document.id,
                    favorite = document.data?.get("favorite") as? Boolean == true,
                    mediaList = (document.data?.get("media_list") as? List<*>)?.mapNotNull { item ->
                        val mediaMap = item as? Map<*, *> ?: return@mapNotNull null
                        MediaDetail(
                            photoUrl = mediaMap["photoUrl"] as? String,
                            videoUrl = mediaMap["videoUrl"] as? String,
                            thumbnailUrl = mediaMap["thumbnailUrl"] as? String,
                            isVideo = mediaMap["video"] as? Boolean == true,
                            text = mediaMap["text"] as? String
                        )
                    }
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    override suspend fun saveNote(homeNote: HomeNote) {
        firestore.collection("note")
            .add(
                hashMapOf(
                    "text" to homeNote.text,
                    "created" to FieldValue.serverTimestamp(),
                    "favorite" to false,
                    "media_list" to homeNote.mediaList
                )
            )
            .await()
    }

    override suspend fun searchNotes(query: String): List<HomeNote> {
        val snapshot =
            firestore.collection("note")
                .orderBy("created", Query.Direction.DESCENDING)
                .get()
                .await()
        return snapshot.documents
            .filter { document ->
                // Check if the main note text contains the query
                val noteText = document.data?.get("text")?.toString() ?: ""
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
                HomeNote(
                    text = document.data?.get("text")?.toString(),
                    createdDate = document.getDate("created"),
                    documentPath = document.id,
                    favorite = document.data?.get("favorite") as Boolean,
                    mediaList = (document.data?.get("media_list") as? List<*>)?.mapNotNull { item ->
                        val mediaMap = item as? Map<*, *> ?: return@mapNotNull null
                        MediaDetail(
                            photoUrl = mediaMap["photoUrl"] as? String,
                            videoUrl = mediaMap["videoUrl"] as? String,
                            thumbnailUrl = mediaMap["thumbnailUrl"] as? String,
                            isVideo = mediaMap["video"] as? Boolean == true,
                            text = mediaMap["text"] as? String
                        )
                    }
                )
            }
    }

    override suspend fun deleteNote(documentPath: String) {
        firestore.collection("note").document(documentPath).delete().await()
    }

    override suspend fun favoriteNote(homeNote: HomeNote) {
        homeNote.documentPath?.let { documentPath ->
            firestore
                .collection("note").document(documentPath)
                .update("favorite", homeNote.favorite.not())
                .await()
        }
    }

    override suspend fun searchFavoritedNotes(query: String): List<HomeNote> {
        val snapshot =
            firestore.collection("note")
                .whereEqualTo("favorite", true)
                .orderBy("created", Query.Direction.DESCENDING)
                .get()
                .await()
        return snapshot.documents
            .filter { document ->
                // Check if the main note text contains the query
                val noteText = document.data?.get("text")?.toString() ?: ""
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
                HomeNote(
                    text = document.data?.get("text")?.toString(),
                    createdDate = document.getDate("created"),
                    documentPath = document.id,
                    favorite = document.data?.get("favorite") as Boolean,
                    mediaList = (document.data?.get("media_list") as? List<*>)?.mapNotNull { item ->
                        val mediaMap = item as? Map<*, *> ?: return@mapNotNull null
                        MediaDetail(
                            photoUrl = mediaMap["photoUrl"] as? String,
                            videoUrl = mediaMap["videoUrl"] as? String,
                            thumbnailUrl = mediaMap["thumbnailUrl"] as? String,
                            isVideo = mediaMap["video"] as? Boolean == true,
                            text = mediaMap["text"] as? String
                        )
                    }
                )
            }
    }
}