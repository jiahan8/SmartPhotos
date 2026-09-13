package com.jiahan.smartcamera.fake

import android.graphics.Bitmap
import android.net.Uri
import com.jiahan.smartcamera.data.repository.MediaFileRepository

/**
 * No-op [MediaFileRepository] test double. Only `DefaultMediaUploadRepository` calls this contract,
 * so its one consumer now is `SmartPhotosNavigationTest`, binding it in place of `DataModule`'s
 * real one; the capture methods that screens reached went to `FakeMediaCaptureRepository`.
 */
class FakeMediaFileRepository : MediaFileRepository {

    override fun saveBitmapAsTempFile(bitmap: Bitmap): Uri? = null

    override fun isVideoUri(uri: Uri): Boolean = false

    override fun hasContent(uri: Uri): Boolean = true

    override fun deleteFile(uri: Uri) {}
}