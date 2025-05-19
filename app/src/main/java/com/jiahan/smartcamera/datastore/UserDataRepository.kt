package com.jiahan.smartcamera.datastore

import kotlinx.coroutines.flow.Flow

interface UserDataRepository {
    val userPreferencesFlow: Flow<UserPreferences>
    suspend fun updateIsDarkTheme(isDarkTheme: Boolean)
}