package com.jiahan.smartcamera.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.datastore.ProfileRepository
import com.jiahan.smartcamera.util.ResourceProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val resourceProvider: ResourceProvider
) : ViewModel() {

    private val _email = MutableStateFlow("")
    val email = _email.asStateFlow()
    private val _password = MutableStateFlow("")
    val password = _password.asStateFlow()
    private val _fullName = MutableStateFlow("")
    val fullName = _fullName.asStateFlow()
    private val _username = MutableStateFlow("")
    val username = _username.asStateFlow()

    private val _isPasswordVisible = MutableStateFlow(false)
    val passwordVisible = _isPasswordVisible.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _errorMessage = MutableStateFlow("")
    val errorMessage = _errorMessage.asStateFlow()
    private val _isLoginMode = MutableStateFlow(true)
    val isLoginMode = _isLoginMode.asStateFlow()

    private val _navigationEvent = MutableStateFlow<NavigationEvent?>(null)
    val navigationEvent = _navigationEvent.asStateFlow()

    fun updateEmailText(text: String) {
        _email.value = text
    }

    fun updatePasswordText(text: String) {
        _password.value = text
    }

    fun updateFullNameText(text: String) {
        _fullName.value = text
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
        _errorMessage.value = ""
    }

    private fun clearFields() {
        _email.value = ""
        _password.value = ""
        _fullName.value = ""
        _username.value = ""
    }

    fun signIn() {
        val trimmedEmail = email.value.trim()

        if (trimmedEmail.isBlank() || password.value.isBlank()) {
            _errorMessage.value = resourceProvider.getString(R.string.email_password_empty)
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""

            try {
                val result = profileRepository.signIn(trimmedEmail, password.value)
                if (result.isSuccess) {
                    if (profileRepository.isEmailVerified()) {
                        _navigationEvent.value = NavigationEvent.NavigateToHome
                    } else {
                        _errorMessage.value =
                            resourceProvider.getString(R.string.email_not_verified)
                    }
                } else {
                    _errorMessage.value = result.exceptionOrNull()?.localizedMessage
                        ?: resourceProvider.getString(R.string.login_failure)
                }
            } catch (e: Exception) {
                _errorMessage.value =
                    e.localizedMessage ?: resourceProvider.getString(R.string.error_occurred)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun signUp() {
        val trimmedEmail = email.value.trim()
        val trimmedFullName = fullName.value.trim()
        val trimmedUsername = username.value.trim()

        if (trimmedEmail.isBlank() || password.value.isBlank()) {
            _errorMessage.value = resourceProvider.getString(R.string.email_password_empty)
            return
        }

        if (trimmedFullName.isBlank() || trimmedUsername.isBlank()) {
            _errorMessage.value = resourceProvider.getString(R.string.all_fields_required)
            return
        }

        when (val result = validateFullName(trimmedFullName)) {
            is ValidationResult.Error -> {
                _errorMessage.value = resourceProvider.getString(result.messageResId)
                return
            }

            else -> {}
        }

        when (val result = validateUsername(trimmedUsername)) {
            is ValidationResult.Error -> {
                _errorMessage.value = resourceProvider.getString(result.messageResId)
                return
            }

            else -> {}
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""

            try {
                if (!profileRepository.isUsernameAvailable(trimmedUsername)) {
                    throw Exception(resourceProvider.getString(R.string.username_not_available))
                }

                val result = profileRepository.signUp(
                    email = trimmedEmail,
                    password = password.value,
                    fullName = trimmedFullName,
                    username = trimmedUsername
                )
                if (result.isSuccess) {
                    _errorMessage.value =
                        resourceProvider.getString(R.string.verification_email_sent)
                } else {
                    _errorMessage.value = result.exceptionOrNull()?.localizedMessage
                        ?: resourceProvider.getString(R.string.sign_up_failure)
                }
            } catch (e: Exception) {
                _errorMessage.value =
                    e.localizedMessage ?: resourceProvider.getString(R.string.error_occurred)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun resetPassword() {
        val trimmedEmail = email.value.trim()

        if (trimmedEmail.isBlank()) {
            _errorMessage.value = resourceProvider.getString(R.string.enter_email)
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = ""

            try {
                if (!profileRepository.isEmailRegistered(trimmedEmail)) {
                    throw Exception(resourceProvider.getString(R.string.email_not_registered))
                }

                val result = profileRepository.resetPassword(trimmedEmail)
                if (result.isSuccess) {
                    _errorMessage.value =
                        resourceProvider.getString(R.string.password_reset_email_sent)
                } else {
                    _errorMessage.value =
                        result.exceptionOrNull()?.localizedMessage
                            ?: resourceProvider.getString(R.string.password_reset_failure)
                }
            } catch (e: Exception) {
                _errorMessage.value =
                    e.localizedMessage ?: resourceProvider.getString(R.string.error_occurred)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun validateUsername(username: String): ValidationResult {
        return when {
            username.length > 30 ->
                ValidationResult.Error(R.string.username_too_long)

            !username.matches(Regex("^[a-zA-Z0-9._]+$")) ->
                ValidationResult.Error(R.string.username_invalid_characters)

            else -> ValidationResult.Success
        }
    }

    private fun validateFullName(fullName: String): ValidationResult {
        return when {
            fullName.length > 50 ->
                ValidationResult.Error(R.string.name_too_long)

            else -> ValidationResult.Success
        }
    }

    fun resendVerificationEmail() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                profileRepository.sendEmailVerification()
                _errorMessage.value = resourceProvider.getString(R.string.verification_email_resent)
            } catch (e: Exception) {
                _errorMessage.value =
                    e.localizedMessage ?: resourceProvider.getString(R.string.error_occurred)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun navigationEventConsumed() {
        _navigationEvent.value = null
    }

    sealed class NavigationEvent {
        object NavigateToHome : NavigationEvent()
    }
}

sealed class ValidationResult {
    object Success : ValidationResult()
    data class Error(val messageResId: Int) : ValidationResult()
}