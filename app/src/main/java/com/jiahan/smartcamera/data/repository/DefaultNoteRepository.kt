package com.jiahan.smartcamera.data.repository

import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.jiahan.smartcamera.database.data.DatabaseNote
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class DefaultNoteRepository @Inject constructor() : NoteRepository {
    private val firestore = Firebase.firestore

    override suspend fun getNotes(): List<DatabaseNote> {
        val snapshot = firestore.collection("note").get().await()
        return snapshot.documents.map { document ->
            DatabaseNote(
                text = document.data?.get("text")?.toString() ?: "",
                createdDate = document.getDate("created")?.time ?: System.currentTimeMillis(),
                documentPath = document.id
            )
        }
    }

    override suspend fun saveNote(databaseNote: DatabaseNote) {
        firestore.collection("note")
            .add(databaseNote)
            .await()
    }

    override suspend fun searchNotes(query: String): List<DatabaseNote> {
        val snapshot = firestore.collection("note").get().await()
        return snapshot.documents
            .filter { document ->
                val text = document.data?.get("text")?.toString() ?: ""
                text.contains(query, ignoreCase = true)
            }
            .map { document ->
                DatabaseNote(
                    text = document.data?.get("text")?.toString() ?: "",
                    createdDate = document.getDate("created")?.time
                        ?: System.currentTimeMillis(),
                    documentPath = document.id
                )
            }
    }

    override suspend fun deleteNote(documentPath: String) {
        firestore.collection("note").document(documentPath).delete().await()
    }
}