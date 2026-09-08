package com.jiahan.smartcamera.data.datastore

import kotlinx.coroutines.flow.Flow

/**
 * Data-layer contract for locally persisted user preferences (DataStore).
 *
 * Authentication lives in [com.jiahan.smartcamera.data.repository.AuthRepository].
 * Remote user-profile operations live in [com.jiahan.smartcamera.data.repository.UserRepository].
 */
interface UserPreferencesRepository {
    val userPreferences: Flow<UserPreferences>
    suspend fun setDarkTheme(enabled: Boolean): Result<Unit>
    suspend fun updateLocalUserProfile(username: String, profilePictureUrl: String?): Result<Unit>

    /**
     * Drops the preferences that belong to the signed-in user, leaving the ones that belong to the
     * device.
     *
     * That split is the whole content of this method: `username` and `profilePictureUrl` are the
     * previous account's identity and must not greet the next one, while the theme is a choice
     * made on this device and survives. Called from the sign-out and delete-account paths through
     * `LocalUserDataCleaner`, which owns the full list of what "local user data" means.
     */
    suspend fun clearUserScopedPreferences(): Result<Unit>
}