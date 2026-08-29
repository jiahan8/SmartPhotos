package com.jiahan.smartcamera.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider.getUriForFile
import com.jiahan.smartcamera.di.IoDispatcher
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.FileConstants.EXTENSION_JPG
import com.jiahan.smartcamera.util.FileConstants.EXTENSION_MP4
import com.jiahan.smartcamera.util.FileConstants.FILE_PROVIDER_AUTHORITY
import com.jiahan.smartcamera.util.FileConstants.MIME_TYPE_VIDEO_PREFIX
import com.jiahan.smartcamera.util.FileConstants.PREFIX_PHOTO
import com.jiahan.smartcamera.util.FileConstants.PREFIX_THUMBNAIL
import com.jiahan.smartcamera.util.FileConstants.PREFIX_VIDEO
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import javax.inject.Inject

class DefaultMediaFileRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val errorHandler: ErrorHandler,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
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

    override suspend fun downloadToCacheFile(url: String, isVideo: Boolean): Uri? =
        withContext(ioDispatcher) {
            try {
                val timeStamp = System.currentTimeMillis()
                val prefix = if (isVideo) PREFIX_VIDEO else PREFIX_PHOTO
                val extension = if (isVideo) EXTENSION_MP4 else EXTENSION_JPG
                val file = File.createTempFile("$prefix$timeStamp", extension, context.cacheDir)
                URL(url).openStream().use { input ->
                    FileOutputStream(file).use { output -> input.copyTo(output) }
                }
                getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
            } catch (e: Exception) {
                errorHandler.logError(e)
                null
            }
        }

    override fun isVideoUri(uri: Uri): Boolean =
        context.contentResolver.getType(uri)?.startsWith(MIME_TYPE_VIDEO_PREFIX) == true

    override fun hasContent(uri: Uri): Boolean = try {
        context.contentResolver.openAssetFileDescriptor(uri, "r")
            ?.use { descriptor -> descriptor.length != 0L } == true
    } catch (e: Exception) {
        errorHandler.logError(e)
        false
    }

    override fun deleteUri(uri: Uri) {
        try {
            context.contentResolver.delete(uri, null, null)
        } catch (e: Exception) {
            errorHandler.logError(e)
        }
    }
}