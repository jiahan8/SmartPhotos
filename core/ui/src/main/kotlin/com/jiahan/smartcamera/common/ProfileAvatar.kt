package com.jiahan.smartcamera.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jiahan.smartcamera.core.ui.R

/**
 * A circular user profile picture, falling back to a generic account icon when
 * [profilePictureUrl] is null. Pass [onClick] to make a loaded picture tappable (e.g. to open a
 * full-screen preview); the fallback icon is never clickable since there is nothing to preview.
 */
@Composable
fun ProfileAvatar(
    profilePictureUrl: String?,
    onImageLoadError: (Throwable) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    onClick: (() -> Unit)? = null
) {
    profilePictureUrl?.let { url ->
        AsyncImage(
            model = url,
            contentDescription = stringResource(R.string.cd_profile_picture),
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .then(if (onClick != null) Modifier.bounceClick(onClick = onClick) else Modifier),
            onError = { onImageLoadError(it.result.throwable) }
        )
    } ?: Image(
        imageVector = Icons.Rounded.AccountCircle,
        contentDescription = stringResource(R.string.cd_profile_picture),
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(CircleShape),
        colorFilter = ColorFilter.tint(
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    )
}