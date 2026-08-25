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
import com.jiahan.smartcamera.util.usernameErrorMessageResId
import com.jiahan.smartcamera.util.validateDisplayName
import com.jiahan.smartcamera.util.validateUsername
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AuthStatus {
    data object Idle : AuthStatus
    data object Loading : AuthStatus
    data class Error(val message: String) : AuthStatus
    data class Info(val message: String) : AuthStatus
}

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val displayName: String = "",
    val username: String = "",
    val passwordVisible: Boolean = false,
    val isLoginMode: Boolean = true,
    val status: AuthStatus = AuthStatus.Idle,
    val showResendButton: Boolean = false
)

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

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState = _uiState.asStateFlow()

    private val _navigationEvent = Channel<AuthNavigationEvent>(Channel.BUFFERED)
    val navigationEvent = _navigationEvent.receiveAsFlow()

    fun updateEmailText(text: String) {
        _uiState.update { it.copy(email = text) }
        analyticsRepository.logTextCustomEvent(text)
    }

    fun updatePasswordText(text: String) {
        _uiState.update { it.copy(password = text) }
        analyticsRepository.logTextCustomEvent(text)
    }

    fun updateDisplayNameText(text: String) {
        _uiState.update { it.copy(displayName = text) }
        analyticsRepository.logDisplayNameCustomEvent(text)
    }

    fun updateUsernameText(text: String) {
        _uiState.update { it.copy(username = text) }
        analyticsRepository.logUsernameCustomEvent(text)
    }

    fun updatePasswordVisibility(showPassword: Boolean) {
        _uiState.update { it.copy(passwordVisible = showPassword) }
    }

    fun toggleAuthMode() {
        _uiState.update {
            it.copy(
                isLoginMode = !it.isLoginMode,
                email = "",
                password = "",
                displayName = "",
                username = "",
                status = AuthStatus.Idle,
                showResendButton = false
            )
        }
    }

    fun submit() {
        if (_uiState.value.isLoginMode) signIn() else signUp()
    }

    fun signIn() {
        val trimmedEmail = _uiState.value.email.trim()
        val currentPassword = _uiState.value.password
        if (trimmedEmail.isBlank() || currentPassword.isBlank()) {
            _uiState.update {
                it.copy(
                    status = AuthStatus.Error(resourceProvider.getString(R.string.email_password_empty)),
                    showResendButton = false
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(status = AuthStatus.Loading, showResendButton = false) }

            authRepository.signIn(trimmedEmail, currentPassword)
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
                                userRepository.registerForPushNotifications()
                                    .onFailure { e -> errorHandler.logError(e) }
                                analyticsRepository.setUserId(authRepository.currentUserId)
                                _navigationEvent.trySend(AuthNavigationEvent.NavigateToHome)
                                _uiState.update {
                                    it.copy(status = AuthStatus.Idle, showResendButton = false)
                                }
                            } else {
                                _uiState.update {
                                    it.copy(
                                        status = AuthStatus.Error(
                                            message = resourceProvider.getString(R.string.email_not_verified)
                                        ),
                                        showResendButton = true
                                    )
                                }
                            }
                        }
                        .onFailure { e ->
                            errorHandler.logError(e)
                            _uiState.update {
                                it.copy(
                                    status = AuthStatus.Error(errorHandler.getErrorMessage(e)),
                                    showResendButton = false
                                )
                            }
                        }
                }
                .onFailure { e ->
                    errorHandler.logError(e)
                    _uiState.update {
                        it.copy(
                            status = AuthStatus.Error(message = errorHandler.getErrorMessage(e)),
                            showResendButton = false
                        )
                    }
                }
        }
    }

    fun signUp() {
        val trimmedEmail = _uiState.value.email.trim()
        val currentPassword = _uiState.value.password
        val trimmedDisplayName = _uiState.value.displayName.trim()
        val trimmedUsername = _uiState.value.username.trim()

        if (trimmedEmail.isBlank() || currentPassword.isBlank()) {
            _uiState.update {
                it.copy(
                    status = AuthStatus.Error(resourceProvider.getString(R.string.email_password_empty)),
                    showResendButton = false
                )
            }
            return
        }
        if (trimmedDisplayName.isBlank() || trimmedUsername.isBlank()) {
            _uiState.update {
                it.copy(
                    status = AuthStatus.Error(resourceProvider.getString(R.string.all_fields_required)),
                    showResendButton = false
                )
            }
            return
        }
        when (val validationResult = validateDisplayName(trimmedDisplayName)) {
            is ValidationResult.Error -> {
                _uiState.update {
                    it.copy(
                        status = AuthStatus.Error(resourceProvider.getString(validationResult.messageResId)),
                        showResendButton = false
                    )
                }
                return
            }

            else -> {}
        }
        when (val validationResult = validateUsername(trimmedUsername)) {
            is ValidationResult.Error -> {
                _uiState.update {
                    it.copy(
                        status = AuthStatus.Error(resourceProvider.getString(validationResult.messageResId)),
                        showResendButton = false
                    )
                }
                return
            }

            else -> {}
        }

        viewModelScope.launch {
            _uiState.update { it.copy(status = AuthStatus.Loading, showResendButton = false) }

            authRepository.isUsernameAvailable(trimmedUsername)
                .onSuccess { available ->
                    if (!available) {
                        _uiState.update {
                            it.copy(
                                status = AuthStatus.Error(resourceProvider.getString(R.string.username_not_available)),
                                showResendButton = false
                            )
                        }
                        return@onSuccess
                    }
                    authRepository.signUp(
                        email = trimmedEmail,
                        password = currentPassword,
                        displayName = trimmedDisplayName,
                        username = trimmedUsername
                    ).onSuccess {
                        _uiState.update {
                            it.copy(
                                status = AuthStatus.Info(
                                    message = resourceProvider.getString(R.string.verification_email_sent)
                                ),
                                showResendButton = true
                            )
                        }
                    }.onFailure { e ->
                        errorHandler.logError(e)
                        val message = usernameErrorMessageResId(e)?.let(resourceProvider::getString)
                            ?: errorHandler.getErrorMessage(e)
                        _uiState.update {
                            it.copy(
                                status = AuthStatus.Error(message = message),
                                showResendButton = false
                            )
                        }
                    }
                }
                .onFailure { e ->
                    errorHandler.logError(e)
                    _uiState.update {
                        it.copy(
                            status = AuthStatus.Error(errorHandler.getErrorMessage(e)),
                            showResendButton = false
                        )
                    }
                }
        }
    }

    fun resetPassword() {
        val trimmedEmail = _uiState.value.email.trim()
        if (trimmedEmail.isBlank()) {
            _uiState.update {
                it.copy(
                    status = AuthStatus.Error(resourceProvider.getString(R.string.enter_email)),
                    showResendButton = false
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(status = AuthStatus.Loading, showResendButton = false) }

            authRepository.isEmailRegistered(trimmedEmail)
                .onSuccess { registered ->
                    if (!registered) {
                        _uiState.update {
                            it.copy(
                                status = AuthStatus.Error(resourceProvider.getString(R.string.email_not_registered)),
                                showResendButton = false
                            )
                        }
                        return@onSuccess
                    }
                    authRepository.resetPassword(trimmedEmail)
                        .onSuccess {
                            _uiState.update {
                                it.copy(
                                    status = AuthStatus.Info(resourceProvider.getString(R.string.password_reset_email_sent)),
                                    showResendButton = false
                                )
                            }
                        }
                        .onFailure { e ->
                            errorHandler.logError(e)
                            _uiState.update {
                                it.copy(
                                    status = AuthStatus.Error(
                                        message = errorHandler.getErrorMessage(e)
                                    ),
                                    showResendButton = false
                                )
                            }
                        }
                }
                .onFailure { e ->
                    errorHandler.logError(e)
                    _uiState.update {
                        it.copy(
                            status = AuthStatus.Error(errorHandler.getErrorMessage(e)),
                            showResendButton = false
                        )
                    }
                }
        }
    }

    fun resendVerificationEmail() {
        viewModelScope.launch {
            _uiState.update { it.copy(status = AuthStatus.Loading, showResendButton = false) }
            authRepository.sendEmailVerification()
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            status = AuthStatus.Info(message = resourceProvider.getString(R.string.verification_email_resent)),
                            showResendButton = true
                        )
                    }
                }
                .onFailure { e ->
                    errorHandler.logError(e)
                    _uiState.update {
                        it.copy(
                            status = AuthStatus.Error(errorHandler.getErrorMessage(e)),
                            showResendButton = false
                        )
                    }
                }
        }
    }
}