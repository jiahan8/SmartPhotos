package com.jiahan.smartcamera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiahan.smartcamera.datastore.ProfileRepository
import com.jiahan.smartcamera.datastore.UserDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    profileRepository: ProfileRepository,
    userDataRepository: UserDataRepository
) : ViewModel() {

    val isDarkTheme = profileRepository.userPreferencesFlow
        .map { it.isDarkTheme }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val startDestination =
        if (userDataRepository.firebaseUser != null && userDataRepository.isEmailVerified())
            Screen.Home.route
        else
            Screen.Auth.route
}