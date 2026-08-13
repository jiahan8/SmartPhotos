package com.jiahan.smartcamera.data.repository

import android.graphics.Bitmap
import android.net.Uri

/**
 * Handles creation and deletion of temporary media files used when capturing
 * photos/videos in the Note flow.
 *
 * Keeping these Android-framework operations in the data layer ensures that
 * no ViewModel needs to hold a reference to [android.content.Context].
 */
interface MediaFileRepository {

    /**
     * Creates a temporary JPEG file in the app cache and returns a FileProvider URI
     * that can be passed directly to the TakePicture activity-result contract.
     * Returns `null` if the file could not be created.
     */
    fun createImageUri(): Uri?

    /**
     * Creates a temporary MP4 file in the app cache and returns a FileProvider URI
     * that can be passed directly to the CaptureVideo activity-result contract.
     * Returns `null` if the file could not be created.
     */
    fun createVideoUri(): Uri?

    /**
     * Saves [bitmap] as a temporary JPEG file in the app cache (used for
     * video thumbnails) and returns its file URI. Recycles [bitmap] once
     * written. Returns `null` if the file could not be created.
     */
    fun saveBitmapAsTempFile(bitmap: Bitmap): Uri?

    /**
     * Downloads the remote resource at [url] into a temporary cache file and
     * returns a FileProvider URI for it, so it can be attached to a share
     * intent. Returns `null` if the download fails.
     */
    suspend fun downloadToCacheFile(url: String, isVideo: Boolean): Uri?

    /**
     * Deletes the file represented by [uri] via the content resolver.
     * Safe to call with a FileProvider URI pointing at a cache file.
     */
    fun deleteUri(uri: Uri)
}