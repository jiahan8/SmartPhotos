package com.jiahan.smartcamera.data.repository

import com.jiahan.smartcamera.database.data.DatabasePhoto
import kotlinx.coroutines.flow.Flow

interface PhotoRepository {
    val photos: Flow<List<DatabasePhoto>>
    suspend fun savePhoto(databasePhoto: DatabasePhoto)
}