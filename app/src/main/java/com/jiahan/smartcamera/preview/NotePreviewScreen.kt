package com.jiahan.smartcamera.preview

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
import androidx.hilt.navigation.compose.hiltViewModel
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
    val note by viewModel.note.collectAsState()
    val noteToDelete by viewModel.noteToDelete.collectAsState()

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
                windowInsets = WindowInsets(0.dp),
            )
        }
    ) { padding ->
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
                            contentDescription = "Profile Picture",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                        )
                    } ?: Image(
                        imageVector = Icons.Rounded.AccountCircle,
                        contentDescription = "Profile Picture",
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
                                text = note.createdDate?.time?.let { (formatDateTime(it)) } ?: "",
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

                val list = note.mediaList
                if (!list.isNullOrEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        contentPadding = PaddingValues(start = 56.dp, end = 8.dp)
                    ) {
                        items(list.size) { index ->
                            val mediaDetail = list[index]
                            Box(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .clickable {
                                        if (list[index].isVideo) {
                                            navController.navigate(
                                                Screen.VideoPreview.createRemoteRoute(
                                                    list[index].videoUrl.toString()
                                                )
                                            )
                                        } else {
                                            navController.navigate(
                                                Screen.PhotoPreview.createRemoteRoute(
                                                    list[index].photoUrl.toString()
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
                                    contentDescription = "Image",
                                    contentScale = ContentScale.Crop,
                                    onError = {
                                        it.result.throwable.printStackTrace()
                                    }
                                )

                                if (mediaDetail.isVideo)
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = "Play Video",
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
                        contentDescription = "Favorite",
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
                        contentDescription = "Delete",
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