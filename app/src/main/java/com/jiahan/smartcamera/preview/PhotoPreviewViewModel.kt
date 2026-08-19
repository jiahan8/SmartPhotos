package com.jiahan.smartcamera.preview

import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.data.repository.MediaFileRepository
import com.jiahan.smartcamera.navigation.MediaSourceType
import com.jiahan.smartcamera.navigation.Screen
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.ErrorTag
import com.jiahan.smartcamera.util.ResourceProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PhotoPreviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val errorHandler: ErrorHandler,
    private val mediaFileRepository: MediaFileRepository,
    private val resourceProvider: ResourceProvider
) : ViewModel() {

    val photoSource: PhotoSource = run {
        val route = savedStateHandle.toRoute<Screen.PhotoPreview>()
        when (route.type) {
            MediaSourceType.LOCAL -> PhotoSource.LocalUri(route.source.toUri())
            MediaSourceType.REMOTE -> PhotoSource.RemoteUrl(route.source)
        }
    }

    private val _shareEvent = MutableSharedFlow<Uri>(extraBufferCapacity = 1)
    val shareEvent = _shareEvent.asSharedFlow()

    private val _actionError = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val actionError = _actionError.asSharedFlow()

    private val _isSharing = MutableStateFlow(false)
    val isSharing = _isSharing.asStateFlow()

    fun logImageLoadError(throwable: Throwable) {
        errorHandler.logError(throwable, tag = ErrorTag.IMAGE_LOAD)
    }

    fun sharePhoto() {
        if (_isSharing.value) return
        viewModelScope.launch {
            _isSharing.value = true
            try {
                val uri = when (val source = photoSource) {
                    is PhotoSource.LocalUri -> source.uri
                    is PhotoSource.RemoteUrl ->
                        mediaFileRepository.downloadToCacheFile(source.url, isVideo = false)
                }
                if (uri != null) {
                    _shareEvent.emit(uri)
                } else {
                    _actionError.emit(resourceProvider.getString(R.string.share_photo_failure))
                }
            } finally {
                _isSharing.value = false
            }
        }
    }
}