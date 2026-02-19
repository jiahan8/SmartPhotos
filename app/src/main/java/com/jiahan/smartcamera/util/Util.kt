package com.jiahan.smartcamera.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.jiahan.smartcamera.util.AppConstants.VIDEO_THUMBNAIL_DIMENSION
import com.jiahan.smartcamera.util.AppConstants.VIDEO_THUMBNAIL_TIME_MICROSECONDS
import com.jiahan.smartcamera.util.AppConstants.VIDEO_THUMBNAIL_DEFAULT_WIDTH
import com.jiahan.smartcamera.util.AppConstants.VIDEO_THUMBNAIL_DEFAULT_HEIGHT
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Util {
    // Modern approach using java.time (thread-safe, API 26+)
    fun formatDateTime(timestamp: Long): String {
        val instant = Instant.ofEpochMilli(timestamp)
        val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
            .withLocale(Locale.getDefault())
            .withZone(ZoneId.systemDefault())
        return formatter.format(instant)
    }

    fun createVideoThumbnail(context: Context, videoUri: Uri): Bitmap? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, videoUri)

            // Get video dimensions for better quality thumbnails
            val width =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt()
                    ?: VIDEO_THUMBNAIL_DEFAULT_WIDTH
            val height =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt()
                    ?: VIDEO_THUMBNAIL_DEFAULT_HEIGHT

            // Calculate scaled dimensions maintaining aspect ratio
            val scaleFactor = if (width > height) {
                VIDEO_THUMBNAIL_DIMENSION.toFloat() / width
            } else {
                VIDEO_THUMBNAIL_DIMENSION.toFloat() / height
            }

            val scaledWidth = (width * scaleFactor).toInt()
            val scaledHeight = (height * scaleFactor).toInt()

            // Get frame at specified time
            val bitmap = retriever.getScaledFrameAtTime(
                VIDEO_THUMBNAIL_TIME_MICROSECONDS,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                scaledWidth,
                scaledHeight
            )

            retriever.release()
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}