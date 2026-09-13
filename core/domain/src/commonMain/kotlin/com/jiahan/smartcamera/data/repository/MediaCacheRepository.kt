package com.jiahan.smartcamera.data.repository

import com.jiahan.smartcamera.domain.MediaUri

/**
 * Local copies of remote media, made so the media can leave the app -- attached to a share.
 *
 * Split off `MediaFileRepository`, whose other methods hand an `android.net.Uri` straight to an
 * activity-result contract and so stay Android-typed in :core:common. This method's result travels
 * between layers instead -- from the data layer through `NoteShareDelegate` or a preview
 * ViewModel's share event, to the screen that builds the intent -- which is the case [MediaUri]
 * exists for, and what lets `NoteShareDelegate` sit in `commonMain`. The screen converts it with
 * `toPlatformUri()` at the last step.
 *
 * `DefaultMediaFileRepository` implements both interfaces: the file is created, written and exposed
 * through the `FileProvider` exactly as it was.
 */
interface MediaCacheRepository {

    /**
     * Downloads the remote resource at [url] into a temporary cache file and returns a location a
     * share intent can carry. Returns `null` if the download fails.
     */
    suspend fun downloadToCacheFile(url: String, isVideo: Boolean): MediaUri?
}