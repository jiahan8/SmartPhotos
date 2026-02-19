package com.jiahan.smartcamera.profile

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider.getUriForFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.auth.ValidationResult
import com.jiahan.smartcamera.datastore.ProfileRepository
import com.jiahan.smartcamera.domain.User
import com.jiahan.smartcamera.util.AppConstants.MAX_DISPLAY_NAME_LENGTH
import com.jiahan.smartcamera.util.AppConstants.MAX_USERNAME_LENGTH
import com.jiahan.smartcamera.util.FileConstants.EXTENSION_JPG
import com.jiahan.smartcamera.util.FileConstants.FILE_PROVIDER_AUTHORITY
import com.jiahan.smartcamera.util.FileConstants.PREFIX_PHOTO
import com.jiahan.smartcamera.util.ResourceProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val resourceProvider: ResourceProvider
) : ViewModel() {

    private val _user = MutableStateFlow<User?>(null)
    private val _email = MutableStateFlow("")
    val email = _email.asStateFlow()
    private val _displayName = MutableStateFlow("")
    val displayName = _displayName.asStateFlow()
    private val _username = MutableStateFlow("")
    val username = _username.asStateFlow()
    private val _profilePictureUrl = MutableStateFlow<String?>(null)
    val profilePictureUrl = _profilePictureUrl.asStateFlow()
    private val _photoUri = MutableStateFlow<Uri?>(null)
    val photoUri = _photoUri.asStateFlow()

    private val _displayNameErrorMessage = MutableStateFlow<String?>(null)
    val displayNameErrorMessage = _displayNameErrorMessage.asStateFlow()
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
    private val _isUploading = MutableStateFlow(false)
    val isUploading = _isUploading.asStateFlow()
    private val _updateSuccess = MutableStateFlow(false)
    val updateSuccess = _updateSuccess.asStateFlow()
    private val _uploadSuccess = MutableStateFlow(false)
    val uploadSuccess = _uploadSuccess.asStateFlow()
    private val _updateError = MutableStateFlow(false)
    val updateError = _updateError.asStateFlow()
    private val _dialogState = MutableStateFlow<DialogState>(DialogState.None)
    val dialogState = _dialogState.asStateFlow()
    private val _isErrorSnackBar = MutableStateFlow(false)
    val isErrorSnackBar = _isErrorSnackBar.asStateFlow()
    private val _showBottomSheet = MutableStateFlow(false)
    val showBottomSheet = _showBottomSheet.asStateFlow()

    init {
        loadUserProfile()
    }

    private fun loadUserProfile() {
        viewModelScope.launch {
            _user.value = profileRepository.getUser()
            _email.value = _user.value?.email ?: ""
            _displayName.value = _user.value?.displayName ?: ""
            _username.value = _user.value?.username ?: ""
            _profilePictureUrl.value = _user.value?.profilePicture
            profileRepository.updateLocalUserProfile(
                username = _user.value?.username ?: "",
                profilePictureUrl = _user.value?.profilePicture
            )
        }
    }

    fun updateDisplayNameText(text: String) {
        _displayName.value = text
        _displayNameErrorMessage.value = when (val result = validateDisplayName(text.trim())) {
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

    private fun checkFormChanges() {
        _isFormChanged.value = _displayName.value.trim() != _user.value?.displayName ||
                _username.value.trim() != _user.value?.username
        _isErrorFree.value =
            _usernameErrorMessage.value == null && _displayNameErrorMessage.value == null && _errorMessage.value == null
        _errorMessage.value = null
    }

    fun updateUserProfile() {
        if (!_isFormChanged.value || !_isErrorFree.value) return

        _displayNameErrorMessage.value = null
        _usernameErrorMessage.value = null
        _errorMessage.value = null

        val trimmedDisplayName = displayName.value.trim()
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
                    displayName = trimmedDisplayName,
                    username = trimmedUsername,
                    profilePictureUri = null,
                    profilePictureUrl = null,
                    deleteProfilePicture = false
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

    private fun validateUsername(username: String): ValidationResult {
        return when {
            username.isBlank() -> ValidationResult.Error(R.string.username_empty)

            username.length > MAX_USERNAME_LENGTH ->
                ValidationResult.Error(R.string.username_too_long)

            !username.matches(Regex("^[a-zA-Z0-9._]+$")) ->
                ValidationResult.Error(R.string.username_invalid_characters)

            else -> ValidationResult.Success
        }
    }

    private fun validateDisplayName(displayName: String): ValidationResult {
        return when {
            displayName.isBlank() -> ValidationResult.Error(R.string.name_empty)

            displayName.length > MAX_DISPLAY_NAME_LENGTH ->
                ValidationResult.Error(R.string.name_too_long)

            else -> ValidationResult.Success
        }
    }

    fun uploadProfilePicture(profilePictureUri: Uri) {
        viewModelScope.launch {
            _isUploading.value = true
            _showBottomSheet.value = false
            try {
                val profilePictureUrl = profileRepository.uploadMediaToFirebase(profilePictureUri)
                profileRepository.updateUserProfile(
                    displayName = null,
                    username = null,
                    profilePictureUri = profilePictureUri,
                    profilePictureUrl = profilePictureUrl,
                    deleteProfilePicture = false
                )
                loadUserProfile()
                _uploadSuccess.value = true
            } catch (e: Exception) {
                e.printStackTrace()
                _uploadSuccess.value = false
                _updateError.value = true
            } finally {
                _isUploading.value = false
            }
        }
    }

    fun deleteProfilePicture() {
        viewModelScope.launch {
            _isUploading.value = true
            _showBottomSheet.value = false
            try {
                profileRepository.updateUserProfile(
                    displayName = null,
                    username = null,
                    profilePictureUri = null,
                    profilePictureUrl = null,
                    deleteProfilePicture = true
                )
                loadUserProfile()
                _uploadSuccess.value = true
            } catch (e: Exception) {
                e.printStackTrace()
                _uploadSuccess.value = false
                _updateError.value = true
            } finally {
                _isUploading.value = false
            }
        }
    }

    fun createImageUri(context: Context): Uri? {
        val timeStamp = System.currentTimeMillis()
        val storageDir = context.cacheDir
        val imageFile = File.createTempFile(
            "$PREFIX_PHOTO${timeStamp}",
            EXTENSION_JPG,
            storageDir
        )

        return getUriForFile(
            context,
            FILE_PROVIDER_AUTHORITY,
            imageFile
        )
    }

    fun updatePhotoUri(uri: Uri?) {
        _photoUri.value = uri
    }

    fun resetUpdateSuccess() {
        _updateSuccess.value = false
    }

    fun resetUploadSuccess() {
        _uploadSuccess.value = false
    }

    fun resetUpdateError() {
        _updateError.value = false
    }

    fun showDeletePictureDialog() {
        _dialogState.value = DialogState.DeletePicture
    }

    fun dismissDialog() {
        _dialogState.value = DialogState.None
    }

    fun updateErrorSnackBar(isError: Boolean) {
        _isErrorSnackBar.value = isError
    }

    fun updateBottomSheetVisibility(showBottomSheet: Boolean) {
        _showBottomSheet.value = showBottomSheet
    }

    sealed class DialogState {
        object None : DialogState()
        object DeletePicture : DialogState()
    }
}