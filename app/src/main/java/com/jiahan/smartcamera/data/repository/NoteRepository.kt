package com.jiahan.smartcamera.data.repository

import com.jiahan.smartcamera.domain.HomeNote

interface NoteRepository {
    suspend fun getNotes(page: Int = 0, pageSize: Int = 10): List<HomeNote>
    suspend fun saveNote(homeNote: HomeNote)
    suspend fun searchNotes(query: String): List<HomeNote>
    suspend fun deleteNote(documentPath: String)
    suspend fun favoriteNote(homeNote: HomeNote)
}