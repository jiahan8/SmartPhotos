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
 * Backs the full-screen photo preview.
 *
 * Open and annotation-free so it can live in `commonMain`; `HiltPhotoPreviewViewModel` in
 * :feature:preview is what Hilt builds, and it decodes `PhotoPreviewRoute` into the [photoSource]
 * this class takes -- `NotePreviewViewModel`'s shape. [shareEvent] carries a [MediaUri] rather than
 * `android.net.Uri`, and the screen resolves it as it builds the share intent.
 */
open class PhotoPreviewViewModel(
    val photoSource: PhotoSource,
    private val errorHandler: ErrorHandler,
    private val mediaCacheRepository: MediaCacheRepository
) : ViewModel() {

    private val _shareEvent = MutableSharedFlow<MediaUri>(extraBufferCapacity = 1)
    val shareEvent = _shareEvent.asSharedFlow()

    private val _actionError = MutableSharedFlow<MediaPreviewError>(extraBufferCapacity = 1)
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
                        mediaCacheRepository.downloadToCacheFile(source.url, isVideo = false)
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