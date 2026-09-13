package com.jiahan.smartcamera.data.repository

import android.graphics.Bitmap
import android.net.Uri

/**
 * Inspection, deletion and thumbnail writing for the temporary media files behind a note's
 * attachments -- the file-level half of preparing picked and captured media for upload.
 *
 * Keeping these Android-framework operations in the data layer ensures that
 * no ViewModel needs to hold a reference to [android.content.Context].
 *
 * This is the one repository interface that deliberately keeps Android types in its signatures:
 * every method here is a `ContentResolver` or file operation, and every caller is inside the data
 * layer (`DefaultMediaUploadRepository`), so nothing it returns reaches a ViewModel. Contracts that
 * carry media *between* layers use [com.jiahan.smartcamera.domain.MediaUri] instead, and two were
 * carved out of this one for exactly that reason: `downloadToCacheFile`, whose result left through
 * a share event, is `MediaCacheRepository`'s, and `createPhotoUri`/`createVideoUri`, whose results
 * the note composer's and profile screen's ViewModels hold until a capture returns, are
 * `MediaCaptureRepository`'s. Both are in :core:domain, and `DefaultMediaFileRepository` implements
 * all three.
 *
 * Those Android types are also why this sits in :core:common rather than beside the other
 * contracts in :core:domain, which has no Android plugin. It lived in :core:data next to
 * `DefaultMediaFileRepository` until `:feature:profile` was extracted and needed to inject it: a
 * feature module must not depend on :core:data, so the interface came down to the module both
 * sides can see while the implementation stayed put. No feature injects it any more -- the capture
 * methods were what `:feature:profile` wanted -- but it has not gone back up: `FakeMediaFileRepository`
 * in :core:testing implements it, and no fixtures module may depend on :core:data.
 * `AppUpdateRepository` is the interface that never came down, because nothing below :app injects
 * it — move it if and when something does.
 */
interface MediaFileRepository {

    /**
     * Saves [bitmap] as a temporary JPEG file in the app cache (used for
     * video thumbnails) and returns its file URI. Recycles [bitmap] once
     * written. Returns `null` if the file could not be created.
     */
    fun saveBitmapAsTempFile(bitmap: Bitmap): Uri?

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
    fun deleteFile(uri: Uri)
}