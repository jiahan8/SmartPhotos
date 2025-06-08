package com.jiahan.smartcamera.home

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.util.Util.formatDateTime
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel(),
    onScrollDirectionChanged: (Boolean) -> Unit = {}
) {
    val state = rememberPullToRefreshState()
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

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

    LaunchedEffect(listState) {
        // Initialize with true to show the bottom bar initially
        var prevFirstVisibleItemIndex = listState.firstVisibleItemIndex
        var prevFirstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset

        // Set bottom bar to visible initially
        onScrollDirectionChanged(true)

        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }
            .collect { (currentIndex, currentScrollOffset) ->
                // Only update direction after actual scrolling occurs
                if (currentIndex != prevFirstVisibleItemIndex || currentScrollOffset != prevFirstVisibleItemScrollOffset) {
                    val isScrollingUp = currentIndex < prevFirstVisibleItemIndex ||
                            (currentIndex == prevFirstVisibleItemIndex &&
                                    currentScrollOffset < prevFirstVisibleItemScrollOffset)

                    onScrollDirectionChanged(isScrollingUp)

                    prevFirstVisibleItemIndex = currentIndex
                    prevFirstVisibleItemScrollOffset = currentScrollOffset
                }
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
                        viewModel.deleteNote(note.documentPath)
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
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(
                            count = notes.size,
                            key = { index -> notes[index].documentPath }
                        ) { index ->
                            val note = notes[index]
                            HomeItem(
                                note = note,
                                onTap = {
                                    navController.navigate(
                                        Screen.NotePreview.createRoute(note.documentPath)
                                    )
                                },
                                onDoubleTap = {
                                    viewModel.favoriteNote(note)
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
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onLongPress: () -> Unit,
    onPhotoClick: (String) -> Unit,
    onVideoClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        onTap()
                    },
                    onDoubleTap = {
                        onDoubleTap()
                    },
                    onLongPress = {
                        onLongPress()
                    }
                )
            }
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
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            )

            Column(
                modifier = Modifier.padding(start = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = note.username,
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
                                .clickable(
                                    interactionSource = null,
                                    indication = null
                                ) {
                                    onDoubleTap()
                                },
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                note.text?.let { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 15,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (!note.mediaList.isNullOrEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                contentPadding = PaddingValues(start = 56.dp, end = 8.dp)
            ) {
                items(note.mediaList.size) { index ->
                    val mediaDetail = note.mediaList[index]
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clickable {
                                if (mediaDetail.isVideo)
                                    mediaDetail.videoUrl?.let {
                                        onVideoClick(it)
                                    }
                                else {
                                    mediaDetail.photoUrl?.let {
                                        onPhotoClick(it)
                                    }
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
        HorizontalDivider(
            modifier = Modifier.padding(top = 16.dp, start = 8.dp, end = 8.dp),
            thickness = 0.5.dp
        )
    }
}