package com.jiahan.smartcamera.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiahan.smartcamera.data.repository.AnalyticsRepository
import com.jiahan.smartcamera.data.repository.AuthRepository
import com.jiahan.smartcamera.data.datastore.UserPreferencesRepository
import com.jiahan.smartcamera.util.AppConstants.AUTH_ACTION_DELAY_MS
import com.jiahan.smartcamera.util.AppConstants.STATEFLOW_WHILE_SUBSCRIBED_MS
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.ErrorMessage
import com.jiahan.smartcamera.util.ValidationError
import com.jiahan.smartcamera.util.ValidationResult
import com.jiahan.smartcamera.util.toErrorMessage
import com.jiahan.smartcamera.util.validateNewPassword
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
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
    data class Error(val message: ErrorMessage) : SettingsStatus
}

sealed interface SettingsDialogState {
    data object None : SettingsDialogState
    data object Logout : SettingsDialogState
    data object DeleteAccount : SettingsDialogState
    data class ChangePassword(
        val currentPassword: String = "",
        val newPassword: String = "",
        val confirmNewPassword: String = "",
        val isCurrentPasswordVisible: Boolean = false,
        val isNewPasswordVisible: Boolean = false,
        val isConfirmNewPasswordVisible: Boolean = false,
        val newPasswordError: ValidationError? = null,
        val confirmNewPasswordError: ConfirmPasswordError? = null,
    ) : SettingsDialogState
}

/** Why the confirm-password field is rejected, for SettingsScreen to render under it. */
enum class ConfirmPasswordError { EMPTY, MISMATCH }

sealed interface SettingsNavigationEvent {
    data object NavigateToAuth : SettingsNavigationEvent
    data object OpenLanguageSettings : SettingsNavigationEvent
}

sealed interface SettingsChangePasswordEvent {
    data object Success : SettingsChangePasswordEvent
}

data class SettingsUiState(
    val status: SettingsStatus = SettingsStatus.Idle,
    val dialogState: SettingsDialogState = SettingsDialogState.None,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val errorHandler: ErrorHandler
) : ViewModel() {

    private val _navigationEvent = Channel<SettingsNavigationEvent>(Channel.BUFFERED)
    val navigationEvent = _navigationEvent.receiveAsFlow()
    private val _changePasswordEvent =
        MutableSharedFlow<SettingsChangePasswordEvent>(extraBufferCapacity = 1)
    val changePasswordEvent = _changePasswordEvent.asSharedFlow()
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    val isDarkTheme = userPreferencesRepository.userPreferences
        .map { it.isDarkTheme }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATEFLOW_WHILE_SUBSCRIBED_MS),
            initialValue = false
        )

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setDarkTheme(enabled)
                .onFailure(errorHandler::logError)
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
                        status = SettingsStatus.Error(e.toErrorMessage())
                    )
                }
            }
            if (result.isSuccess) {
                analyticsRepository.setUserId(null)
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
                        status = SettingsStatus.Error(e.toErrorMessage())
                    )
                }
            }
            if (result.isSuccess) {
                analyticsRepository.setUserId(null)
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

    fun showChangePasswordDialog() {
        _uiState.update { it.copy(dialogState = SettingsDialogState.ChangePassword()) }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(dialogState = SettingsDialogState.None) }
    }

    fun updateCurrentPassword(text: String) {
        updateChangePasswordDialog { it.copy(currentPassword = text) }
        analyticsRepository.logText(text)
    }

    fun updateNewPassword(text: String) {
        updateChangePasswordDialog {
            it.copy(
                newPassword = text,
                newPasswordError = null,
                confirmNewPasswordError = mismatchError(text, it.confirmNewPassword)
            )
        }
        analyticsRepository.logText(text)
    }

    fun updateConfirmNewPassword(text: String) {
        updateChangePasswordDialog {
            it.copy(
                confirmNewPassword = text,
                confirmNewPasswordError = mismatchError(it.newPassword, text)
            )
        }
        analyticsRepository.logText(text)
    }

    private fun mismatchError(
        newPassword: String,
        confirmNewPassword: String
    ): ConfirmPasswordError? =
        if (confirmNewPassword.isNotEmpty() && confirmNewPassword != newPassword)
            ConfirmPasswordError.MISMATCH
        else null

    fun updateCurrentPasswordVisibility(visible: Boolean) {
        updateChangePasswordDialog { it.copy(isCurrentPasswordVisible = visible) }
    }

    fun updateNewPasswordVisibility(visible: Boolean) {
        updateChangePasswordDialog { it.copy(isNewPasswordVisible = visible) }
    }

    fun updateConfirmNewPasswordVisibility(visible: Boolean) {
        updateChangePasswordDialog { it.copy(isConfirmNewPasswordVisible = visible) }
    }

    private fun updateChangePasswordDialog(
        transform: (SettingsDialogState.ChangePassword) -> SettingsDialogState.ChangePassword
    ) {
        _uiState.update { state ->
            val dialog =
                state.dialogState as? SettingsDialogState.ChangePassword ?: return@update state
            state.copy(dialogState = transform(dialog))
        }
    }

    fun changePassword() {
        val dialog = _uiState.value.dialogState as? SettingsDialogState.ChangePassword ?: return
        val newPasswordError =
            (validateNewPassword(
                dialog.newPassword,
                requireNonBlank = true
            ) as? ValidationResult.Error)?.reason
        val confirmError = when {
            dialog.confirmNewPassword.isBlank() -> ConfirmPasswordError.EMPTY
            dialog.confirmNewPassword != dialog.newPassword -> ConfirmPasswordError.MISMATCH
            else -> null
        }
        if (newPasswordError != null || confirmError != null) {
            updateChangePasswordDialog {
                it.copy(
                    newPasswordError = newPasswordError,
                    confirmNewPasswordError = confirmError
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(status = SettingsStatus.Loading) }
            val result = authRepository.changePassword(
                currentPassword = dialog.currentPassword,
                newPassword = dialog.newPassword,
            )
            result.onFailure { e ->
                errorHandler.logError(e)
                _uiState.update {
                    it.copy(status = SettingsStatus.Error(e.toErrorMessage()))
                }
            }
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        status = SettingsStatus.Idle,
                        dialogState = SettingsDialogState.None,
                    )
                }
                _changePasswordEvent.tryEmit(SettingsChangePasswordEvent.Success)
            }
        }
    }

    fun openLanguageSettings() {
        _navigationEvent.trySend(SettingsNavigationEvent.OpenLanguageSettings)
    }

    fun dismissError() {
        _uiState.update { it.copy(status = SettingsStatus.Idle) }
    }

}