package com.jiahan.smartcamera.data.repository

import com.jiahan.smartcamera.domain.MediaUri

/**
 * Destinations for a capture: an empty temporary file, created before the camera starts, whose
 * location the capture activity is handed to write into.
 *
 * Split off `MediaFileRepository` the way [MediaCacheRepository] was, and for the same reason: the
 * location travels between layers. The note composer's and the profile screen's ViewModels hold it
 * in their state from the moment it is created until the capture comes back, and both ViewModels
 * are in `commonMain`, where `android.net.Uri` does not resolve -- so this returns a [MediaUri],
 * and the screen converts it with `toPlatformUri()` as it launches the camera.
 *
 * `DefaultMediaFileRepository` implements this too: the file is created in the app cache and
 * exposed through the `FileProvider` exactly as it was.
 */
interface MediaCaptureRepository {

    /**
     * Creates a temporary JPEG file in the app cache for the TakePicture activity-result contract
     * to write into. Returns `null` if the file could not be created.
     */
    fun createPhotoUri(): MediaUri?

    /**
     * Creates a temporary MP4 file in the app cache for the CaptureVideo activity-result contract
     * to write into. Returns `null` if the file could not be created.
     */
    fun createVideoUri(): MediaUri?
}