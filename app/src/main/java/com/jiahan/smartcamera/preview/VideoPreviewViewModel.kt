package com.jiahan.smartcamera.preview

import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.jiahan.smartcamera.navigation.Screen
import com.jiahan.smartcamera.util.ErrorHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class VideoPreviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val errorHandler: ErrorHandler
) : ViewModel() {

    val videoSource: VideoSource? = run {
        val type = savedStateHandle.get<String>(Screen.VideoPreview.TYPE_ARG) ?: return@run null
        val source = savedStateHandle.get<String>(Screen.VideoPreview.SOURCE_ARG)
            ?.replace("%25", "%") ?: return@run null
        when (type) {
            Screen.VideoPreview.TYPE_LOCAL -> VideoSource.LocalUri(source.toUri())
            Screen.VideoPreview.TYPE_REMOTE -> VideoSource.RemoteUrl(source)
            else -> null
        }
    }

    fun logVideoLoadError(throwable: Throwable) {
        errorHandler.logError(throwable, tag = "VideoLoad")
    }
}