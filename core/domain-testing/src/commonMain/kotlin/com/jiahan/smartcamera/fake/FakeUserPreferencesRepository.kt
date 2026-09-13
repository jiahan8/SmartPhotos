package com.jiahan.smartcamera.fake

import com.jiahan.smartcamera.data.datastore.UserPreferences
import com.jiahan.smartcamera.data.datastore.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory [UserPreferencesRepository] test double backed by a [MutableStateFlow] so that updates
 * propagate to collectors immediately — no DataStore / disk I/O involved.
 */
class FakeUserPreferencesRepository(
    initial: UserPreferences = UserPreferences(
        isDarkTheme = false,
        username = "",
        profilePictureUrl = null
    )
) : UserPreferencesRepository {

    private val _preferences = MutableStateFlow(initial)
    override val userPreferences: Flow<UserPreferences> = _preferences.asStateFlow()

    var setDarkThemeResult: Result<Unit> = Result.success(Unit)

    override suspend fun setDarkTheme(enabled: Boolean): Result<Unit> {
        if (setDarkThemeResult.isSuccess) {
            _preferences.value = _preferences.value.copy(isDarkTheme = enabled)
        }
        return setDarkThemeResult
    }

    override suspend fun updateLocalUserProfile(
        username: String,
        profilePictureUrl: String?
    ): Result<Unit> {
        _preferences.value = _preferences.value.copy(
            username = username,
            profilePictureUrl = profilePictureUrl
        )
        return Result.success(Unit)
    }

    override suspend fun clearUserScopedPreferences(): Result<Unit> {
        // Mirrors the real implementation: the user-scoped fields reset, the theme is left alone.
        _preferences.value = _preferences.value.copy(username = "", profilePictureUrl = null)
        return Result.success(Unit)
    }
}