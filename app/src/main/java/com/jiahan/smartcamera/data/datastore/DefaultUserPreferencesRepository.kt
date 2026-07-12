package com.jiahan.smartcamera.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jiahan.smartcamera.util.safeCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

private object PreferencesKeys {
    val IS_DARK_THEME = booleanPreferencesKey(name = "is_dark_theme")
    val USERNAME = stringPreferencesKey(name = "username")
    val PROFILE_PICTURE = stringPreferencesKey(name = "profile_picture")
}

/**
 * DataStore-backed [UserPreferencesRepository].
 *
 * The [DataStore] is injected (provided by [DataStoreModule]) rather than created from a hard-coded
 * `Context` delegate, so instrumented tests can supply a temp-file DataStore for full isolation.
 */
class DefaultUserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : UserPreferencesRepository {
    override val userPreferencesFlow: Flow<UserPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences ->
            UserPreferences(
                isDarkTheme = preferences[PreferencesKeys.IS_DARK_THEME] == true,
                username = preferences[PreferencesKeys.USERNAME] ?: "",
                profilePicture = preferences[PreferencesKeys.PROFILE_PICTURE]
            )
        }

    override suspend fun updateDarkThemeVisibility(isDarkTheme: Boolean): Result<Unit> = safeCall {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_DARK_THEME] = isDarkTheme
        }
    }

    override suspend fun updateLocalUserProfile(
        username: String,
        profilePictureUrl: String?
    ): Result<Unit> = safeCall {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.USERNAME] = username
            profilePictureUrl?.let { preferences[PreferencesKeys.PROFILE_PICTURE] = it }
                ?: preferences.remove(PreferencesKeys.PROFILE_PICTURE)
        }
    }
}