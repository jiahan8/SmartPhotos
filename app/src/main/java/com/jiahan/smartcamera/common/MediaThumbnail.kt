package com.jiahan.smartcamera.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.domain.MediaDetail

/**
 * A single note-media thumbnail (220x256, with 8dp trailing spacing for a LazyRow) showing a
 * [shimmer] placeholder while the image (or, for a video, its thumbnail) loads and a play-icon
 * overlay for videos.
 */
@Composable
fun MediaThumbnail(
    mediaDetail: MediaDetail,
    onPhotoClick: (String) -> Unit,
    onVideoClick: (String) -> Unit,
    onImageLoadError: (Throwable) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isVideo = mediaDetail.isVideo
    val imageUrl = if (isVideo) mediaDetail.thumbnailUrl else mediaDetail.photoUrl
    val mediaUrl = if (isVideo) mediaDetail.videoUrl else mediaDetail.photoUrl

    // Keyed on imageUrl so a recycled LazyRow slot resets to loading for its new media instead of
    // keeping the previous item's loaded state.
    var isImageLoading by remember(imageUrl) { mutableStateOf(true) }

    Box(
        modifier =
            modifier
                .padding(end = 8.dp)
                .bounceClick {
                    mediaUrl?.takeIf { it.isNotEmpty() }?.let { url ->
                        if (isVideo) {
                            onVideoClick(url)
                        } else {
                            onPhotoClick(url)
                        }
                    }
                },
    ) {
        // Drawn before (and therefore behind) the media, so it never tints the image during the
        // frame where both are on screen.
        if (isImageLoading) {
            Box(
                modifier =
                    Modifier
                        .height(256.dp)
                        .width(220.dp)
                        .shimmer(MaterialTheme.shapes.medium),
            )
        }

        AsyncImage(
            model = imageUrl,
            modifier =
                Modifier
                    .height(256.dp)
                    .width(220.dp)
                    .clip(MaterialTheme.shapes.medium),
            contentDescription = stringResource(R.string.cd_note_photo),
            contentScale = ContentScale.Crop,
            onLoading = { isImageLoading = true },
            onSuccess = { isImageLoading = false },
            onError = {
                isImageLoading = false
                onImageLoadError(it.result.throwable)
            },
        )

        if (isVideo) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = stringResource(R.string.cd_play_video),
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}