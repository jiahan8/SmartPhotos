package com.jiahan.smartcamera.data.datastore

import kotlinx.coroutines.flow.Flow

/**
 * Data-layer contract for locally persisted user preferences (DataStore).
 *
 * Authentication lives in [com.jiahan.smartcamera.data.repository.AuthRepository].
 * Remote user-profile operations live in [com.jiahan.smartcamera.data.repository.UserRepository].
 */
interface UserPreferencesRepository {
    val userPreferencesFlow: Flow<UserPreferences>
    suspend fun updateDarkThemeVisibility(isDarkTheme: Boolean): Result<Unit>
    suspend fun updateLocalUserProfile(username: String, profilePictureUrl: String?): Result<Unit>
}