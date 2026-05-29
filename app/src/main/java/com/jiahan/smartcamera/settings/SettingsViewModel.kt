package com.jiahan.smartcamera.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiahan.smartcamera.data.repository.AuthRepository
import com.jiahan.smartcamera.data.datastore.UserPreferencesRepository
import com.jiahan.smartcamera.util.AppConstants.AUTH_ACTION_DELAY_MS
import com.jiahan.smartcamera.util.AppConstants.STATEFLOW_WHILE_SUBSCRIBED_MS
import com.jiahan.smartcamera.util.ErrorHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SettingsUiState {
    data object Idle : SettingsUiState
    data object Loading : SettingsUiState
    data class Error(val message: String) : SettingsUiState
}

sealed interface SettingsDialogState {
    data object None : SettingsDialogState
    data object Logout : SettingsDialogState
    data object DeleteAccount : SettingsDialogState
}

sealed interface SettingsNavigationEvent {
    data object NavigateToAuth : SettingsNavigationEvent
    data object OpenLanguageSettings : SettingsNavigationEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val errorHandler: ErrorHandler
) : ViewModel() {

    private val _navigationEvent = Channel<SettingsNavigationEvent>(Channel.BUFFERED)
    val navigationEvent = _navigationEvent.receiveAsFlow()
    private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Idle)
    val uiState = _uiState.asStateFlow()
    private val _dialogState = MutableStateFlow<SettingsDialogState>(SettingsDialogState.None)
    val dialogState = _dialogState.asStateFlow()

    val isDarkTheme = userPreferencesRepository.userPreferencesFlow
        .map { it.isDarkTheme }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATEFLOW_WHILE_SUBSCRIBED_MS),
            initialValue = false
        )

    fun updateDarkThemeVisibility(showDarkTheme: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.updateDarkThemeVisibility(showDarkTheme)
                .onFailure { e -> errorHandler.logError(e) }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            _uiState.value = SettingsUiState.Loading
            val result = authRepository.signOut()
            result.onFailure { e ->
                errorHandler.logError(e)
                _uiState.value = SettingsUiState.Error(errorHandler.getErrorMessage(e))
            }
            if (result.isSuccess) {
                delay(AUTH_ACTION_DELAY_MS)
                _navigationEvent.trySend(SettingsNavigationEvent.NavigateToAuth)
                _uiState.value = SettingsUiState.Idle
            }
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            _uiState.value = SettingsUiState.Loading
            val result = authRepository.deleteAccount()
            result.onFailure { e ->
                errorHandler.logError(e)
                _uiState.value = SettingsUiState.Error(errorHandler.getErrorMessage(e))
            }
            if (result.isSuccess) {
                delay(AUTH_ACTION_DELAY_MS)
                _navigationEvent.trySend(SettingsNavigationEvent.NavigateToAuth)
                _uiState.value = SettingsUiState.Idle
            }
        }
    }

    fun showLogoutDialog() {
        _dialogState.value = SettingsDialogState.Logout
    }

    fun showDeleteAccountDialog() {
        _dialogState.value = SettingsDialogState.DeleteAccount
    }

    fun dismissDialog() {
        _dialogState.value = SettingsDialogState.None
    }

    fun openLanguageSettings() {
        _navigationEvent.trySend(SettingsNavigationEvent.OpenLanguageSettings)
    }

    fun resetActionError() {
        _uiState.value = SettingsUiState.Idle
    }

}