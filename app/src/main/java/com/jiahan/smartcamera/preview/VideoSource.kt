package com.jiahan.smartcamera.preview

import android.net.Uri

/**
 * The video [VideoPreviewScreen] plays, resolved from the navigation route by
 * [VideoPreviewViewModel].
 */
sealed interface VideoSource {
    data class LocalUri(val uri: Uri) : VideoSource
    data class RemoteUrl(val url: String) : VideoSource
}