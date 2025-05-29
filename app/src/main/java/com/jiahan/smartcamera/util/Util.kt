package com.jiahan.smartcamera.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
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
                    ?: 640
            val height =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt()
                    ?: 360

            // Calculate scaled dimensions maintaining aspect ratio
            val maxDimension = 1080 // Higher resolution for better quality
            val scaleFactor = if (width > height) {
                maxDimension.toFloat() / width
            } else {
                maxDimension.toFloat() / height
            }

            val scaledWidth = (width * scaleFactor).toInt()
            val scaledHeight = (height * scaleFactor).toInt()

            // Get frame at 1 second or first frame if video is shorter
            val bitmap = retriever.getScaledFrameAtTime(
                1000000, // 1 second in microseconds
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