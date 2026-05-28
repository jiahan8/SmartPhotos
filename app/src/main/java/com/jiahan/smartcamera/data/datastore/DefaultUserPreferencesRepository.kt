package com.jiahan.smartcamera.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jiahan.smartcamera.util.safeCall
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

private const val USER_PREFERENCES_NAME = "user_preferences"
private val Context.dataStore by preferencesDataStore(name = USER_PREFERENCES_NAME)

private object PreferencesKeys {
    val IS_DARK_THEME = booleanPreferencesKey(name = "is_dark_theme")
    val USERNAME = stringPreferencesKey(name = "username")
    val PROFILE_PICTURE = stringPreferencesKey(name = "profile_picture")
}

class DefaultUserPreferencesRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : UserPreferencesRepository {
    override val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data
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
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_DARK_THEME] = isDarkTheme
        }
    }

    override suspend fun updateLocalUserProfile(
        username: String,
        profilePictureUrl: String?
    ): Result<Unit> = safeCall {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.USERNAME] = username
            profilePictureUrl?.let { preferences[PreferencesKeys.PROFILE_PICTURE] = it }
                ?: preferences.remove(PreferencesKeys.PROFILE_PICTURE)
        }
    }
}