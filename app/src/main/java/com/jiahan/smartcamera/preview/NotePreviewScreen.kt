package com.jiahan.smartcamera.preview

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import com.jiahan.smartcamera.common.CustomSnackbarHost
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.Screen
import com.jiahan.smartcamera.util.Util.formatDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotePreviewScreen(
    navController: NavController,
    viewModel: NotePreviewViewModel = hiltViewModel()
) {
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val noteToDelete by viewModel.noteToDelete.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.actionError.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    noteToDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { viewModel.setNoteToDelete(null) },
            title = { Text(stringResource(R.string.delete_note)) },
            text = { Text(stringResource(R.string.delete_note_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteNote(note.documentPath)
                        viewModel.setNoteToDelete(null)
                        navController.popBackStack()
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.note),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        },
        snackbarHost = { CustomSnackbarHost(snackbarHostState, isError = true) }
    ) { padding ->
        when (val state = uiState) {
            is NotePreviewUiState.Loading ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(strokeWidth = 1.5.dp)
                }

            is NotePreviewUiState.Error ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(state.message)
                }

            is NotePreviewUiState.Success -> {
                val note = state.note
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = padding.calculateTopPadding(),
                            start = padding.calculateStartPadding(LayoutDirection.Ltr),
                            end = padding.calculateEndPadding(LayoutDirection.Ltr)
                        )
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
                                            navController.navigate(
                                                Screen.PhotoPreview.createRemoteRoute(it)
                                            )
                                        }
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
                                        text = note.createdDate?.time?.let { (formatDateTime(it)) }
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
                                                    navController.navigate(
                                                        Screen.VideoPreview.createRemoteRoute(
                                                            mediaList[index].videoUrl.toString()
                                                        )
                                                    )
                                                } else {
                                                    navController.navigate(
                                                        Screen.PhotoPreview.createRemoteRoute(
                                                            mediaList[index].photoUrl.toString()
                                                        )
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
                                            contentDescription = stringResource(R.string.cd_image),
                                            contentScale = ContentScale.Crop,
                                            onError = {
                                                it.result.throwable.printStackTrace()
                                            }
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
                                .padding(start = 68.dp, end = 16.dp, top = 16.dp)
                        ) {
                            Icon(
                                painter = painterResource(if (note.favorite) R.drawable.favorite else R.drawable.favorite_outlined),
                                contentDescription = stringResource(R.string.favorite),
                                modifier = Modifier
                                    .clickable(
                                        interactionSource = null,
                                        indication = null
                                    ) {
                                        viewModel.favoriteNote(note)
                                    },
                                tint = if (note.favorite) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )

                            Icon(
                                painter = painterResource(R.drawable.delete),
                                contentDescription = stringResource(R.string.delete),
                                modifier = Modifier
                                    .padding(start = 6.dp)
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