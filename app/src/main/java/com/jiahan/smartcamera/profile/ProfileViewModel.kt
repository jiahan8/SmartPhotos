package com.jiahan.smartcamera.profile

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
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository
) : ViewModel() {

    val isDarkTheme = profileRepository.userPreferencesFlow
        .map { it.isDarkTheme }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    private val _navigationEvent = MutableStateFlow<NavigationEvent?>(null)
    val navigationEvent = _navigationEvent.asStateFlow()

    fun updateIsDarkTheme(isDarkTheme: Boolean) {
        viewModelScope.launch {
            profileRepository.updateIsDarkTheme(isDarkTheme)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            profileRepository.signOut()
            _navigationEvent.value = NavigationEvent.NavigateToAuth
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            profileRepository.deleteAccount()
            _navigationEvent.value = NavigationEvent.NavigateToAuth
        }
    }

    fun navigationEventConsumed() {
        _navigationEvent.value = null
    }

    sealed class NavigationEvent {
        object NavigateToAuth : NavigationEvent()
    }
}