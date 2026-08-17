package com.jiahan.smartcamera.preview

import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.navigation.toRoute
import com.jiahan.smartcamera.navigation.MediaSourceType
import com.jiahan.smartcamera.navigation.Screen
import com.jiahan.smartcamera.util.ErrorHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class VideoPreviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val errorHandler: ErrorHandler
) : ViewModel() {

    val videoSource: VideoSource = run {
        val route = savedStateHandle.toRoute<Screen.VideoPreview>()
        when (route.type) {
            MediaSourceType.LOCAL -> VideoSource.LocalUri(route.source.toUri())
            MediaSourceType.REMOTE -> VideoSource.RemoteUrl(route.source)
        }
    }

    fun logVideoLoadError(throwable: Throwable) {
        errorHandler.logError(throwable, tag = "VideoLoad")
    }
}