package com.jiahan.smartcamera.profile

import com.jiahan.smartcamera.data.datastore.UserPreferencesRepository
import com.jiahan.smartcamera.data.repository.AnalyticsRepository
import com.jiahan.smartcamera.data.repository.AuthRepository
import com.jiahan.smartcamera.data.repository.MediaCaptureRepository
import com.jiahan.smartcamera.data.repository.MediaUploadRepository
import com.jiahan.smartcamera.data.repository.UserRepository
import com.jiahan.smartcamera.util.ErrorHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * What Hilt instantiates for [ProfileViewModel], adding the annotations and nothing else --
 * `HiltExploreViewModel` records why. Profile has no route argument to decode.
 */
@HiltViewModel
class HiltProfileViewModel @Inject constructor(
    userRepository: UserRepository,
    authRepository: AuthRepository,
    userPreferencesRepository: UserPreferencesRepository,
    mediaCaptureRepository: MediaCaptureRepository,
    mediaUploadRepository: MediaUploadRepository,
    analyticsRepository: AnalyticsRepository,
    errorHandler: ErrorHandler,
) : ProfileViewModel(
    userRepository,
    authRepository,
    userPreferencesRepository,
    mediaCaptureRepository,
    mediaUploadRepository,
    analyticsRepository,
    errorHandler,
)