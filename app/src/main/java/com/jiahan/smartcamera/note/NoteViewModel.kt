package com.jiahan.smartcamera.note

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.mutableStateMapOf
import androidx.core.content.FileProvider.getUriForFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.data.repository.AnalyticsRepository
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.data.repository.RemoteConfigRepository
import com.jiahan.smartcamera.datastore.ProfileRepository
import com.jiahan.smartcamera.datastore.UserPreferences
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.domain.NoteMediaDetail
import com.jiahan.smartcamera.util.AppConstants.MAX_POST_TEXT_LENGTH
import com.jiahan.smartcamera.util.AppConstants.STATEFLOW_WHILE_SUBSCRIBED_MS
import com.jiahan.smartcamera.util.FileConstants.EXTENSION_JPG
import com.jiahan.smartcamera.util.FileConstants.EXTENSION_MP4
import com.jiahan.smartcamera.util.FileConstants.FILE_PROVIDER_AUTHORITY
import com.jiahan.smartcamera.util.FileConstants.PREFIX_PHOTO
import com.jiahan.smartcamera.util.FileConstants.PREFIX_VIDEO
import com.jiahan.smartcamera.util.ResourceProvider
import com.jiahan.smartcamera.util.Util.createVideoThumbnail
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

@HiltViewModel
class NoteViewModel @Inject constructor(
    private val remoteConfigRepository: RemoteConfigRepository,
    private val noteRepository: NoteRepository,
    profileRepository: ProfileRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val noteHandler: NoteHandler,
    private val resourceProvider: ResourceProvider
) : ViewModel() {

    private val _uploading = MutableStateFlow(false)
    val uploading = _uploading.asStateFlow()
    private val _uploadSuccess = MutableStateFlow(false)
    val uploadSuccess = _uploadSuccess.asStateFlow()
    private val _uploadError = MutableStateFlow(false)
    val uploadError = _uploadError.asStateFlow()
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
    private val _videoThumbnails = mutableStateMapOf<Uri, Bitmap?>()

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
            remoteConfigRepository.fetchAndActivateConfig()
            combine(
                _uploading,
                _postText,
                _mediaList,
                _postTextError
            ) { uploading, postText, uriList, postTextError ->
                !uploading && (postText.isNotBlank() || uriList.isNotEmpty()) && postTextError == null
            }.collect {
                _postButtonEnabled.value = it
            }
        }
    }

    fun uploadPost(text: String, mediaList: List<NoteMediaDetail>) {
        viewModelScope.launch {
            try {
                _uploading.value = true
                val mediaDetailList = noteRepository.uploadMediaToFirebase(mediaList)

                noteRepository.addNote(
                    HomeNote(
                        text = text,
                        mediaList = mediaDetailList,
                        documentPath = "",
                        username = ""
                    )
                )
                _uploadSuccess.value = true
                noteHandler.notifyNoteAdded()
            } catch (e: Exception) {
                e.printStackTrace()
                _uploading.value = false
                _uploadSuccess.value = false
                _uploadError.value = true
            } finally {
                _uploading.value = false
            }
        }
    }

    fun resetUploadSuccess() {
        _uploadSuccess.value = false
    }

    fun resetUploadError() {
        _uploadError.value = false
    }

    fun resetUploading() {
        _uploading.value = false
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

    fun createVideoUri(context: Context): Uri? {
        val timeStamp = System.currentTimeMillis()
        val storageDir = context.cacheDir
        val videoFile = File.createTempFile(
            "$PREFIX_VIDEO${timeStamp}",
            EXTENSION_MP4,
            storageDir
        )

        return getUriForFile(
            context,
            FILE_PROVIDER_AUTHORITY,
            videoFile
        )
    }

    fun updatePostText(text: String) {
        _postText.value = text
        analyticsRepository.logNoteCustomEvent(text)
        validatePostText(text)
    }

    fun updateUriList(context: Context, uriList: List<Uri>) {
        viewModelScope.launch {
            viewModelScope.launch {
                noteRepository.quickUploadMediaToFirebase(uriList)
            }
            val newMediaDetailList = uriList.mapNotNull { uri ->
                try {
                    val isVideo = context.contentResolver.getType(uri)?.startsWith("video/") == true

                    val bitmap = if (isVideo) getVideoThumbnail(context, uri) else null

                    NoteMediaDetail(
                        photoUri = if (!isVideo) uri else null,
                        videoUri = if (isVideo) uri else null,
                        thumbnailBitmap = if (isVideo) bitmap else null,
                        isVideo = isVideo
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }

            _mediaList.value = newMediaDetailList + _mediaList.value
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

    private fun validatePostText(text: String) {
        _postTextError.value = when {
            text.length > MAX_POST_TEXT_LENGTH -> resourceProvider.getString(R.string.post_validation)
            else -> null
        }
    }

    private fun getVideoThumbnail(context: Context, uri: Uri): Bitmap? {
        return _videoThumbnails.getOrPut(uri) {
            createVideoThumbnail(context, uri)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up bitmap cache to prevent memory leaks
        _videoThumbnails.values.forEach { bitmap ->
            bitmap?.recycle()
        }
        _videoThumbnails.clear()
    }
}