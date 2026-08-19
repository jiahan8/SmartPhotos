package com.jiahan.smartcamera.preview

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.res.stringResource
import com.jiahan.smartcamera.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ShareCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.jiahan.smartcamera.common.showAppSnackbar
import com.jiahan.smartcamera.util.FileConstants.MIME_TYPE_VIDEO

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPreviewScreen(
    onBack: () -> Unit,
    viewModel: VideoPreviewViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState
) {
    val videoSource = viewModel.videoSource
    val context = LocalContext.current
    val isSharing by viewModel.isSharing.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.shareEvent.collect { uri ->
            ShareCompat.IntentBuilder(context)
                .setType(MIME_TYPE_VIDEO)
                .addStream(uri)
                .startChooser()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.actionError.collect { message ->
            snackbarHostState.showAppSnackbar(message, isError = true)
        }
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    viewModel.logVideoLoadError(error)
                }
            })
            when (videoSource) {
                is VideoSource.LocalUri -> setMediaItem(MediaItem.fromUri(videoSource.uri))
                is VideoSource.RemoteUrl -> setMediaItem(MediaItem.fromUri(videoSource.url))
            }
            prepare()
            playWhenReady = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx)
                        .apply {
                            player = exoPlayer
                            useController = true
                        }
                },
                modifier = Modifier.fillMaxSize()
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
                    onClick = { viewModel.shareVideo() },
                    enabled = !isSharing
                ) {
                    if (isSharing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 1.5.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = stringResource(R.string.share)
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }
}

sealed class VideoSource {
    data class LocalUri(val uri: Uri) : VideoSource()
    data class RemoteUrl(val url: String) : VideoSource()
}