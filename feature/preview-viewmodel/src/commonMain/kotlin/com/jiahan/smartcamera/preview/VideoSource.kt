package com.jiahan.smartcamera.preview

import com.jiahan.smartcamera.domain.MediaUri

/**
 * The video `VideoPreviewScreen` plays, resolved from the navigation route by
 * `HiltVideoPreviewViewModel` and held by [VideoPreviewViewModel]. A local video is a [MediaUri],
 * for the reason [PhotoSource] gives; the screen resolves it for ExoPlayer.
 */
sealed interface VideoSource {
    data class LocalUri(val uri: MediaUri) : VideoSource
    data class RemoteUrl(val url: String) : VideoSource
}