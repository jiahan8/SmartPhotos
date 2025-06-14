package com.jiahan.smartcamera.preview

import android.net.Uri
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoPreviewScreen(
    photoSource: PhotoSource,
    navController: NavHostController
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                windowInsets = WindowInsets(0.dp),
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            // Track zoom and offset state
            val scale = remember { mutableFloatStateOf(1f) }
            val offsetX = remember { mutableFloatStateOf(0f) }
            val offsetY = remember { mutableFloatStateOf(0f) }

            // Create transformable state for handling gestures
            val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
                // Update scale with limits
                scale.floatValue = (scale.floatValue * zoomChange).coerceIn(1f, 10f)

                // Calculate the maximum allowed offset based on scale
                // As we zoom in (scale > 1), we allow more panning
                val maxX =
                    (scale.floatValue - 1) * 500 // Adjust this multiplier based on your image size
                val maxY = (scale.floatValue - 1) * 500

                // Update offsets with constraints
                offsetX.floatValue = (offsetX.floatValue + panChange.x).coerceIn(-maxX, maxX)
                offsetY.floatValue = (offsetY.floatValue + panChange.y).coerceIn(-maxY, maxY)
            }

            val model = when (photoSource) {
                is PhotoSource.LocalUri -> photoSource.uri
                is PhotoSource.RemoteUrl -> photoSource.url
            }
            AsyncImage(
                model = model,
                contentDescription = "Selected Image",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale.floatValue,
                        scaleY = scale.floatValue,
                        translationX = offsetX.floatValue,
                        translationY = offsetY.floatValue
                    )
                    .transformable(state = transformableState),
                onError = {
                    it.result.throwable.printStackTrace()
                }
            )
        }
    }
}

sealed class PhotoSource {
    data class LocalUri(val uri: Uri) : PhotoSource()
    data class RemoteUrl(val url: String) : PhotoSource()
}