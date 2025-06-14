package com.jiahan.smartcamera.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.auth.AuthViewModel.ValidationResult
import com.jiahan.smartcamera.datastore.ProfileRepository
import com.jiahan.smartcamera.domain.User
import com.jiahan.smartcamera.util.ResourceProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val resourceProvider: ResourceProvider
) : ViewModel() {

    private val _user = MutableStateFlow<User?>(null)
    private val _email = MutableStateFlow("")
    val email = _email.asStateFlow()
    private val _fullName = MutableStateFlow("")
    val fullName = _fullName.asStateFlow()
    private val _username = MutableStateFlow("")
    val username = _username.asStateFlow()
    private val _profilePictureUrl = MutableStateFlow<String?>(null)
    val profilePictureUrl = _profilePictureUrl.asStateFlow()

    private val _fullNameErrorMessage = MutableStateFlow<String?>(null)
    val fullNameErrorMessage = _fullNameErrorMessage.asStateFlow()
    private val _usernameErrorMessage = MutableStateFlow<String?>(null)
    val usernameErrorMessage = _usernameErrorMessage.asStateFlow()
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()
    private val _isErrorFree = MutableStateFlow(true)
    val isErrorFree = _isErrorFree.asStateFlow()
    private val _isFormChanged = MutableStateFlow(false)
    val isFormChanged = _isFormChanged.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _updateSuccess = MutableStateFlow(false)
    val updateSuccess = _updateSuccess.asStateFlow()
    private val _updateError = MutableStateFlow(false)
    val updateError = _updateError.asStateFlow()

    init {
        loadUserProfile()
    }

    private fun loadUserProfile() {
        viewModelScope.launch {
            _user.value = profileRepository.getUser()
            _email.value = _user.value?.email ?: ""
            _fullName.value = _user.value?.fullName ?: ""
            _username.value = _user.value?.username ?: ""
            _profilePictureUrl.value = _user.value?.profilePicture
        }
    }

    fun updateFullNameText(text: String) {
        _fullName.value = text
        _fullNameErrorMessage.value = when (val result = validateFullName(text.trim())) {
            is ValidationResult.Error -> {
                resourceProvider.getString(result.messageResId)
            }

            else -> null
        }
        checkFormChanges()
    }

    fun updateUsernameText(text: String) {
        _username.value = text
        _usernameErrorMessage.value = when (val result = validateUsername(text.trim())) {
            is ValidationResult.Error -> {
                resourceProvider.getString(result.messageResId)
            }

            else -> null
        }
        checkFormChanges()
    }

    fun updateProfilePictureUrl(url: String) {
        _profilePictureUrl.value = url
    }

    private fun checkFormChanges() {
        _isFormChanged.value = _fullName.value.trim() != _user.value?.fullName ||
                _username.value.trim() != _user.value?.username
        _isErrorFree.value =
            _usernameErrorMessage.value == null && _fullNameErrorMessage.value == null && _errorMessage.value == null
        _errorMessage.value = null
    }

    fun updateUserProfile() {
        if (!_isFormChanged.value || !_isErrorFree.value) return

        _fullNameErrorMessage.value = null
        _usernameErrorMessage.value = null
        _errorMessage.value = null

        val trimmedFullName = fullName.value.trim()
        val trimmedUsername = username.value.trim()

        if (!_isErrorFree.value) return

        viewModelScope.launch {
            _isLoading.value = true

            try {
                if (trimmedUsername != _user.value?.username &&
                    !profileRepository.isUsernameAvailable(trimmedUsername)
                ) {
                    _usernameErrorMessage.value =
                        resourceProvider.getString(R.string.username_not_available)
                    _isErrorFree.value = false
                    return@launch
                }

                profileRepository.updateUserProfile(
                    fullName = trimmedFullName,
                    username = trimmedUsername,
                    profilePictureUrl = null
                )
                loadUserProfile()
                _isFormChanged.value = false
                _updateSuccess.value = true
            } catch (e: Exception) {
                _errorMessage.value =
                    e.localizedMessage ?: resourceProvider.getString(R.string.error_occurred)
                _updateSuccess.value = false
                _updateError.value = true
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun validateUsername(username: String): ValidationResult {
        return when {
            username.isBlank() -> ValidationResult.Error(R.string.username_empty)

            username.length > 30 ->
                ValidationResult.Error(R.string.username_too_long)

            !username.matches(Regex("^[a-zA-Z0-9._]+$")) ->
                ValidationResult.Error(R.string.username_invalid_characters)

            else -> ValidationResult.Success
        }
    }

    fun validateFullName(fullName: String): ValidationResult {
        return when {
            fullName.isBlank() -> ValidationResult.Error(R.string.name_empty)

            fullName.length > 50 ->
                ValidationResult.Error(R.string.name_too_long)

            else -> ValidationResult.Success
        }
    }

    fun resetUpdateSuccess() {
        _updateSuccess.value = false
    }

    fun resetUpdateError() {
        _updateError.value = false
    }
}