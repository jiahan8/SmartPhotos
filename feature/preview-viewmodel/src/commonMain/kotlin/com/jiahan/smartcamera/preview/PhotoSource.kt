package com.jiahan.smartcamera.preview

import com.jiahan.smartcamera.domain.MediaUri

/**
 * The photo `PhotoPreviewScreen` shows, resolved from the navigation route by
 * `HiltPhotoPreviewViewModel` and held by [PhotoPreviewViewModel].
 *
 * A local photo is a [MediaUri] rather than `android.net.Uri`, which is what let this and its
 * ViewModel into `commonMain`; the screen calls `toPlatformUri()` as it hands one to Coil.
 */
sealed interface PhotoSource {
    data class LocalUri(val uri: MediaUri) : PhotoSource
    data class RemoteUrl(val url: String) : PhotoSource
}