package com.jiahan.smartcamera.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.data.repository.AuthRepository
import com.jiahan.smartcamera.data.datastore.UserPreferencesRepository
import com.jiahan.smartcamera.util.AppConstants.AUTH_ACTION_DELAY_MS
import com.jiahan.smartcamera.util.AppConstants.STATEFLOW_WHILE_SUBSCRIBED_MS
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.ResourceProvider
import com.jiahan.smartcamera.util.ValidationResult
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
    data class Error(val message: String) : SettingsStatus
}

sealed interface SettingsDialogState {
    data object None : SettingsDialogState
    data object Logout : SettingsDialogState
    data object DeleteAccount : SettingsDialogState
    data class ChangePassword(
        val currentPassword: String = "",
        val newPassword: String = "",
        val confirmNewPassword: String = "",
        val currentPasswordVisible: Boolean = false,
        val newPasswordVisible: Boolean = false,
        val confirmNewPasswordVisible: Boolean = false,
        val newPasswordErrorMessage: String? = null,
        val confirmNewPasswordErrorMessage: String? = null,
    ) : SettingsDialogState
}

sealed interface SettingsNavigationEvent {
    data object NavigateToAuth : SettingsNavigationEvent
    data object OpenLanguageSettings : SettingsNavigationEvent
}

sealed interface SettingsChangePasswordEvent {
    data class Success(val message: String) : SettingsChangePasswordEvent
}

data class SettingsUiState(
    val status: SettingsStatus = SettingsStatus.Idle,
    val dialogState: SettingsDialogState = SettingsDialogState.None,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val resourceProvider: ResourceProvider,
    private val errorHandler: ErrorHandler
) : ViewModel() {

    private val _navigationEvent = Channel<SettingsNavigationEvent>(Channel.BUFFERED)
    val navigationEvent = _navigationEvent.receiveAsFlow()
    private val _changePasswordEvent =
        MutableSharedFlow<SettingsChangePasswordEvent>(extraBufferCapacity = 1)
    val changePasswordEvent = _changePasswordEvent.asSharedFlow()
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

    fun showChangePasswordDialog() {
        _uiState.update { it.copy(dialogState = SettingsDialogState.ChangePassword()) }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(dialogState = SettingsDialogState.None) }
    }

    fun updateCurrentPasswordText(text: String) {
        updateChangePasswordDialog { it.copy(currentPassword = text) }
    }

    fun updateNewPasswordText(text: String) {
        updateChangePasswordDialog {
            it.copy(
                newPassword = text,
                newPasswordErrorMessage = null,
                confirmNewPasswordErrorMessage = mismatchError(text, it.confirmNewPassword)
            )
        }
    }

    fun updateConfirmNewPasswordText(text: String) {
        updateChangePasswordDialog {
            it.copy(
                confirmNewPassword = text,
                confirmNewPasswordErrorMessage = mismatchError(it.newPassword, text)
            )
        }
    }

    private fun mismatchError(newPassword: String, confirmNewPassword: String): String? =
        if (confirmNewPassword.isNotEmpty() && confirmNewPassword != newPassword)
            resourceProvider.getString(R.string.passwords_do_not_match)
        else null

    fun updateCurrentPasswordVisibility(visible: Boolean) {
        updateChangePasswordDialog { it.copy(currentPasswordVisible = visible) }
    }

    fun updateNewPasswordVisibility(visible: Boolean) {
        updateChangePasswordDialog { it.copy(newPasswordVisible = visible) }
    }

    fun updateConfirmNewPasswordVisibility(visible: Boolean) {
        updateChangePasswordDialog { it.copy(confirmNewPasswordVisible = visible) }
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
            ) as? ValidationResult.Error)
                ?.let { resourceProvider.getString(it.messageResId) }
        val confirmError = when {
            dialog.confirmNewPassword.isBlank() -> resourceProvider.getString(R.string.password_empty)
            dialog.confirmNewPassword != dialog.newPassword ->
                resourceProvider.getString(R.string.passwords_do_not_match)

            else -> null
        }
        if (newPasswordError != null || confirmError != null) {
            updateChangePasswordDialog {
                it.copy(
                    newPasswordErrorMessage = newPasswordError,
                    confirmNewPasswordErrorMessage = confirmError
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
                    it.copy(status = SettingsStatus.Error(errorHandler.getErrorMessage(e)))
                }
            }
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        status = SettingsStatus.Idle,
                        dialogState = SettingsDialogState.None,
                    )
                }
                _changePasswordEvent.tryEmit(
                    SettingsChangePasswordEvent.Success(
                        resourceProvider.getString(R.string.change_password_success)
                    )
                )
            }
        }
    }

    fun openLanguageSettings() {
        _navigationEvent.trySend(SettingsNavigationEvent.OpenLanguageSettings)
    }

    fun resetActionError() {
        _uiState.update { it.copy(status = SettingsStatus.Idle) }
    }

}