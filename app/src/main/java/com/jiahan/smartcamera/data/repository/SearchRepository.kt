package com.jiahan.smartcamera.data.repository

import android.net.Uri
import com.jiahan.smartcamera.database.data.DatabasePhoto
import kotlinx.coroutines.flow.Flow

interface SearchRepository {
    val photos: Flow<List<DatabasePhoto>>
    suspend fun savePhoto(databasePhoto: DatabasePhoto)
    fun searchPhotos(query: String): Flow<List<DatabasePhoto>>
    suspend fun deleteImage(imageId: Int)
    suspend fun saveImageFromUri(uri: Uri, title: String)
    suspend fun isImageExists(path: String): Boolean
    suspend fun prepareAndStartImageDescription(
        imageUri: Uri
    ): String

    fun closeImageDescriber()
}