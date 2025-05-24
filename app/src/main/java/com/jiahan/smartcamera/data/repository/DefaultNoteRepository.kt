package com.jiahan.smartcamera.data.repository

import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.jiahan.smartcamera.domain.HomeNote
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class DefaultNoteRepository @Inject constructor() : NoteRepository {
    private val firestore = Firebase.firestore

    override suspend fun getNotes(): List<HomeNote> {
        val snapshot =
            firestore.collection("note").orderBy("created", Query.Direction.DESCENDING).get()
                .await()
        return snapshot.documents.map { document ->
            HomeNote(
                text = document.data?.get("text")?.toString() ?: "",
                createdDate = document.getDate("created"),
                documentPath = document.id
            )
        }
    }

    override suspend fun saveNote(homeNote: HomeNote) {
        firestore.collection("note")
            .add(
                hashMapOf(
                    "text" to homeNote.text,
                    "created" to FieldValue.serverTimestamp()
                )
            )
            .await()
    }

    override suspend fun searchNotes(query: String): List<HomeNote> {
        val snapshot =
            firestore.collection("note").orderBy("created", Query.Direction.DESCENDING).get()
                .await()
        return snapshot.documents
            .filter { document ->
                val text = document.data?.get("text")?.toString() ?: ""
                text.contains(query, ignoreCase = true)
            }
            .map { document ->
                HomeNote(
                    text = document.data?.get("text")?.toString() ?: "",
                    createdDate = document.getDate("created"),
                    documentPath = document.id
                )
            }
    }

    override suspend fun deleteNote(documentPath: String) {
        firestore.collection("note").document(documentPath).delete().await()
    }
}