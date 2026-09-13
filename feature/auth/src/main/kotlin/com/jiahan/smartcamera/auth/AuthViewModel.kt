package com.jiahan.smartcamera.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiahan.smartcamera.data.repository.AnalyticsRepository
import com.jiahan.smartcamera.data.repository.AuthRepository
import com.jiahan.smartcamera.data.repository.UserRepository
import com.jiahan.smartcamera.data.datastore.UserPreferencesRepository
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.ErrorMessage
import com.jiahan.smartcamera.util.ValidationError
import com.jiahan.smartcamera.util.ValidationResult
import com.jiahan.smartcamera.util.toErrorMessage
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
    data class Error(val error: AuthError) : AuthStatus
    data class Info(val notice: AuthNotice) : AuthStatus
}

/**
 * Why the last auth action was refused, for AuthScreen to render.
 *
 * [Failed] is a repository failure and [Invalid] a validator's verdict. The objects are this
 * screen's own pre-checks, each named for the one string it shows.
 */
sealed interface AuthError {
    data class Failed(val message: ErrorMessage) : AuthError
    data class Invalid(val reason: ValidationError) : AuthError
    data object EmailPasswordEmpty : AuthError
    data object AllFieldsRequired : AuthError
    data object EmailNotVerified : AuthError
    data object UsernameNotAvailable : AuthError
    data object EmailEmpty : AuthError
    data object EmailNotRegistered : AuthError
}

/** What a successful auth action tells the user to do next. */
enum class AuthNotice {
    VERIFICATION_EMAIL_SENT,
    VERIFICATION_EMAIL_RESENT,
    PASSWORD_RESET_EMAIL_SENT,
}

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val displayName: String = "",
    val username: String = "",
    val isPasswordVisible: Boolean = false,
    val isLoginMode: Boolean = true,
    val status: AuthStatus = AuthStatus.Idle,
    val isResendButtonVisible: Boolean = false
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
    private val errorHandler: ErrorHandler
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState = _uiState.asStateFlow()

    private val _navigationEvent = Channel<AuthNavigationEvent>(Channel.BUFFERED)
    val navigationEvent = _navigationEvent.receiveAsFlow()

    private fun startLoading() {
        _uiState.update { it.copy(status = AuthStatus.Loading, isResendButtonVisible = false) }
    }

    private fun showError(error: AuthError, canResend: Boolean = false) {
        _uiState.update {
            it.copy(status = AuthStatus.Error(error), isResendButtonVisible = canResend)
        }
    }

    private fun showInfo(notice: AuthNotice, canResend: Boolean = false) {
        _uiState.update {
            it.copy(status = AuthStatus.Info(notice), isResendButtonVisible = canResend)
        }
    }

    /** Logs a failure and shows the message it names. */
    private fun fail(e: Throwable) {
        errorHandler.logError(e)
        showError(AuthError.Failed(e.toErrorMessage()))
    }

    /**
     * Shows the first failed validation among [results], if any.
     *
     * Returns whether an error was shown, so a caller reads as `if (showFirstValidationError(...))
     * return`. The validators are pure and cheap -- [validateUsername] already runs on every
     * keystroke in `ProfileViewModel` -- so evaluating them all to pick the first is free.
     */
    private fun showFirstValidationError(vararg results: ValidationResult): Boolean {
        val failure =
            results.filterIsInstance<ValidationResult.Error>().firstOrNull() ?: return false
        showError(AuthError.Invalid(failure.reason))
        return true
    }

    fun updateEmail(text: String) {
        _uiState.update { it.copy(email = text) }
        analyticsRepository.logText(text)
    }

    fun updatePassword(text: String) {
        _uiState.update { it.copy(password = text) }
        analyticsRepository.logText(text)
    }

    fun updateDisplayName(text: String) {
        _uiState.update { it.copy(displayName = text) }
        analyticsRepository.logDisplayName(text)
    }

    fun updateUsername(text: String) {
        _uiState.update { it.copy(username = text) }
        analyticsRepository.logUsername(text)
    }

    fun updatePasswordVisibility(visible: Boolean) {
        _uiState.update { it.copy(isPasswordVisible = visible) }
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
                isResendButtonVisible = false
            )
        }
    }

    fun submit() {
        if (_uiState.value.isLoginMode) signIn() else signUp()
    }

    fun signIn() {
        val trimmedEmail = _uiState.value.email.trim()
        val password = _uiState.value.password
        if (trimmedEmail.isBlank() || password.isBlank()) {
            showError(AuthError.EmailPasswordEmpty)
            return
        }

        viewModelScope.launch {
            startLoading()

            authRepository.signIn(trimmedEmail, password).getOrElse { return@launch fail(it) }
            val verified = authRepository.checkEmailVerified().getOrElse { return@launch fail(it) }
            if (!verified) {
                showError(AuthError.EmailNotVerified, canResend = true)
                return@launch
            }

            userRepository.getUser()
                .onSuccess { user ->
                    userPreferencesRepository.updateLocalUserProfile(
                        username = user?.username.orEmpty(),
                        profilePictureUrl = user?.profilePictureUrl,
                    )
                }
                .onFailure(errorHandler::logError)
            userRepository.registerForPushNotifications().onFailure(errorHandler::logError)
            analyticsRepository.setUserId(authRepository.currentUserId)
            _navigationEvent.trySend(AuthNavigationEvent.NavigateToHome)
            _uiState.update { it.copy(status = AuthStatus.Idle, isResendButtonVisible = false) }
        }
    }

    fun signUp() {
        val trimmedEmail = _uiState.value.email.trim()
        val password = _uiState.value.password
        val trimmedDisplayName = _uiState.value.displayName.trim()
        val trimmedUsername = _uiState.value.username.trim()

        if (trimmedEmail.isBlank() || password.isBlank()) {
            showError(AuthError.EmailPasswordEmpty)
            return
        }
        if (trimmedDisplayName.isBlank() || trimmedUsername.isBlank()) {
            showError(AuthError.AllFieldsRequired)
            return
        }
        if (
            showFirstValidationError(
                validateDisplayName(trimmedDisplayName),
                validateUsername(trimmedUsername)
            )
        ) return

        viewModelScope.launch {
            startLoading()

            val available = authRepository.isUsernameAvailable(trimmedUsername)
                .getOrElse { return@launch fail(it) }
            if (!available) {
                showError(AuthError.UsernameNotAvailable)
                return@launch
            }

            authRepository.signUp(
                email = trimmedEmail,
                password = password,
                displayName = trimmedDisplayName,
                username = trimmedUsername
            ).onSuccess {
                showInfo(AuthNotice.VERIFICATION_EMAIL_SENT, canResend = true)
            }
                // A username conflict arrives as AppError.UsernameTaken/UsernameReserved,
                // which toErrorMessage names like any other failure -- this used to try
                // usernameErrorMessageResId first and fall back.
                .onFailure(::fail)
        }
    }

    fun resetPassword() {
        val trimmedEmail = _uiState.value.email.trim()
        if (trimmedEmail.isBlank()) {
            showError(AuthError.EmailEmpty)
            return
        }

        viewModelScope.launch {
            startLoading()

            val registered = authRepository.isEmailRegistered(trimmedEmail)
                .getOrElse { return@launch fail(it) }
            if (!registered) {
                showError(AuthError.EmailNotRegistered)
                return@launch
            }

            authRepository.resetPassword(trimmedEmail)
                .onSuccess { showInfo(AuthNotice.PASSWORD_RESET_EMAIL_SENT) }
                .onFailure(::fail)
        }
    }

    fun resendVerificationEmail() {
        viewModelScope.launch {
            startLoading()
            authRepository.sendEmailVerification()
                .onSuccess { showInfo(AuthNotice.VERIFICATION_EMAIL_RESENT, canResend = true) }
                .onFailure(::fail)
        }
    }
}