package com.jiahan.smartcamera.fake

import com.jiahan.smartcamera.data.repository.MediaCacheRepository
import com.jiahan.smartcamera.domain.MediaUri

/**
 * [MediaCacheRepository] test double. Answers every download with [downloadResult] -- `null`, a
 * failed download, unless a test sets one -- or with [downloadAnswer] when that is set, and records
 * what was asked for.
 */
class FakeMediaCacheRepository : MediaCacheRepository {

    var downloadResult: MediaUri? = null

    /**
     * Answers [downloadToCacheFile] in place of [downloadResult] when set -- e.g. to hold the
     * download in flight. A `null` it returns is a failed download, not a fall-back to the result.
     */
    var downloadAnswer: (suspend (url: String, isVideo: Boolean) -> MediaUri?)? = null

    /** Each `url` and `isVideo` [downloadToCacheFile] was called with, in order. */
    val requestedDownloads = mutableListOf<Pair<String, Boolean>>()

    override suspend fun downloadToCacheFile(url: String, isVideo: Boolean): MediaUri? {
        requestedDownloads += url to isVideo
        val answer = downloadAnswer ?: return downloadResult
        return answer(url, isVideo)
    }
}