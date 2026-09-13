package com.jiahan.smartcamera.settings

import com.jiahan.smartcamera.data.datastore.UserPreferencesRepository
import com.jiahan.smartcamera.data.repository.AnalyticsRepository
import com.jiahan.smartcamera.data.repository.AuthRepository
import com.jiahan.smartcamera.util.ErrorHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * What Hilt instantiates for [SettingsViewModel], adding the annotations and nothing else -- the
 * arrangement `HiltExploreViewModel` records the reasons for.
 */
@HiltViewModel
class HiltSettingsViewModel @Inject constructor(
    authRepository: AuthRepository,
    analyticsRepository: AnalyticsRepository,
    userPreferencesRepository: UserPreferencesRepository,
    errorHandler: ErrorHandler,
) : SettingsViewModel(authRepository, analyticsRepository, userPreferencesRepository, errorHandler)