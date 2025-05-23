package com.jiahan.smartcamera.data.repository

import com.jiahan.smartcamera.database.data.DatabaseNote

interface NoteRepository {
    suspend fun getNotes(): List<DatabaseNote>
    suspend fun saveNote(databaseNote: DatabaseNote)
    suspend fun searchNotes(query: String): List<DatabaseNote>
}