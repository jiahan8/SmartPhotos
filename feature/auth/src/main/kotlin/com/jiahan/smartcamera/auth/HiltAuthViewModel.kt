package com.jiahan.smartcamera.auth

import com.jiahan.smartcamera.data.datastore.UserPreferencesRepository
import com.jiahan.smartcamera.data.repository.AnalyticsRepository
import com.jiahan.smartcamera.data.repository.AuthRepository
import com.jiahan.smartcamera.data.repository.UserRepository
import com.jiahan.smartcamera.util.ErrorHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * What Hilt instantiates for [AuthViewModel], adding the annotations and nothing else -- the
 * arrangement `HiltExploreViewModel` records the reasons for.
 */
@HiltViewModel
class HiltAuthViewModel @Inject constructor(
    authRepository: AuthRepository,
    userRepository: UserRepository,
    userPreferencesRepository: UserPreferencesRepository,
    analyticsRepository: AnalyticsRepository,
    errorHandler: ErrorHandler,
) : AuthViewModel(
    authRepository,
    userRepository,
    userPreferencesRepository,
    analyticsRepository,
    errorHandler,
)