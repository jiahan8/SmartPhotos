package com.jiahan.smartcamera.data.repository

import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseRemoteConfigRepository @Inject constructor() : RemoteConfigRepository {
    private val remoteConfig = Firebase.remoteConfig

    companion object {
        private const val STORAGE_URL_KEY = "firebase_storage_url"
        private const val STORAGE_FOLDER_NAME_KEY = "firebase_storage_folder_name"
        private const val FETCH_INTERVAL_RELEASE = 3600L
    }

    init {
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = FETCH_INTERVAL_RELEASE // for release
        }

        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.setDefaultsAsync(
            mapOf(
                STORAGE_URL_KEY to "default_value",
                STORAGE_FOLDER_NAME_KEY to "default_value"
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

    override fun getStorageUrl(): String = remoteConfig.getString(STORAGE_URL_KEY)

    override fun getStorageFolderName(): String = remoteConfig.getString(STORAGE_FOLDER_NAME_KEY)
}