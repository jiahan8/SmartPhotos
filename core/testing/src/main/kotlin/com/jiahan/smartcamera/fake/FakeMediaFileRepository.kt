package com.jiahan.smartcamera.fake

import android.graphics.Bitmap
import android.net.Uri
import com.jiahan.smartcamera.data.repository.MediaFileRepository

/**
 * No-op [MediaFileRepository] test double. The Profile UI only touches this when the user launches
 * the camera/library, which instrumented UI tests do not exercise, so returning `null` is safe.
 */
class FakeMediaFileRepository : MediaFileRepository {

    override fun createPhotoUri(): Uri? = null

    override fun createVideoUri(): Uri? = null

    override fun saveBitmapAsTempFile(bitmap: Bitmap): Uri? = null

    override fun isVideoUri(uri: Uri): Boolean = false

    override fun hasContent(uri: Uri): Boolean = true

    override fun deleteFile(uri: Uri) {}
}