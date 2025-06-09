package com.jiahan.smartcamera.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiahan.smartcamera.datastore.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _navigationEvent = MutableStateFlow<NavigationEvent?>(null)
    val navigationEvent = _navigationEvent.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _dialogState = MutableStateFlow<DialogState>(DialogState.None)
    val dialogState = _dialogState.asStateFlow()

    val isDarkTheme = profileRepository.userPreferencesFlow
        .map { it.isDarkTheme }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    fun updateIsDarkTheme(isDarkTheme: Boolean) {
        viewModelScope.launch {
            profileRepository.updateIsDarkTheme(isDarkTheme)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            _isLoading.value = true
            profileRepository.signOut()
            delay(1000)
            _navigationEvent.value = NavigationEvent.NavigateToAuth
            _isLoading.value = false
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            _isLoading.value = true
            profileRepository.deleteAccount()
            delay(1000)
            _navigationEvent.value = NavigationEvent.NavigateToAuth
            _isLoading.value = false
        }
    }

    fun showLogoutDialog() {
        _dialogState.value = DialogState.Logout
    }

    fun showDeleteAccountDialog() {
        _dialogState.value = DialogState.DeleteAccount
    }

    fun dismissDialog() {
        _dialogState.value = DialogState.None
    }

    fun navigationEventConsumed() {
        _navigationEvent.value = null
    }

    sealed class DialogState {
        object None : DialogState()
        object Logout : DialogState()
        object DeleteAccount : DialogState()
    }

    sealed class NavigationEvent {
        object NavigateToAuth : NavigationEvent()
    }
}