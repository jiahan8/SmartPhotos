package com.jiahan.smartcamera.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider.getUriForFile
import com.jiahan.smartcamera.di.IoDispatcher
import com.jiahan.smartcamera.domain.MediaUri
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.FileConstants.EXTENSION_JPG
import com.jiahan.smartcamera.util.FileConstants.EXTENSION_MP4
import com.jiahan.smartcamera.util.FileConstants.FILE_PROVIDER_AUTHORITY
import com.jiahan.smartcamera.util.FileConstants.MIME_TYPE_VIDEO_PREFIX
import com.jiahan.smartcamera.util.FileConstants.PREFIX_PHOTO
import com.jiahan.smartcamera.util.FileConstants.PREFIX_THUMBNAIL
import com.jiahan.smartcamera.util.FileConstants.PREFIX_VIDEO
import com.jiahan.smartcamera.util.createVideoThumbnail
import com.jiahan.smartcamera.util.safeCall
import com.jiahan.smartcamera.util.toMediaUri
import com.jiahan.smartcamera.util.toPlatformUri
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
) : MediaFileRepository, MediaCacheRepository, MediaCaptureRepository {

    override fun createPhotoUri(): MediaUri? = try {
        val timeStamp = System.currentTimeMillis()
        val imageFile =
            File.createTempFile("$PREFIX_PHOTO$timeStamp", EXTENSION_JPG, context.cacheDir)
        getUriForFile(context, FILE_PROVIDER_AUTHORITY, imageFile).toMediaUri()
    } catch (e: Exception) {
        errorHandler.logError(e)
        null
    }

    override fun createVideoUri(): MediaUri? = try {
        val timeStamp = System.currentTimeMillis()
        val videoFile =
            File.createTempFile("$PREFIX_VIDEO$timeStamp", EXTENSION_MP4, context.cacheDir)
        getUriForFile(context, FILE_PROVIDER_AUTHORITY, videoFile).toMediaUri()
    } catch (e: Exception) {
        errorHandler.logError(e)
        null
    }

    override fun createVideoThumbnail(video: MediaUri): MediaUri? =
        createVideoThumbnail(context, video.toPlatformUri())
            ?.let(::saveBitmapAsTempFile)
            ?.toMediaUri()

    /**
     * Writes [bitmap] as a temporary JPEG in the app cache and returns its file URI, recycling the
     * bitmap either way; null if the file cannot be written. Off the contract since that stopped
     * carrying a `Bitmap` -- [createVideoThumbnail] is its caller -- and `internal` so its suite can
     * still reach it.
     */
    internal fun saveBitmapAsTempFile(bitmap: Bitmap): Uri? = try {
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

    override suspend fun downloadToCacheFile(url: String, isVideo: Boolean): MediaUri? =
        withContext(ioDispatcher) {
            safeCall {
                val timeStamp = System.currentTimeMillis()
                val prefix = if (isVideo) PREFIX_VIDEO else PREFIX_PHOTO
                val extension = if (isVideo) EXTENSION_MP4 else EXTENSION_JPG
                val file = File.createTempFile("$prefix$timeStamp", extension, context.cacheDir)
                // The destination exists before the source is opened, so a failed download leaves
                // an empty file behind unless it is cleaned up here -- and this path fails on
                // exactly the input a user retries: sharing a remote photo with no connectivity.
                runCatching {
                    URL(url).openStream().use { input ->
                        FileOutputStream(file).use { output -> input.copyTo(output) }
                    }
                }.onFailure { file.delete() }.getOrThrow()
                getUriForFile(context, FILE_PROVIDER_AUTHORITY, file).toMediaUri()
            }.onFailure(errorHandler::logError).getOrNull()
        }

    override fun isVideoUri(uri: MediaUri): Boolean =
        context.contentResolver.getType(uri.toPlatformUri())
            ?.startsWith(MIME_TYPE_VIDEO_PREFIX) == true

    override fun hasContent(uri: MediaUri): Boolean = try {
        context.contentResolver.openAssetFileDescriptor(uri.toPlatformUri(), "r")
            ?.use { descriptor -> descriptor.length != 0L } == true
    } catch (e: Exception) {
        errorHandler.logError(e)
        false
    }

    override fun deleteFile(uri: MediaUri) {
        try {
            context.contentResolver.delete(uri.toPlatformUri(), null, null)
        } catch (e: Exception) {
            errorHandler.logError(e)
        }
    }
}