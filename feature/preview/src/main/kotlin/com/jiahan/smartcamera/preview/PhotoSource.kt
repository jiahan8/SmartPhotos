package com.jiahan.smartcamera.preview

import android.net.Uri

/**
 * The photo [PhotoPreviewScreen] shows, resolved from the navigation route by
 * [PhotoPreviewViewModel].
 */
sealed interface PhotoSource {
    data class LocalUri(val uri: Uri) : PhotoSource
    data class RemoteUrl(val url: String) : PhotoSource
}