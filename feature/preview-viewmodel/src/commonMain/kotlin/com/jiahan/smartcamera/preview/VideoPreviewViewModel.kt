package com.jiahan.smartcamera.preview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiahan.smartcamera.data.repository.MediaCacheRepository
import com.jiahan.smartcamera.domain.MediaUri
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.ErrorTag
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backs the video preview. `HiltVideoPreviewViewModel` in :feature:preview is what Hilt builds and
 * where [videoSource] is decoded from `VideoPreviewRoute` -- [PhotoPreviewViewModel]'s arrangement.
 */
open class VideoPreviewViewModel(
    val videoSource: VideoSource,
    private val errorHandler: ErrorHandler,
    private val mediaCacheRepository: MediaCacheRepository
) : ViewModel() {

    private val _shareEvent = MutableSharedFlow<MediaUri>(extraBufferCapacity = 1)
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
                        mediaCacheRepository.downloadToCacheFile(source.url, isVideo = true)
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