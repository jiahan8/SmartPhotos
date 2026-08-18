package com.jiahan.smartcamera.preview

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ShareCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.common.showAppSnackbar
import com.jiahan.smartcamera.util.AppConstants.ANIMATION_DURATION_SHORT_MS
import com.jiahan.smartcamera.util.toFormattedDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotePreviewScreen(
    onBack: () -> Unit,
    onNavigateToPhotoPreview: (url: String) -> Unit,
    onNavigateToVideoPreview: (url: String) -> Unit,
    viewModel: NotePreviewViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val scrollState = rememberScrollState()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.actionError.collect { message ->
            snackbarHostState.showAppSnackbar(message, isError = true)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.shareEvent.collect { shareContent ->
            val intentBuilder = ShareCompat.IntentBuilder(context)
                .setType(if (shareContent.uris.isEmpty()) "text/plain" else "*/*")
            shareContent.text?.let { intentBuilder.setText(it) }
            shareContent.uris.forEach { intentBuilder.addStream(it) }
            intentBuilder.startChooser()
        }
    }

    uiState.noteToDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { viewModel.setNoteToDelete(null) },
            title = { Text(stringResource(R.string.delete_note)) },
            text = { Text(stringResource(R.string.delete_note_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteNote(note.noteId)
                        viewModel.setNoteToDelete(null)
                        onBack()
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.setNoteToDelete(null) }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.note),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (val state = uiState.content) {
                    is NotePreviewContent.Loading ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(strokeWidth = 1.5.dp)
                        }

                    is NotePreviewContent.Error ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(state.message)
                        }

                    is NotePreviewContent.Success -> {
                        val note = state.note
                        Box(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(scrollState)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                                ) {
                                    note.profilePictureUrl?.let {
                                        AsyncImage(
                                            model = it,
                                            contentDescription = stringResource(R.string.cd_profile_picture),
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .clickable {
                                                    onNavigateToPhotoPreview(it)
                                                },
                                            onError = { viewModel.logImageLoadError(it.result.throwable) }
                                        )
                                    } ?: Image(
                                        imageVector = Icons.Rounded.AccountCircle,
                                        contentDescription = stringResource(R.string.cd_profile_picture),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape),
                                        colorFilter = ColorFilter.tint(
                                            MaterialTheme.colorScheme.onSurface.copy(
                                                alpha = 0.7f
                                            )
                                        )
                                    )

                                    Column(
                                        modifier = Modifier.padding(start = 16.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = note.username,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )

                                            Text(
                                                text = note.createdDate?.toEpochMilli()
                                                    ?.toFormattedDateTime()
                                                    ?: "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(start = 8.dp),
                                                maxLines = 1
                                            )
                                        }

                                        note.text?.let { text ->
                                            Text(
                                                text = text,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                }

                                note.mediaList?.takeIf { it.isNotEmpty() }?.let { mediaList ->
                                    LazyRow(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        contentPadding = PaddingValues(start = 56.dp, end = 8.dp)
                                    ) {
                                        items(
                                            count = mediaList.size,
                                            key = { index ->
                                                val media = mediaList[index]
                                                "${index}_${if (media.isVideo) media.videoUrl else media.photoUrl}"
                                            }
                                        ) { index ->
                                            val mediaDetail = mediaList[index]
                                            Box(
                                                modifier = Modifier
                                                    .padding(end = 8.dp)
                                                    .clickable {
                                                        if (mediaList[index].isVideo) {
                                                            onNavigateToVideoPreview(
                                                                mediaList[index].videoUrl.toString()
                                                            )
                                                        } else {
                                                            onNavigateToPhotoPreview(
                                                                mediaList[index].photoUrl.toString()
                                                            )
                                                        }
                                                    }
                                            ) {
                                                AsyncImage(
                                                    model = if (mediaDetail.isVideo) mediaDetail.thumbnailUrl else mediaDetail.photoUrl,
                                                    modifier = Modifier
                                                        .height(256.dp)
                                                        .width(220.dp)
                                                        .clip(MaterialTheme.shapes.medium),
                                                    contentDescription = stringResource(R.string.cd_note_photo),
                                                    contentScale = ContentScale.Crop,
                                                    onError = { viewModel.logImageLoadError(it.result.throwable) }
                                                )

                                                if (mediaDetail.isVideo)
                                                    Icon(
                                                        imageVector = Icons.Rounded.PlayArrow,
                                                        contentDescription = stringResource(R.string.cd_play_video),
                                                        modifier = Modifier
                                                            .align(Alignment.Center)
                                                            .size(52.dp)
                                                            .clip(CircleShape)
                                                            .background(
                                                                MaterialTheme.colorScheme.surfaceVariant.copy(
                                                                    alpha = 0.7f
                                                                )
                                                            ),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 68.dp, end = 16.dp, top = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clickable(
                                                interactionSource = null,
                                                indication = null,
                                                role = Role.Button,
                                                onClickLabel = stringResource(R.string.favorite)
                                            ) {
                                                hapticFeedback.performHapticFeedback(
                                                    HapticFeedbackType.TextHandleMove
                                                )
                                                viewModel.favoriteNote(note)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AnimatedContent(
                                            targetState = note.favorite,
                                            transitionSpec = {
                                                (scaleIn(
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessLow
                                                    ),
                                                    initialScale = 0.5f
                                                ) + fadeIn(tween(ANIMATION_DURATION_SHORT_MS)))
                                                    .togetherWith(
                                                        scaleOut(tween(ANIMATION_DURATION_SHORT_MS)) +
                                                                fadeOut(
                                                                    tween(
                                                                        ANIMATION_DURATION_SHORT_MS
                                                                    )
                                                                )
                                                    )
                                                    .using(SizeTransform(clip = false))
                                            },
                                            label = "favoriteIconAnimation"
                                        ) { isFavorite ->
                                            Icon(
                                                painter = painterResource(if (isFavorite) R.drawable.favorite else R.drawable.favorite_outlined),
                                                contentDescription = null,
                                                tint = if (isFavorite) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                            )
                                        }
                                    }

                                    Icon(
                                        painter = painterResource(R.drawable.share),
                                        contentDescription = stringResource(R.string.share),
                                        modifier = Modifier
                                            .padding(start = 8.dp)
                                            .clickable {
                                                viewModel.shareNote(note)
                                            }
                                    )

                                    Icon(
                                        painter = painterResource(R.drawable.delete),
                                        contentDescription = stringResource(R.string.delete),
                                        modifier = Modifier
                                            .padding(start = 8.dp)
                                            .clickable {
                                                viewModel.setNoteToDelete(note)
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}