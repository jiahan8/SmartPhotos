package com.jiahan.smartcamera.data.repository

import com.jiahan.smartcamera.domain.MediaUri

/**
 * Inspection, deletion and thumbnail writing for the local media files behind a note's attachments --
 * the file-level half of preparing picked and captured media for upload.
 *
 * Its caller is `DefaultMediaUploadRepository`, in :core:firebase's `commonMain`, which is why it
 * takes [MediaUri]s. It used to take `android.net.Uri` and a `Bitmap`, and sat in :core:common as one
 * of the two repository interfaces whose signatures could not leave Android -- tolerable while every
 * caller was in :core:data. When that caller moved to shared code the interface followed, as
 * [MediaCacheRepository] and [MediaCaptureRepository] had split off it before, and the one method
 * that took a `Bitmap` became [createVideoThumbnail], which extracts the frame and writes it in one
 * call so no bitmap crosses the contract.
 *
 * `DefaultMediaFileRepository` (:core:data) implements this beside those two. Every method is a
 * `ContentResolver`, `MediaMetadataRetriever` or file operation, so the implementation stays Android.
 */
interface MediaFileRepository {

    /**
     * Extracts a frame from the video at [video], writes it as a temporary JPEG in the app cache and
     * returns its location, or `null` if the video yields no frame or the file cannot be written. A
     * video that cannot be read at all throws, so the caller can drop that one item and log it.
     */
    fun createVideoThumbnail(video: MediaUri): MediaUri?

    /**
     * True when [uri]'s MIME type identifies it as a video, so a caller can tell a picked or
     * captured video apart from a photo. A location whose provider reports no type is treated as a
     * photo.
     */
    fun isVideoUri(uri: MediaUri): Boolean

    /**
     * True when [uri] resolves to a file with bytes in it. A canceled capture leaves behind the
     * empty temp file that was handed to the camera, which is not worth uploading. A provider that
     * can't report a size up front (`UNKNOWN_LENGTH`) is treated as having content.
     */
    fun hasContent(uri: MediaUri): Boolean

    /**
     * Deletes the file at [uri] via the content resolver. Safe to call with a FileProvider location
     * pointing at a cache file.
     */
    fun deleteFile(uri: MediaUri)
}