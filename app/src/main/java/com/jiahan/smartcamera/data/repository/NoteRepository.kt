package com.jiahan.smartcamera.data.repository

import android.net.Uri
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.domain.MediaDetail
import com.jiahan.smartcamera.domain.NoteMediaDetail

interface NoteRepository {
    suspend fun getNotes(page: Int = 0, pageSize: Int = 10): List<HomeNote>
    suspend fun addNote(homeNote: HomeNote)
    suspend fun searchNotes(query: String): List<HomeNote>
    suspend fun deleteNote(documentPath: String)
    suspend fun favoriteNote(homeNote: HomeNote)
    suspend fun searchFavoriteNotes(query: String): List<HomeNote>
    suspend fun getNote(documentPath: String): HomeNote
    suspend fun quickUploadMediaToFirebase(uriList: List<Uri>)
    suspend fun uploadMediaToFirebase(noteMediaDetailList: List<NoteMediaDetail>): List<MediaDetail>
}