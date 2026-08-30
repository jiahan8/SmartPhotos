package com.jiahan.smartcamera.preview

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.app.ShareCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.common.shimmer
import com.jiahan.smartcamera.common.showAppSnackbar
import com.jiahan.smartcamera.core.ui.R as UiR
import com.jiahan.smartcamera.util.AppConstants.ANIMATION_DURATION_SHORT_MS
import com.jiahan.smartcamera.util.FileConstants.MIME_TYPE_IMAGE
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoPreviewScreen(
    onBack: () -> Unit,
    viewModel: PhotoPreviewViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState
) {
    val photoSource = viewModel.photoSource
    val context = LocalContext.current
    val isSharing by viewModel.isSharing.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.shareEvent.collect { uri ->
            ShareCompat.IntentBuilder(context)
                .setType(MIME_TYPE_IMAGE)
                .addStream(uri)
                .startChooser()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.actionError.collect { message ->
            snackbarHostState.showAppSnackbar(message, isError = true)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val scope = rememberCoroutineScope()
            val scale = remember { Animatable(1f) }
            val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

            // Min and max scale values
            val minScale = 1f
            val maxScale = 8f

            // Remember the size of the image composable
            var composableSize by remember { mutableStateOf(IntSize.Zero) }

            // Whether the photo is still being decoded/downloaded by Coil
            var isPhotoLoading by remember { mutableStateOf(true) }

            // State for handling pinch-to-zoom and pan gestures
            val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
                scope.launch {
                    // Update the scale with the zoom change, coerced within min/max bounds
                    val newScale = (scale.value * zoomChange).coerceIn(minScale, maxScale)
                    scale.snapTo(newScale)

                    // If the image is zoomed in, update the offset for panning
                    if (newScale > 1f) {
                        val maxOffsetX = (composableSize.width * (newScale - 1)) / 2f
                        val maxOffsetY = (composableSize.height * (newScale - 1)) / 2f

                        // Calculate the new offset and coerce it within the allowed bounds
                        val newOffset = offset.value + panChange
                        offset.snapTo(
                            Offset(
                                x = newOffset.x.coerceIn(-maxOffsetX, maxOffsetX),
                                y = newOffset.y.coerceIn(-maxOffsetY, maxOffsetY)
                            )
                        )
                    } else {
                        // If not zoomed in, reset the offset
                        offset.snapTo(Offset.Zero)
                    }
                }
            }

            // Modifier for handling double-tap gestures
            val doubleTapModifier = Modifier.pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        scope.launch {
                            if (scale.value > minScale) {
                                // If already zoomed in, animate back to the default state (zoomed out)
                                launch {
                                    scale.animateTo(
                                        minScale,
                                        animationSpec = tween(ANIMATION_DURATION_SHORT_MS)
                                    )
                                }
                                launch {
                                    offset.animateTo(
                                        Offset.Zero,
                                        animationSpec = tween(ANIMATION_DURATION_SHORT_MS)
                                    )
                                }
                            } else {
                                // If zoomed out, animate to a zoomed-in state, focusing on the tapped point
                                val targetScale = 3f
                                launch {
                                    scale.animateTo(
                                        targetScale,
                                        animationSpec = tween(ANIMATION_DURATION_SHORT_MS)
                                    )
                                }

                                // Calculate the offset to center the tapped point on the screen
                                val newOffset = Offset(
                                    x = (targetScale - 1) * (size.width / 2f - tapOffset.x),
                                    y = (targetScale - 1) * (size.height / 2f - tapOffset.y)
                                )

                                // Calculate the maximum allowed offset for the new scale
                                val maxOffsetX = (size.width * (targetScale - 1)) / 2f
                                val maxOffsetY = (size.height * (targetScale - 1)) / 2f

                                // Animate the offset change, ensuring it stays within bounds
                                launch {
                                    offset.animateTo(
                                        Offset(
                                            x = newOffset.x.coerceIn(-maxOffsetX, maxOffsetX),
                                            y = newOffset.y.coerceIn(-maxOffsetY, maxOffsetY)
                                        ),
                                        animationSpec = tween(ANIMATION_DURATION_SHORT_MS)
                                    )
                                }
                            }
                        }
                    }
                )
            }

            val model = when (photoSource) {
                is PhotoSource.LocalUri -> photoSource.uri
                is PhotoSource.RemoteUrl -> photoSource.url
            }
            // Drawn before (and therefore behind) the photo, so it never tints the image
            // during the frame where both are on screen.
            if (isPhotoLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .shimmer(RectangleShape)
                )
            }

            AsyncImage(
                model = model,
                contentDescription = stringResource(R.string.cd_selected_photo),
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { composableSize = it } // Update the composable size
                    .graphicsLayer(
                        scaleX = scale.value,
                        scaleY = scale.value,
                        translationX = offset.value.x,
                        translationY = offset.value.y
                    )
                    .then(doubleTapModifier)
                    .transformable(state = transformableState),
                onLoading = { isPhotoLoading = true },
                onSuccess = { isPhotoLoading = false },
                onError = {
                    isPhotoLoading = false
                    viewModel.logImageLoadError(it.result.throwable)
                }
            )
        }
        TopAppBar(
            title = {},
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back)
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = { viewModel.sharePhoto() },
                    enabled = !isSharing
                ) {
                    if (isSharing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 1.5.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.IosShare,
                            contentDescription = stringResource(UiR.string.share)
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )
    }
}