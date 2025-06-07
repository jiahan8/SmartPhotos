package com.jiahan.smartcamera.data.repository

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseRemoteConfigRepository @Inject constructor(
    private val remoteConfig: FirebaseRemoteConfig
) : RemoteConfigRepository {

    companion object {
        private const val STORAGE_URL_KEY = "firebase_storage_url"
        private const val STORAGE_FOLDER_KEY = "firebase_storage_folder"
        private const val STORAGE_CACHE_FOLDER_KEY = "firebase_storage_cache_folder"
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
                STORAGE_FOLDER_KEY to "default_value",
                STORAGE_CACHE_FOLDER_KEY to "default_value"
            )
        )
    }

    override suspend fun fetchAndActivateConfig() {
        remoteConfig.fetchAndActivate().await()
    }

    override fun getStorageUrl(): String = remoteConfig.getString(STORAGE_URL_KEY)

    override fun getStorageFolderName(): String = remoteConfig.getString(STORAGE_FOLDER_KEY)

    override fun getStorageCacheFolderName(): String =
        remoteConfig.getString(STORAGE_CACHE_FOLDER_KEY)
}