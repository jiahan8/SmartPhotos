package com.jiahan.smartcamera.note

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider.getUriForFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.data.repository.AnalyticsRepository
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.datastore.ProfileRepository
import com.jiahan.smartcamera.datastore.UserPreferences
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.domain.NoteMediaDetail
import com.jiahan.smartcamera.util.AppConstants.MAX_POST_TEXT_LENGTH
import com.jiahan.smartcamera.util.AppConstants.STATEFLOW_WHILE_SUBSCRIBED_MS
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.FileConstants.EXTENSION_JPG
import com.jiahan.smartcamera.util.FileConstants.EXTENSION_MP4
import com.jiahan.smartcamera.util.FileConstants.FILE_PROVIDER_AUTHORITY
import com.jiahan.smartcamera.util.FileConstants.PREFIX_PHOTO
import com.jiahan.smartcamera.util.FileConstants.PREFIX_VIDEO
import com.jiahan.smartcamera.util.ResourceProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed interface UploadUiState {
    data object Idle : UploadUiState
    data object Uploading : UploadUiState
    data object Success : UploadUiState
    data class Error(val message: String) : UploadUiState
}

@HiltViewModel
class NoteViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    profileRepository: ProfileRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val noteHandler: NoteHandler,
    private val resourceProvider: ResourceProvider,
    private val errorHandler: ErrorHandler
) : ViewModel() {

    private val _uploadUiState = MutableStateFlow<UploadUiState>(UploadUiState.Idle)
    val uploadUiState = _uploadUiState.asStateFlow()
    private val _postTextError = MutableStateFlow<String?>(null)
    val postTextError = _postTextError.asStateFlow()
    private val _postButtonEnabled = MutableStateFlow(false)
    val postButtonEnabled = _postButtonEnabled.asStateFlow()
    private val _isErrorSnackBar = MutableStateFlow(false)
    val isErrorSnackBar = _isErrorSnackBar.asStateFlow()

    private val _postText = MutableStateFlow("")
    val postText = _postText.asStateFlow()
    private val _photoUri = MutableStateFlow<Uri?>(null)
    val photoUri = _photoUri.asStateFlow()
    private val _videoUri = MutableStateFlow<Uri?>(null)
    val videoUri = _videoUri.asStateFlow()
    private val _mediaList = MutableStateFlow<List<NoteMediaDetail>>(emptyList())
    val mediaList = _mediaList.asStateFlow()

    private val _currentPlaceholderIndex = MutableStateFlow(0)
    val currentPlaceholderIndex = _currentPlaceholderIndex.asStateFlow()

    private val userPreferences = profileRepository.userPreferencesFlow
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATEFLOW_WHILE_SUBSCRIBED_MS),
            initialValue = UserPreferences(
                isDarkTheme = false,
                username = "",
                profilePicture = null
            )
        )

    val username = userPreferences
        .map { it.username }
        .distinctUntilChanged()

    val profilePicture = userPreferences
        .map { it.profilePicture }
        .distinctUntilChanged()

    init {
        viewModelScope.launch {
            combine(
                _uploadUiState,
                _postText,
                _mediaList,
                _postTextError
            ) { uploadState, postText, uriList, postTextError ->
                uploadState !is UploadUiState.Uploading && (postText.isNotBlank() || uriList.isNotEmpty()) && postTextError == null
            }.collect {
                _postButtonEnabled.value = it
            }
        }
    }

    fun uploadPost(text: String, mediaList: List<NoteMediaDetail>) {
        viewModelScope.launch {
            _uploadUiState.value = UploadUiState.Uploading
            noteRepository.uploadMediaToFirebase(mediaList)
                .onSuccess { mediaDetailList ->
                    noteRepository.addNote(
                        HomeNote(
                            text = text,
                            mediaList = mediaDetailList,
                            documentPath = "",
                            username = ""
                        )
                    )
                        .onSuccess {
                            noteHandler.notifyNoteAdded()
                            _uploadUiState.value = UploadUiState.Success
                        }
                        .onFailure { e ->
                            errorHandler.logError(e)
                            _uploadUiState.value =
                                UploadUiState.Error(errorHandler.getErrorMessage(e))
                        }
                }
                .onFailure { e ->
                    errorHandler.logError(e)
                    _uploadUiState.value = UploadUiState.Error(errorHandler.getErrorMessage(e))
                }
        }
    }

    fun resetUploadState() {
        _uploadUiState.value = UploadUiState.Idle
    }

    fun createImageUri(context: Context): Uri? {
        val timeStamp = System.currentTimeMillis()
        val storageDir = context.cacheDir
        val imageFile = File.createTempFile(
            "$PREFIX_PHOTO${timeStamp}",
            EXTENSION_JPG,
            storageDir
        )
        return getUriForFile(context, FILE_PROVIDER_AUTHORITY, imageFile)
    }

    fun createVideoUri(context: Context): Uri? {
        val timeStamp = System.currentTimeMillis()
        val storageDir = context.cacheDir
        val videoFile = File.createTempFile(
            "$PREFIX_VIDEO${timeStamp}",
            EXTENSION_MP4,
            storageDir
        )
        return getUriForFile(context, FILE_PROVIDER_AUTHORITY, videoFile)
    }

    fun updatePostText(text: String) {
        _postText.value = text
        analyticsRepository.logNoteCustomEvent(text)
        _postTextError.value = when {
            text.length > MAX_POST_TEXT_LENGTH -> resourceProvider.getString(R.string.post_validation)
            else -> null
        }
    }

    fun updateUriList(uriList: List<Uri>) {
        viewModelScope.launch { noteRepository.quickUploadMediaToFirebase(uriList) }

        viewModelScope.launch {
            noteRepository.buildLocalMediaDetails(uriList)
                .onSuccess { newMediaDetailList ->
                    _mediaList.value = newMediaDetailList + _mediaList.value
                }
                .onFailure { e -> errorHandler.logError(e) }
        }
    }

    fun removeUriFromList(index: Int) {
        if (index >= 0 && index < _mediaList.value.size) {
            val newList = _mediaList.value.toMutableList()
            newList.removeAt(index)
            _mediaList.value = newList
        }
    }

    fun updatePhotoUri(uri: Uri?) {
        _photoUri.value = uri
    }

    fun updateVideoUri(uri: Uri?) {
        _videoUri.value = uri
    }

    fun updateErrorSnackBar(isError: Boolean) {
        _isErrorSnackBar.value = isError
    }

    fun updateCurrentPlaceholderIndex(index: Int) {
        _currentPlaceholderIndex.value = index
    }


    override fun onCleared() {
        super.onCleared()
        // Recycle any video thumbnail bitmaps to prevent memory leaks
        _mediaList.value
            .mapNotNull { it.thumbnailBitmap }
            .forEach { it.recycle() }
    }
}