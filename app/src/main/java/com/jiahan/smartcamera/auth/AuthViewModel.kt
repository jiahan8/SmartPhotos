package com.jiahan.smartcamera.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.data.repository.AnalyticsRepository
import com.jiahan.smartcamera.data.repository.AuthRepository
import com.jiahan.smartcamera.data.repository.UserRepository
import com.jiahan.smartcamera.data.datastore.UserPreferencesRepository
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.ResourceProvider
import com.jiahan.smartcamera.util.ValidationResult
import com.jiahan.smartcamera.util.validateDisplayName
import com.jiahan.smartcamera.util.validateUsername
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState
    data class Error(val message: String, val showResendButton: Boolean = false) : AuthUiState
    data class Info(val message: String, val showResendButton: Boolean = false) : AuthUiState
}

sealed interface AuthNavigationEvent {
    data object NavigateToHome : AuthNavigationEvent
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val resourceProvider: ResourceProvider,
    private val errorHandler: ErrorHandler
) : ViewModel() {

    private val _email = MutableStateFlow("")
    val email = _email.asStateFlow()
    private val _password = MutableStateFlow("")
    val password = _password.asStateFlow()
    private val _displayName = MutableStateFlow("")
    val displayName = _displayName.asStateFlow()
    private val _username = MutableStateFlow("")
    val username = _username.asStateFlow()

    private val _isPasswordVisible = MutableStateFlow(false)
    val passwordVisible = _isPasswordVisible.asStateFlow()
    private val _isLoginMode = MutableStateFlow(true)
    val isLoginMode = _isLoginMode.asStateFlow()

    private val _authUiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val authUiState = _authUiState.asStateFlow()

    private val _navigationEvent = Channel<AuthNavigationEvent>(Channel.BUFFERED)
    val navigationEvent = _navigationEvent.receiveAsFlow()

    fun updateEmailText(text: String) {
        _email.value = text
    }

    fun updatePasswordText(text: String) {
        _password.value = text
        analyticsRepository.logTextCustomEvent(text)
    }

    fun updateDisplayNameText(text: String) {
        _displayName.value = text
    }

    fun updateUsernameText(text: String) {
        _username.value = text
    }

    fun updatePasswordVisibility(showPassword: Boolean) {
        _isPasswordVisible.value = showPassword
    }

    fun toggleAuthMode() {
        _isLoginMode.value = !_isLoginMode.value
        clearFields()
        _authUiState.value = AuthUiState.Idle
    }

    fun submit() {
        if (_isLoginMode.value) signIn() else signUp()
    }

    private fun clearFields() {
        _email.value = ""
        _password.value = ""
        _displayName.value = ""
        _username.value = ""
    }

    fun signIn() {
        val trimmedEmail = email.value.trim()
        if (trimmedEmail.isBlank() || password.value.isBlank()) {
            _authUiState.value = AuthUiState.Error(
                resourceProvider.getString(R.string.email_password_empty)
            )
            return
        }

        viewModelScope.launch {
            _authUiState.value = AuthUiState.Loading

            authRepository.signIn(trimmedEmail, password.value)
                .onSuccess {
                    authRepository.checkEmailVerified()
                        .onSuccess { verified ->
                            if (verified) {
                                userRepository.getUser()
                                    .onSuccess { user ->
                                        userPreferencesRepository.updateLocalUserProfile(
                                            username = user?.username ?: "",
                                            profilePictureUrl = user?.profilePicture,
                                        )
                                    }
                                    .onFailure { e -> errorHandler.logError(e) }
                                _navigationEvent.trySend(AuthNavigationEvent.NavigateToHome)
                                _authUiState.value = AuthUiState.Idle
                            } else {
                                _authUiState.value = AuthUiState.Error(
                                    message = resourceProvider.getString(R.string.email_not_verified),
                                    showResendButton = true
                                )
                            }
                        }
                        .onFailure { e ->
                            errorHandler.logError(e)
                            _authUiState.value = AuthUiState.Error(errorHandler.getErrorMessage(e))
                        }
                }
                .onFailure { e ->
                    errorHandler.logError(e)
                    _authUiState.value =
                        AuthUiState.Error(message = errorHandler.getErrorMessage(e))
                }
        }
    }

    fun signUp() {
        val trimmedEmail = email.value.trim()
        val trimmedDisplayName = displayName.value.trim()
        val trimmedUsername = username.value.trim()

        if (trimmedEmail.isBlank() || password.value.isBlank()) {
            _authUiState.value = AuthUiState.Error(
                resourceProvider.getString(R.string.email_password_empty)
            )
            return
        }
        if (trimmedDisplayName.isBlank() || trimmedUsername.isBlank()) {
            _authUiState.value = AuthUiState.Error(
                resourceProvider.getString(R.string.all_fields_required)
            )
            return
        }
        when (val r = validateDisplayName(trimmedDisplayName)) {
            is ValidationResult.Error -> {
                _authUiState.value = AuthUiState.Error(resourceProvider.getString(r.messageResId))
                return
            }

            else -> {}
        }
        when (val r = validateUsername(trimmedUsername)) {
            is ValidationResult.Error -> {
                _authUiState.value = AuthUiState.Error(resourceProvider.getString(r.messageResId))
                return
            }

            else -> {}
        }

        viewModelScope.launch {
            _authUiState.value = AuthUiState.Loading

            authRepository.isUsernameAvailable(trimmedUsername)
                .onSuccess { available ->
                    if (!available) {
                        _authUiState.value = AuthUiState.Error(
                            resourceProvider.getString(R.string.username_not_available)
                        )
                        return@onSuccess
                    }
                    authRepository.signUp(
                        email = trimmedEmail,
                        password = password.value,
                        displayName = trimmedDisplayName,
                        username = trimmedUsername
                    ).onSuccess {
                        _authUiState.value = AuthUiState.Info(
                            message = resourceProvider.getString(R.string.verification_email_sent),
                            showResendButton = true
                        )
                    }.onFailure { e ->
                        errorHandler.logError(e)
                        _authUiState.value = AuthUiState.Error(
                            message = errorHandler.getErrorMessage(e)
                        )
                    }
                }
                .onFailure { e ->
                    errorHandler.logError(e)
                    _authUiState.value = AuthUiState.Error(errorHandler.getErrorMessage(e))
                }
        }
    }

    fun resetPassword() {
        val trimmedEmail = email.value.trim()
        if (trimmedEmail.isBlank()) {
            _authUiState.value = AuthUiState.Error(
                resourceProvider.getString(R.string.enter_email)
            )
            return
        }

        viewModelScope.launch {
            _authUiState.value = AuthUiState.Loading

            authRepository.isEmailRegistered(trimmedEmail)
                .onSuccess { registered ->
                    if (!registered) {
                        _authUiState.value = AuthUiState.Error(
                            resourceProvider.getString(R.string.email_not_registered)
                        )
                        return@onSuccess
                    }
                    authRepository.resetPassword(trimmedEmail)
                        .onSuccess {
                            _authUiState.value = AuthUiState.Info(
                                resourceProvider.getString(R.string.password_reset_email_sent)
                            )
                        }
                        .onFailure { e ->
                            errorHandler.logError(e)
                            _authUiState.value = AuthUiState.Error(
                                message = errorHandler.getErrorMessage(e)
                            )
                        }
                }
                .onFailure { e ->
                    errorHandler.logError(e)
                    _authUiState.value = AuthUiState.Error(errorHandler.getErrorMessage(e))
                }
        }
    }

    fun resendVerificationEmail() {
        viewModelScope.launch {
            _authUiState.value = AuthUiState.Loading
            authRepository.sendEmailVerification()
                .onSuccess {
                    _authUiState.value = AuthUiState.Info(
                        message = resourceProvider.getString(R.string.verification_email_resent),
                        showResendButton = true
                    )
                }
                .onFailure { e ->
                    errorHandler.logError(e)
                    _authUiState.value = AuthUiState.Error(errorHandler.getErrorMessage(e))
                }
        }
    }
}