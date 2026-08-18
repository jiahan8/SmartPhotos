package com.jiahan.smartcamera.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.data.repository.AuthRepository
import com.jiahan.smartcamera.data.repository.MediaFileRepository
import com.jiahan.smartcamera.data.repository.UserRepository
import com.jiahan.smartcamera.data.datastore.UserPreferencesRepository
import com.jiahan.smartcamera.domain.ProfilePictureUpdate
import com.jiahan.smartcamera.domain.User
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.ErrorTag
import com.jiahan.smartcamera.util.ResourceProvider
import com.jiahan.smartcamera.util.ValidationResult
import com.jiahan.smartcamera.util.usernameErrorMessageResId
import com.jiahan.smartcamera.util.validateDisplayName
import com.jiahan.smartcamera.util.validateUsername
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ProfileEvent {
    data object UpdateSuccess : ProfileEvent
    data object UploadSuccess : ProfileEvent
    data class UpdateError(val message: String? = null) : ProfileEvent
}

sealed interface ProfileDialogState {
    data object None : ProfileDialogState
    data object DeletePicture : ProfileDialogState
}

data class ProfileUiState(
    val email: String = "",
    val displayName: String = "",
    val username: String = "",
    val profilePictureUrl: String? = null,
    val photoUri: Uri? = null,
    val displayNameErrorMessage: String? = null,
    val usernameErrorMessage: String? = null,
    val errorMessage: String? = null,
    val isErrorFree: Boolean = true,
    val isFormChanged: Boolean = false,
    val isLoading: Boolean = false,
    val isUploading: Boolean = false,
    val dialogState: ProfileDialogState = ProfileDialogState.None,
    val showBottomSheet: Boolean = false
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val mediaFileRepository: MediaFileRepository,
    private val resourceProvider: ResourceProvider,
    private val errorHandler: ErrorHandler,
) : ViewModel() {

    private var user: User? = null

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<ProfileEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    init {
        loadUserProfile()
    }

    fun logImageLoadError(throwable: Throwable) {
        errorHandler.logError(throwable, tag = ErrorTag.IMAGE_LOAD)
    }

    private fun loadUserProfile() {
        viewModelScope.launch {
            userRepository.getUser()
                .onSuccess { fetchedUser ->
                    user = fetchedUser
                    _uiState.update {
                        it.copy(
                            email = fetchedUser?.email ?: "",
                            displayName = fetchedUser?.displayName ?: "",
                            username = fetchedUser?.username ?: "",
                            profilePictureUrl = fetchedUser?.profilePicture
                        )
                    }
                    userPreferencesRepository.updateLocalUserProfile(
                        username = fetchedUser?.username ?: "",
                        profilePictureUrl = fetchedUser?.profilePicture
                    )
                }
                .onFailure { e ->
                    errorHandler.logError(e)
                    _uiState.update { it.copy(errorMessage = errorHandler.getErrorMessage(e)) }
                }
        }
    }

    fun updateDisplayNameText(text: String) {
        val displayNameErrorMessage =
            when (val validationResult = validateDisplayName(text.trim(), requireNonBlank = true)) {
                is ValidationResult.Error -> resourceProvider.getString(validationResult.messageResId)
                else -> null
            }
        _uiState.update {
            it.copy(
                displayName = text,
                displayNameErrorMessage = displayNameErrorMessage
            )
        }
        checkFormChanges()
    }

    fun updateUsernameText(text: String) {
        val usernameErrorMessage =
            when (val validationResult = validateUsername(text.trim(), requireNonBlank = true)) {
                is ValidationResult.Error -> resourceProvider.getString(validationResult.messageResId)
                else -> null
            }
        _uiState.update { it.copy(username = text, usernameErrorMessage = usernameErrorMessage) }
        checkFormChanges()
    }

    private fun checkFormChanges() {
        _uiState.update {
            val isFormChanged = it.displayName.trim() != user?.displayName ||
                    it.username.trim() != user?.username
            val isErrorFree =
                it.usernameErrorMessage == null && it.displayNameErrorMessage == null &&
                        it.errorMessage == null
            it.copy(isFormChanged = isFormChanged, isErrorFree = isErrorFree, errorMessage = null)
        }
    }

    fun updateUserProfile() {
        val state = _uiState.value
        if (!state.isFormChanged || !state.isErrorFree) return

        _uiState.update {
            it.copy(
                displayNameErrorMessage = null,
                usernameErrorMessage = null,
                errorMessage = null
            )
        }

        val trimmedDisplayName = state.displayName.trim()
        val trimmedUsername = state.username.trim()

        if (!_uiState.value.isErrorFree) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            if (trimmedUsername != user?.username) {
                val available = authRepository.isUsernameAvailable(trimmedUsername)
                    .getOrElse { e ->
                        errorHandler.logError(e)
                        _uiState.update {
                            it.copy(
                                errorMessage = errorHandler.getErrorMessage(e),
                                isLoading = false
                            )
                        }
                        _events.tryEmit(ProfileEvent.UpdateError())
                        return@launch
                    }
                if (!available) {
                    _uiState.update {
                        it.copy(
                            usernameErrorMessage = resourceProvider.getString(R.string.username_not_available),
                            isErrorFree = false,
                            isLoading = false
                        )
                    }
                    return@launch
                }
            }

            // Only pass username when it actually changed, so we don't trigger
            // the updateUsername Cloud Function's Firestore transaction on
            // every display-name-only edit.
            val usernameToUpdate = trimmedUsername.takeIf { it != user?.username }

            userRepository.updateUserProfile(
                displayName = trimmedDisplayName,
                username = usernameToUpdate,
                profilePicture = ProfilePictureUpdate.Keep
            ).onSuccess {
                loadUserProfile()
                _uiState.update { it.copy(isFormChanged = false) }
                _events.tryEmit(ProfileEvent.UpdateSuccess)
            }.onFailure { e ->
                errorHandler.logError(e)
                val usernameErrorMessage =
                    usernameErrorMessageResId(e)?.let(resourceProvider::getString)
                _uiState.update {
                    it.copy(
                        usernameErrorMessage = usernameErrorMessage,
                        errorMessage =
                            if (usernameErrorMessage == null)
                                errorHandler.getErrorMessage(e)
                            else null
                    )
                }
                _events.tryEmit(ProfileEvent.UpdateError())
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun uploadProfilePicture(profilePictureUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, showBottomSheet = false) }
            userRepository.uploadProfilePicture(profilePictureUri)
                .onSuccess { profilePictureUrl ->
                    if (profilePictureUrl == null) {
                        _events.tryEmit(ProfileEvent.UpdateError())
                        _uiState.update { it.copy(isUploading = false) }
                        return@launch
                    }
                    userRepository.updateUserProfile(
                        displayName = null,
                        username = null,
                        profilePicture = ProfilePictureUpdate.Set(
                            uri = profilePictureUri,
                            url = profilePictureUrl
                        )
                    ).onSuccess {
                        loadUserProfile()
                        _events.tryEmit(ProfileEvent.UploadSuccess)
                    }.onFailure { e ->
                        errorHandler.logError(e)
                        _events.tryEmit(ProfileEvent.UpdateError(errorHandler.getErrorMessage(e)))
                    }
                }
                .onFailure { e ->
                    errorHandler.logError(e)
                    _events.tryEmit(ProfileEvent.UpdateError(errorHandler.getErrorMessage(e)))
                }
            _uiState.update { it.copy(isUploading = false) }
        }
    }

    fun deleteProfilePicture() {
        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, showBottomSheet = false) }
            userRepository.updateUserProfile(
                displayName = null,
                username = null,
                profilePicture = ProfilePictureUpdate.Delete
            ).onSuccess {
                loadUserProfile()
                _events.tryEmit(ProfileEvent.UploadSuccess)
            }.onFailure { e ->
                errorHandler.logError(e)
                _events.tryEmit(ProfileEvent.UpdateError(errorHandler.getErrorMessage(e)))
            }
            _uiState.update { it.copy(isUploading = false) }
        }
    }

    fun createImageUri(): Uri? = mediaFileRepository.createImageUri()

    fun updatePhotoUri(uri: Uri?) {
        _uiState.update { it.copy(photoUri = uri) }
    }

    fun cancelPhotoCapture(uri: Uri) {
        mediaFileRepository.deleteUri(uri)
        _uiState.update { it.copy(photoUri = null) }
    }

    fun showDeletePictureDialog() {
        _uiState.update { it.copy(dialogState = ProfileDialogState.DeletePicture) }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(dialogState = ProfileDialogState.None) }
    }

    fun updateBottomSheetVisibility(showBottomSheet: Boolean) {
        _uiState.update { it.copy(showBottomSheet = showBottomSheet) }
    }
}