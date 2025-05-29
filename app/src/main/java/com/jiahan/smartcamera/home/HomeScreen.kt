package com.jiahan.smartcamera.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.Screen
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.util.Util.formatDateTime
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state = rememberPullToRefreshState()
    val coroutineScope = rememberCoroutineScope()

    val notes by viewModel.notes.collectAsState()
    val isInitialLoading by viewModel.isInititalLoading.collectAsState()
    val isRefreshing by viewModel.refreshing.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val noteToDelete by viewModel.noteToDelete.collectAsState()

    val onRefresh: () -> Unit = {
        coroutineScope.launch {
            viewModel.setRefreshing(true)
            viewModel.fetchNotes(initialLoading = true)
            viewModel.setRefreshing(false)
        }
    }

    noteToDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { viewModel.setNoteToDelete(null) },
            title = { Text(stringResource(R.string.delete_note)) },
            text = { Text(stringResource(R.string.delete_note_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        note.documentPath?.let { viewModel.deleteNote(it) }
                        viewModel.setNoteToDelete(null)
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
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                windowInsets = WindowInsets(0.dp),
            )
        }
    ) { innerPadding ->
        if (isInitialLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            PullToRefreshBox(
                modifier = Modifier.padding(
                    top = innerPadding.calculateTopPadding(),
                    start = innerPadding.calculateStartPadding(LayoutDirection.Ltr),
                    end = innerPadding.calculateEndPadding(LayoutDirection.Ltr)
                ),
                state = state,
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
            ) {
                if (notes.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.no_notes_found))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(notes.size) { index ->
                            val note = notes[index]
                            HomeItem(
                                note = note,
                                onDoubleTap = {
                                    note.documentPath?.let {
                                        viewModel.favoriteNote(note)
                                    }
                                },
                                onLongPress = {
                                    viewModel.setNoteToDelete(note)
                                },
                                onPhotoClick = { url ->
                                    navController.navigate(
                                        Screen.PhotoPreview.createRemoteRoute(url)
                                    )
                                },
                                onVideoClick = { url ->
                                    navController.navigate(
                                        Screen.VideoPreview.createRemoteRoute(url)
                                    )
                                }
                            )

                            if (index >= notes.size - 1 && !isLoadingMore) {
                                LaunchedEffect(key1 = Unit) {
                                    viewModel.loadMoreNotes()
                                }
                            }
                        }

                        if (isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(32.dp),
                                        strokeWidth = 2.dp
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeItem(
    note: HomeNote,
    onDoubleTap: () -> Unit,
    onLongPress: () -> Unit,
    onPhotoClick: (String) -> Unit,
    onVideoClick: (String) -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            onDoubleTap()
                        },
                        onLongPress = {
                            onLongPress()
                        }
                    )
                }
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)
        ) {
            AsyncImage(
                model = R.drawable.home_image,
                contentDescription = "Profile picture",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
            )

            Column(
                modifier = Modifier.padding(start = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "jiahan",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )

                    Text(
                        text = note.createdDate?.time?.let { (formatDateTime(it)) } ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    if (note.favorite) {
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = "Favorite",
                            modifier = Modifier
                                .size(16.dp)
                                .clickable {
                                    onDoubleTap()
                                },
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                note.text?.let { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                note.mediaUrlList?.let { urlList ->
                    HorizontalMultiBrowseCarousel(
                        state = rememberCarouselState { urlList.count() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .padding(top = 16.dp, bottom = 16.dp),
                        preferredItemWidth = 186.dp,
                        itemSpacing = 8.dp,
                    ) { index ->
                        val url = urlList[index]
                        val isVideo = url.contains(".mp4")
                        Box(
                            modifier = Modifier.clickable {
                                if (isVideo)
                                    onVideoClick(url)
                                else {
                                    onPhotoClick(url)
                                }
                            }
                        ) {
                            AsyncImage(
                                model = url,
                                modifier = Modifier
                                    .height(205.dp)
                                    .maskClip(MaterialTheme.shapes.extraLarge),
                                contentDescription = "Image",
                                contentScale = ContentScale.Crop,
                                onError = {
                                    it.result.throwable.printStackTrace()
                                }
                            )

                            if (isVideo)
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = "Play video",
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
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 8.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}