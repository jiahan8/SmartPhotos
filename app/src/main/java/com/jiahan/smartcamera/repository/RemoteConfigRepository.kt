package com.jiahan.smartcamera.repository

interface RemoteConfigRepository {
    suspend fun fetchAndActivateConfig(): Boolean
    fun getStorageUrl(): String
    fun getStorageFolderName(): String
}