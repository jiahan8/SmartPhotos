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
        profilePicture = null
    )
) : UserPreferencesRepository {

    private val _preferences = MutableStateFlow(initial)
    override val userPreferencesFlow: Flow<UserPreferences> = _preferences.asStateFlow()

    var updateDarkThemeResult: Result<Unit> = Result.success(Unit)

    override suspend fun updateDarkThemeVisibility(isDarkTheme: Boolean): Result<Unit> {
        if (updateDarkThemeResult.isSuccess) {
            _preferences.value = _preferences.value.copy(isDarkTheme = isDarkTheme)
        }
        return updateDarkThemeResult
    }

    override suspend fun updateLocalUserProfile(
        username: String,
        profilePictureUrl: String?
    ): Result<Unit> {
        _preferences.value = _preferences.value.copy(
            username = username,
            profilePicture = profilePictureUrl
        )
        return Result.success(Unit)
    }
}