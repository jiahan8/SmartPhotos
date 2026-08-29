package com.jiahan.smartcamera.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.jiahan.smartcamera.util.AppConstants.VIDEO_THUMBNAIL_DEFAULT_HEIGHT
import com.jiahan.smartcamera.util.AppConstants.VIDEO_THUMBNAIL_DEFAULT_WIDTH
import com.jiahan.smartcamera.util.AppConstants.VIDEO_THUMBNAIL_DIMENSION
import com.jiahan.smartcamera.util.AppConstants.VIDEO_THUMBNAIL_TIME_MICROSECONDS

/**
 * Extracts a scaled thumbnail [Bitmap] from the video at [videoUri].
 *
 * The frame is taken at [VIDEO_THUMBNAIL_TIME_MICROSECONDS] and scaled so the
 * longest edge equals [VIDEO_THUMBNAIL_DIMENSION], preserving aspect ratio.
 *
 * Any exception is propagated to the caller; wrap this call in [safeCall] when
 * using from a repository.
 */
fun createVideoThumbnail(context: Context, videoUri: Uri): Bitmap? {
    val retriever = MediaMetadataRetriever()
    try {
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
        return retriever.getScaledFrameAtTime(
            VIDEO_THUMBNAIL_TIME_MICROSECONDS,
            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            scaledWidth,
            scaledHeight
        )
    } finally {
        retriever.release()
    }
}