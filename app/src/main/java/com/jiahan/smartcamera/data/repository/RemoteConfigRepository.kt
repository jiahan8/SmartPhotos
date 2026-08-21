package com.jiahan.smartcamera.data.repository

import kotlinx.coroutines.flow.Flow

interface RemoteConfigRepository {
    suspend fun fetchAndActivateConfig(): Result<Unit>
    fun getStorageUrl(): String
    fun getStorageFolderName(): String
    fun getStorageCacheFolderName(): String
    fun observeExploreIconVisible(): Flow<Boolean>
}