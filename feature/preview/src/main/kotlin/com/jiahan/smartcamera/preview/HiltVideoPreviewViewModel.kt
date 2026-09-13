package com.jiahan.smartcamera.preview

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.jiahan.smartcamera.data.repository.MediaCacheRepository
import com.jiahan.smartcamera.domain.MediaUri
import com.jiahan.smartcamera.util.ErrorHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * What Hilt instantiates for [VideoPreviewViewModel], and where [VideoPreviewRoute] is decoded into a
 * [VideoSource] -- `HiltPhotoPreviewViewModel`'s arrangement, which records why the decode resolves
 * [MediaSourceType] here and why it is still tested on the JVM.
 */
@HiltViewModel
class HiltVideoPreviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    errorHandler: ErrorHandler,
    mediaCacheRepository: MediaCacheRepository,
) : VideoPreviewViewModel(
    savedStateHandle.toRoute<VideoPreviewRoute>().toVideoSource(),
    errorHandler,
    mediaCacheRepository,
)

private fun VideoPreviewRoute.toVideoSource(): VideoSource = when (type) {
    MediaSourceType.LOCAL -> VideoSource.LocalUri(MediaUri(source))
    MediaSourceType.REMOTE -> VideoSource.RemoteUrl(source)
}