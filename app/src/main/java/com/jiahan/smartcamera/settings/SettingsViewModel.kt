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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

sealed interface SettingsStatus {
    data object Idle : SettingsStatus
    data object Loading : SettingsStatus
    data class Error(val message: String) : SettingsStatus
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

data class SettingsUiState(
    val status: SettingsStatus = SettingsStatus.Idle,
    val dialogState: SettingsDialogState = SettingsDialogState.None
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val errorHandler: ErrorHandler
) : ViewModel() {

    private val _navigationEvent = Channel<SettingsNavigationEvent>(Channel.BUFFERED)
    val navigationEvent = _navigationEvent.receiveAsFlow()
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

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
            _uiState.update { it.copy(status = SettingsStatus.Loading) }
            val result = authRepository.signOut()
            result.onFailure { e ->
                errorHandler.logError(e)
                _uiState.update {
                    it.copy(
                        status = SettingsStatus.Error(errorHandler.getErrorMessage(e))
                    )
                }
            }
            if (result.isSuccess) {
                delay(AUTH_ACTION_DELAY_MS.milliseconds)
                _navigationEvent.trySend(SettingsNavigationEvent.NavigateToAuth)
                _uiState.update { it.copy(status = SettingsStatus.Idle) }
            }
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            _uiState.update { it.copy(status = SettingsStatus.Loading) }
            val result = authRepository.deleteAccount()
            result.onFailure { e ->
                errorHandler.logError(e)
                _uiState.update {
                    it.copy(
                        status = SettingsStatus.Error(errorHandler.getErrorMessage(e))
                    )
                }
            }
            if (result.isSuccess) {
                delay(AUTH_ACTION_DELAY_MS.milliseconds)
                _navigationEvent.trySend(SettingsNavigationEvent.NavigateToAuth)
                _uiState.update { it.copy(status = SettingsStatus.Idle) }
            }
        }
    }

    fun showLogoutDialog() {
        _uiState.update { it.copy(dialogState = SettingsDialogState.Logout) }
    }

    fun showDeleteAccountDialog() {
        _uiState.update { it.copy(dialogState = SettingsDialogState.DeleteAccount) }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(dialogState = SettingsDialogState.None) }
    }

    fun openLanguageSettings() {
        _navigationEvent.trySend(SettingsNavigationEvent.OpenLanguageSettings)
    }

    fun resetActionError() {
        _uiState.update { it.copy(status = SettingsStatus.Idle) }
    }

}