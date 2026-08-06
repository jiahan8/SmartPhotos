package com.jiahan.smartcamera.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider.getUriForFile
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.FileConstants.EXTENSION_JPG
import com.jiahan.smartcamera.util.FileConstants.EXTENSION_MP4
import com.jiahan.smartcamera.util.FileConstants.FILE_PROVIDER_AUTHORITY
import com.jiahan.smartcamera.util.FileConstants.PREFIX_PHOTO
import com.jiahan.smartcamera.util.FileConstants.PREFIX_THUMBNAIL
import com.jiahan.smartcamera.util.FileConstants.PREFIX_VIDEO
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

class DefaultMediaFileRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val errorHandler: ErrorHandler,
) : MediaFileRepository {

    override fun createImageUri(): Uri? = try {
        val timeStamp = System.currentTimeMillis()
        val imageFile =
            File.createTempFile("$PREFIX_PHOTO$timeStamp", EXTENSION_JPG, context.cacheDir)
        getUriForFile(context, FILE_PROVIDER_AUTHORITY, imageFile)
    } catch (e: Exception) {
        errorHandler.logError(e)
        null
    }

    override fun createVideoUri(): Uri? = try {
        val timeStamp = System.currentTimeMillis()
        val videoFile =
            File.createTempFile("$PREFIX_VIDEO$timeStamp", EXTENSION_MP4, context.cacheDir)
        getUriForFile(context, FILE_PROVIDER_AUTHORITY, videoFile)
    } catch (e: Exception) {
        errorHandler.logError(e)
        null
    }

    override fun saveBitmapAsTempFile(bitmap: Bitmap): Uri? = try {
        val file = File.createTempFile(PREFIX_THUMBNAIL, EXTENSION_JPG, context.cacheDir)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        Uri.fromFile(file)
    } catch (e: Exception) {
        errorHandler.logError(e)
        null
    } finally {
        bitmap.recycle()
    }

    override fun deleteUri(uri: Uri) {
        try {
            context.contentResolver.delete(uri, null, null)
        } catch (e: Exception) {
            errorHandler.logError(e)
        }
    }
}