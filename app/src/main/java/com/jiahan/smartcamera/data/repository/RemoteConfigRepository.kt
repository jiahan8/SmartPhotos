package com.jiahan.smartcamera.data.repository

interface RemoteConfigRepository {
    suspend fun fetchAndActivateConfig(): Result<Unit>
    fun getStorageUrl(): String
    fun getStorageFolderName(): String
    fun getStorageCacheFolderName(): String
}