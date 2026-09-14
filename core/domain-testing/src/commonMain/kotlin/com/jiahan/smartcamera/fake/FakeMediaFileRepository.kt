package com.jiahan.smartcamera.fake

import com.jiahan.smartcamera.data.repository.MediaFileRepository
import com.jiahan.smartcamera.domain.MediaUri

/**
 * In-memory [MediaFileRepository] test double. By default every location is a readable photo with
 * content and no thumbnail, and deleting records the location -- the no-op `SmartPhotosNavigationTest`
 * binds in place of `DataModule`'s real one.
 *
 * It lived in :core:testing while the contract carried `android.net.Uri` and `Bitmap`, and came here
 * when the contract lost them, so `DefaultMediaUploadRepositoryTest` in :core:firebase's `commonTest`
 * can drive it: a video, an unreadable pick, an empty capture.
 */
class FakeMediaFileRepository : MediaFileRepository {

    /** Locations [isVideoUri] reports as videos; every other one is a photo. */
    val videoUris = mutableSetOf<MediaUri>()

    /** Locations whose inspection throws, as an unreadable pick does. */
    val unreadableUris = mutableSetOf<MediaUri>()

    /** Locations [hasContent] reports as empty, as a canceled capture is. */
    val emptyUris = mutableSetOf<MediaUri>()

    /** What [createVideoThumbnail] returns, for any video. */
    var thumbnailUri: MediaUri? = null

    /** Every [deleteFile] call, in order. */
    val deletedUris = mutableListOf<MediaUri>()

    override fun createVideoThumbnail(video: MediaUri): MediaUri? = thumbnailUri

    override fun isVideoUri(uri: MediaUri): Boolean {
        if (uri in unreadableUris) throw IllegalStateException("unreadable: ${uri.value}")
        return uri in videoUris
    }

    override fun hasContent(uri: MediaUri): Boolean = uri !in emptyUris

    override fun deleteFile(uri: MediaUri) {
        deletedUris += uri
    }
}