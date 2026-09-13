package com.jiahan.smartcamera.preview

import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.jiahan.smartcamera.data.repository.MediaFileRepository
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.ErrorTag
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VideoPreviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val errorHandler: ErrorHandler,
    private val mediaFileRepository: MediaFileRepository
) : ViewModel() {

    val videoSource: VideoSource = run {
        val route = savedStateHandle.toRoute<VideoPreviewRoute>()
        when (route.type) {
            MediaSourceType.LOCAL -> VideoSource.LocalUri(route.source.toUri())
            MediaSourceType.REMOTE -> VideoSource.RemoteUrl(route.source)
        }
    }

    private val _shareEvent = MutableSharedFlow<Uri>(extraBufferCapacity = 1)
    val shareEvent = _shareEvent.asSharedFlow()

    private val _actionError = MutableSharedFlow<MediaPreviewError>(extraBufferCapacity = 1)
    val actionError = _actionError.asSharedFlow()

    private val _isSharing = MutableStateFlow(false)
    val isSharing = _isSharing.asStateFlow()

    fun logVideoLoadError(throwable: Throwable) {
        errorHandler.logError(throwable, tag = ErrorTag.VIDEO_LOAD)
    }

    fun shareVideo() {
        if (_isSharing.value) return
        viewModelScope.launch {
            _isSharing.value = true
            try {
                val uri = when (val source = videoSource) {
                    is VideoSource.LocalUri -> source.uri
                    is VideoSource.RemoteUrl ->
                        mediaFileRepository.downloadToCacheFile(source.url, isVideo = true)
                }
                if (uri != null) {
                    _shareEvent.emit(uri)
                } else {
                    _actionError.emit(MediaPreviewError.SHARE_FAILED)
                }
            } finally {
                _isSharing.value = false
            }
        }
    }
}