package com.jiahan.smartcamera.preview

import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.jiahan.smartcamera.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PhotoPreviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val photoSource: PhotoSource? = run {
        val type = savedStateHandle.get<String>(Screen.PhotoPreview.TYPE_ARG) ?: return@run null
        val source = savedStateHandle.get<String>(Screen.PhotoPreview.SOURCE_ARG)
            ?.replace("%25", "%") ?: return@run null
        when (type) {
            Screen.PhotoPreview.TYPE_LOCAL -> PhotoSource.LocalUri(source.toUri())
            Screen.PhotoPreview.TYPE_REMOTE -> PhotoSource.RemoteUrl(source)
            else -> null
        }
    }
}