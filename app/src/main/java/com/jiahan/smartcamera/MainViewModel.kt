package com.jiahan.smartcamera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiahan.smartcamera.datastore.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    profileRepository: ProfileRepository
) : ViewModel() {

    private val _isAppReady = MutableStateFlow(false)
    val isAppReady = _isAppReady.asStateFlow()
    private val _startDestination = MutableStateFlow(Screen.Auth.route)
    val startDestination = _startDestination.asStateFlow()
    private val _showBottomBar = MutableStateFlow(true)
    val showBottomBar = _showBottomBar.asStateFlow()

    val isDarkTheme = profileRepository.userPreferencesFlow
        .map { it.isDarkTheme }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    init {
        viewModelScope.launch {
            _startDestination.value =
                if (profileRepository.firebaseUser != null && profileRepository.isEmailVerified())
                    Screen.Home.route
                else
                    Screen.Auth.route
            _isAppReady.value = true
        }
    }

    fun updateBottomBarVisibility(showBottomBar: Boolean) {
        _showBottomBar.value = showBottomBar
    }
}