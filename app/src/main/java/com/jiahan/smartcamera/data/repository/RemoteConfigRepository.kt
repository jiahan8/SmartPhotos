package com.jiahan.smartcamera.data.repository

interface RemoteConfigRepository {
    suspend fun fetchAndActivateConfig()
    fun getStorageUrl(): String
    fun getStorageFolderName(): String
}