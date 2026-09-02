package com.jiahan.smartcamera.data.repository

import android.graphics.Bitmap
import android.net.Uri

/**
 * Handles creation, inspection and deletion of temporary media files used when
 * capturing photos/videos in the Note flow.
 *
 * Keeping these Android-framework operations in the data layer ensures that
 * no ViewModel needs to hold a reference to [android.content.Context].
 *
 * This is the one repository interface that deliberately keeps Android types in its signatures:
 * every method here *is* a `ContentResolver`/`FileProvider` operation, and the URIs it hands back
 * go straight to activity-result contracts. Wrapping them in
 * [com.jiahan.smartcamera.domain.MediaUri] would add conversions at every call site while hiding
 * that this seam is Android-only and will never move to a shared source set. Contracts that carry
 * media *between* layers use `MediaUri` instead — see `NoteRepository` and `UserRepository`.
 *
 * Those Android types are also why this sits in :core:common rather than beside the other
 * contracts in :core:domain, which has no Android plugin. It lived in :core:data next to
 * [DefaultMediaFileRepository] until `:feature:profile` was extracted and needed to inject it: a
 * feature module must not depend on :core:data, so the interface came down to the module both
 * sides can see while the implementation stayed put. `AppUpdateRepository` is the other interface
 * stranded that way and has *not* followed, because nothing below :app injects it — move it if and
 * when something does.
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
     * True when [uri]'s MIME type identifies it as a video, so a caller can tell a picked or
     * captured video apart from a photo. A URI whose provider reports no type is treated as a
     * photo.
     */
    fun isVideoUri(uri: Uri): Boolean

    /**
     * True when [uri] resolves to a file with bytes in it. A canceled capture leaves behind the
     * empty temp file that was handed to the camera, which is not worth uploading. A provider that
     * can't report a size up front (`UNKNOWN_LENGTH`) is treated as having content.
     */
    fun hasContent(uri: Uri): Boolean

    /**
     * Deletes the file represented by [uri] via the content resolver.
     * Safe to call with a FileProvider URI pointing at a cache file.
     */
    fun deleteUri(uri: Uri)
}