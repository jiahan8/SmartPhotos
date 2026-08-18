package com.jiahan.smartcamera.preview

import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.navigation.toRoute
import com.jiahan.smartcamera.navigation.MediaSourceType
import com.jiahan.smartcamera.navigation.Screen
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.ErrorTag
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PhotoPreviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val errorHandler: ErrorHandler
) : ViewModel() {

    val photoSource: PhotoSource = run {
        val route = savedStateHandle.toRoute<Screen.PhotoPreview>()
        when (route.type) {
            MediaSourceType.LOCAL -> PhotoSource.LocalUri(route.source.toUri())
            MediaSourceType.REMOTE -> PhotoSource.RemoteUrl(route.source)
        }
    }

    fun logImageLoadError(throwable: Throwable) {
        errorHandler.logError(throwable, tag = ErrorTag.IMAGE_LOAD)
    }
}