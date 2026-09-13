package com.jiahan.smartcamera.fake

import com.jiahan.smartcamera.data.repository.MediaCacheRepository
import com.jiahan.smartcamera.domain.MediaUri

/**
 * [MediaCacheRepository] test double. Answers every download with [downloadResult] -- `null`, a
 * failed download, unless a test sets one -- and records what was asked for.
 */
class FakeMediaCacheRepository : MediaCacheRepository {

    var downloadResult: MediaUri? = null

    /** Each `url` and `isVideo` [downloadToCacheFile] was called with, in order. */
    val requestedDownloads = mutableListOf<Pair<String, Boolean>>()

    override suspend fun downloadToCacheFile(url: String, isVideo: Boolean): MediaUri? {
        requestedDownloads += url to isVideo
        return downloadResult
    }
}