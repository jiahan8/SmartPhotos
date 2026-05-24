package com.jiahan.smartcamera.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiahan.smartcamera.datastore.ProfileRepository
import com.jiahan.smartcamera.util.AppConstants.AUTH_ACTION_DELAY_MS
import com.jiahan.smartcamera.util.AppConstants.STATEFLOW_WHILE_SUBSCRIBED_MS
import com.jiahan.smartcamera.util.ErrorHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SettingsUiState {
    data object Idle : SettingsUiState
    data object Loading : SettingsUiState
    data class Error(val message: String) : SettingsUiState
}

sealed class DialogState {
    object None : DialogState()
    object Logout : DialogState()
    object DeleteAccount : DialogState()
}

sealed class NavigationEvent {
    object NavigateToAuth : NavigationEvent()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val errorHandler: ErrorHandler
) : ViewModel() {

    private val _navigationEvent = MutableStateFlow<NavigationEvent?>(null)
    val navigationEvent = _navigationEvent.asStateFlow()
    private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Idle)
    val uiState = _uiState.asStateFlow()
    private val _dialogState = MutableStateFlow<DialogState>(DialogState.None)
    val dialogState = _dialogState.asStateFlow()

    val isDarkTheme = profileRepository.userPreferencesFlow
        .map { it.isDarkTheme }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATEFLOW_WHILE_SUBSCRIBED_MS),
            initialValue = false
        )

    fun updateDarkThemeVisibility(showDarkTheme: Boolean) {
        viewModelScope.launch {
            profileRepository.updateDarkThemeVisibility(showDarkTheme)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            _uiState.value = SettingsUiState.Loading
            val result = profileRepository.signOut()
            result.onFailure { e ->
                errorHandler.logError(e)
                _uiState.value = SettingsUiState.Error(errorHandler.getErrorMessage(e))
            }
            if (result.isSuccess) {
                delay(AUTH_ACTION_DELAY_MS)
                _navigationEvent.value = NavigationEvent.NavigateToAuth
                _uiState.value = SettingsUiState.Idle
            }
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            _uiState.value = SettingsUiState.Loading
            val result = profileRepository.deleteAccount()
            result.onFailure { e ->
                errorHandler.logError(e)
                _uiState.value = SettingsUiState.Error(errorHandler.getErrorMessage(e))
            }
            if (result.isSuccess) {
                delay(AUTH_ACTION_DELAY_MS)
                _navigationEvent.value = NavigationEvent.NavigateToAuth
                _uiState.value = SettingsUiState.Idle
            }
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

    fun resetActionError() {
        _uiState.value = SettingsUiState.Idle
    }

}