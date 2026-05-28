package com.jiahan.smartcamera.profile

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider.getUriForFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.data.repository.AuthRepository
import com.jiahan.smartcamera.data.repository.UserRepository
import com.jiahan.smartcamera.data.datastore.UserPreferencesRepository
import com.jiahan.smartcamera.domain.User
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.FileConstants.EXTENSION_JPG
import com.jiahan.smartcamera.util.FileConstants.FILE_PROVIDER_AUTHORITY
import com.jiahan.smartcamera.util.FileConstants.PREFIX_PHOTO
import com.jiahan.smartcamera.util.ResourceProvider
import com.jiahan.smartcamera.util.ValidationResult
import com.jiahan.smartcamera.util.validateDisplayName
import com.jiahan.smartcamera.util.validateUsername
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed class ProfileEvent {
    object UpdateSuccess : ProfileEvent()
    object UploadSuccess : ProfileEvent()
    object UpdateError : ProfileEvent()
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val resourceProvider: ResourceProvider,
    private val errorHandler: ErrorHandler,
    @param:ApplicationContext private val context: Context
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
    private val _dialogState = MutableStateFlow<DialogState>(DialogState.None)
    val dialogState = _dialogState.asStateFlow()
    private val _isErrorSnackBar = MutableStateFlow(false)
    val isErrorSnackBar = _isErrorSnackBar.asStateFlow()
    private val _showBottomSheet = MutableStateFlow(false)
    val showBottomSheet = _showBottomSheet.asStateFlow()

    private val _events = MutableSharedFlow<ProfileEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    init {
        loadUserProfile()
    }

    private fun loadUserProfile() {
        viewModelScope.launch {
            userRepository.getUser()
                .onSuccess { user ->
                    _user.value = user
                    _email.value = user?.email ?: ""
                    _displayName.value = user?.displayName ?: ""
                    _username.value = user?.username ?: ""
                    _profilePictureUrl.value = user?.profilePicture
                    userPreferencesRepository.updateLocalUserProfile(
                        username = user?.username ?: "",
                        profilePictureUrl = user?.profilePicture
                    )
                }
                .onFailure { e ->
                    errorHandler.logError(e)
                    _errorMessage.value = errorHandler.getErrorMessage(e)
                }
        }
    }

    fun updateDisplayNameText(text: String) {
        _displayName.value = text
        _displayNameErrorMessage.value =
            when (val result = validateDisplayName(text.trim(), requireNonBlank = true)) {
                is ValidationResult.Error -> resourceProvider.getString(result.messageResId)
                else -> null
            }
        checkFormChanges()
    }

    fun updateUsernameText(text: String) {
        _username.value = text
        _usernameErrorMessage.value =
            when (val result = validateUsername(text.trim(), requireNonBlank = true)) {
                is ValidationResult.Error -> resourceProvider.getString(result.messageResId)
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

            if (trimmedUsername != _user.value?.username) {
                val available = authRepository.isUsernameAvailable(trimmedUsername)
                    .getOrElse { e ->
                        errorHandler.logError(e)
                        _errorMessage.value = errorHandler.getErrorMessage(e)
                        _events.tryEmit(ProfileEvent.UpdateError)
                        _isLoading.value = false
                        return@launch
                    }
                if (!available) {
                    _usernameErrorMessage.value =
                        resourceProvider.getString(R.string.username_not_available)
                    _isErrorFree.value = false
                    _isLoading.value = false
                    return@launch
                }
            }

            userRepository.updateUserProfile(
                displayName = trimmedDisplayName,
                username = trimmedUsername,
                profilePictureUri = null,
                profilePictureUrl = null,
                deleteProfilePicture = false
            ).onSuccess {
                loadUserProfile()
                _isFormChanged.value = false
                _events.tryEmit(ProfileEvent.UpdateSuccess)
            }.onFailure { e ->
                errorHandler.logError(e)
                _errorMessage.value = errorHandler.getErrorMessage(e)
                _events.tryEmit(ProfileEvent.UpdateError)
            }
            _isLoading.value = false
        }
    }

    fun uploadProfilePicture(profilePictureUri: Uri) {
        viewModelScope.launch {
            _isUploading.value = true
            _showBottomSheet.value = false
            userRepository.uploadProfilePicture(profilePictureUri)
                .onSuccess { profilePictureUrl ->
                    userRepository.updateUserProfile(
                        displayName = null,
                        username = null,
                        profilePictureUri = profilePictureUri,
                        profilePictureUrl = profilePictureUrl,
                        deleteProfilePicture = false
                    ).onSuccess {
                        loadUserProfile()
                        _events.tryEmit(ProfileEvent.UploadSuccess)
                    }.onFailure { e ->
                        errorHandler.logError(e)
                        _events.tryEmit(ProfileEvent.UpdateError)
                    }
                }
                .onFailure { e ->
                    errorHandler.logError(e)
                    _events.tryEmit(ProfileEvent.UpdateError)
                }
            _isUploading.value = false
        }
    }

    fun deleteProfilePicture() {
        viewModelScope.launch {
            _isUploading.value = true
            _showBottomSheet.value = false
            userRepository.updateUserProfile(
                displayName = null,
                username = null,
                profilePictureUri = null,
                profilePictureUrl = null,
                deleteProfilePicture = true
            ).onSuccess {
                loadUserProfile()
                _events.tryEmit(ProfileEvent.UploadSuccess)
            }.onFailure { e ->
                errorHandler.logError(e)
                _events.tryEmit(ProfileEvent.UpdateError)
            }
            _isUploading.value = false
        }
    }

    fun createImageUri(): Uri? {
        val timeStamp = System.currentTimeMillis()
        val storageDir = context.cacheDir
        val imageFile = File.createTempFile("$PREFIX_PHOTO${timeStamp}", EXTENSION_JPG, storageDir)
        return getUriForFile(context, FILE_PROVIDER_AUTHORITY, imageFile)
    }

    fun updatePhotoUri(uri: Uri?) {
        _photoUri.value = uri
    }

    fun cancelPhotoCapture(uri: Uri) {
        context.contentResolver.delete(uri, null, null)
        _photoUri.value = null
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