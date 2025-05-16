package com.jiahan.smartcamera.repository

import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseRemoteConfigRepository @Inject constructor() : RemoteConfigRepository {
    private val remoteConfig = Firebase.remoteConfig
    private val storageUrlKey = "firebase_storage_url"
    private val storageFolderNameKey = "firebase_storage_folder_name"

    init {
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 3600 // for release
        }

        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.setDefaultsAsync(
            mapOf(
                storageUrlKey to "default_value",
                storageFolderNameKey to "default_value"
            )
        )
    }

    override suspend fun fetchAndActivateConfig(): Boolean {
        return try {
            remoteConfig.fetchAndActivate().await()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun getStorageUrl(): String = remoteConfig.getString(storageUrlKey)

    override fun getStorageFolderName(): String = remoteConfig.getString(storageFolderNameKey)
}