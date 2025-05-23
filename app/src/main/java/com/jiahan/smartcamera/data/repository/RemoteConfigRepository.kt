package com.jiahan.smartcamera.data.repository

interface RemoteConfigRepository {
    suspend fun fetchAndActivateConfig(): Boolean
    fun getStorageUrl(): String
    fun getStorageFolderName(): String
}