package com.jiahan.smartcamera.fake

import com.jiahan.smartcamera.data.repository.MediaCaptureRepository
import com.jiahan.smartcamera.domain.MediaUri

/**
 * [MediaCaptureRepository] test double. Answers with [photoUri] and [videoUri] -- `null`, a file
 * that could not be created, unless a test sets one. No screen suite launches the camera, so
 * nothing yet needs more.
 */
class FakeMediaCaptureRepository : MediaCaptureRepository {

    var photoUri: MediaUri? = null
    var videoUri: MediaUri? = null

    override fun createPhotoUri(): MediaUri? = photoUri

    override fun createVideoUri(): MediaUri? = videoUri
}