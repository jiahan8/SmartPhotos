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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.Screen
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.domain.MediaDetail
import com.jiahan.smartcamera.util.Util.formatDateTime
import com.jiahan.smartcamera.util.pairwise
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel(),
    onScrollDirectionChanged: (Boolean) -> Unit = {},
    scrollToTop: Long?,
    onScrollToTopConsumed: () -> Unit
) {
    val pullToRefreshState = rememberPullToRefreshState()
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val isInitialLoading by viewModel.isInitialLoading.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isLoadingMore.collectAsStateWithLifecycle()
    val noteToDelete by viewModel.noteToDelete.collectAsStateWithLifecycle()

    val onRefresh: () -> Unit = {
        coroutineScope.launch {
            viewModel.setRefreshing(true)
            viewModel.fetchNotes(initialLoading = true)
            viewModel.setRefreshing(false)
        }
    }

    LaunchedEffect(listState) {
        onScrollDirectionChanged(true)
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .pairwise()
            .map { (prev, curr) ->
                val (prevIndex, prevOffset) = prev
                val (currIndex, currOffset) = curr
                currIndex < prevIndex || (currIndex == prevIndex && currOffset < prevOffset)
            }
            .distinctUntilChanged()
            .collect { isScrollingUp ->
                onScrollDirectionChanged(isScrollingUp)
            }
    }

    LaunchedEffect(scrollToTop) {
        scrollToTop?.let {
            if (notes.isNotEmpty()) {
                listState.animateScrollToItem(0)
                onScrollToTopConsumed()
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
                }
            )
        }
    ) { padding ->
        when {
            isInitialLoading ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        strokeWidth = 1.5.dp
                    )
                }

            notes.isEmpty() ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.no_notes_found))
                }

            else ->
                PullToRefreshBox(
                    modifier = Modifier.padding(
                        top = padding.calculateTopPadding(),
                        start = padding.calculateStartPadding(LayoutDirection.Ltr),
                        end = padding.calculateEndPadding(LayoutDirection.Ltr)
                    ),
                    state = pullToRefreshState,
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 76.dp)
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
                                },
                                onProfilePictureClick = { url ->
                                    navController.navigate(
                                        Screen.PhotoPreview.createRemoteRoute(url)
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
                                        strokeWidth = 1.5.dp
                                    )
                                }
                            }
                        }
                    }
                }
        }
    }
}

@Stable
data class HomeItemCallbacks(
    val onTap: () -> Unit,
    val onDoubleTap: () -> Unit,
    val onLongPress: () -> Unit,
    val onPhotoClick: (String) -> Unit,
    val onVideoClick: (String) -> Unit,
    val onProfilePictureClick: (String) -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeItem(
    note: HomeNote,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onLongPress: () -> Unit,
    onPhotoClick: (String) -> Unit,
    onVideoClick: (String) -> Unit,
    onProfilePictureClick: (String) -> Unit
) {
    val callbacks = remember(
        onTap,
        onDoubleTap,
        onLongPress,
        onPhotoClick,
        onVideoClick,
        onProfilePictureClick
    ) {
        HomeItemCallbacks(
            onTap = onTap,
            onDoubleTap = onDoubleTap,
            onLongPress = onLongPress,
            onPhotoClick = onPhotoClick,
            onVideoClick = onVideoClick,
            onProfilePictureClick = onProfilePictureClick
        )
    }

    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant

    val formattedDate = remember(note.createdDate) {
        note.createdDate?.time?.let { formatDateTime(it) } ?: ""
    }

    Column(
        modifier = Modifier
            .pointerInput(callbacks) {
                detectTapGestures(
                    onTap = { callbacks.onTap() },
                    onDoubleTap = { callbacks.onDoubleTap() },
                    onLongPress = { callbacks.onLongPress() }
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp)
        ) {
            note.profilePictureUrl?.let { profileUrl ->
                AsyncImage(
                    model = profileUrl,
                    contentDescription = "Profile Picture",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable {
                            callbacks.onProfilePictureClick(profileUrl)
                        }
                )
            } ?: Image(
                imageVector = Icons.Rounded.AccountCircle,
                contentDescription = "Profile Picture",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape),
                colorFilter = ColorFilter.tint(onSurfaceColor.copy(alpha = 0.7f))
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
                        text = formattedDate,
                        style = MaterialTheme.typography.bodySmall,
                        color = onSurfaceVariantColor,
                        modifier = Modifier.padding(start = 8.dp),
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.width(8.dp))

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
                                    callbacks.onDoubleTap()
                                },
                            tint = primaryColor
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
                        "${note.documentPath}_${index}_${if (media.isVideo) media.videoUrl else media.photoUrl}"
                    }
                ) { index ->
                    MediaItem(
                        mediaDetail = mediaList[index],
                        onPhotoClick = callbacks.onPhotoClick,
                        onVideoClick = callbacks.onVideoClick,
                        surfaceVariantColor = surfaceVariantColor,
                        onSurfaceVariantColor = onSurfaceVariantColor
                    )
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = 16.dp, start = 8.dp, end = 8.dp),
            thickness = 0.5.dp
        )
    }
}

@Composable
private fun MediaItem(
    mediaDetail: MediaDetail,
    onPhotoClick: (String) -> Unit,
    onVideoClick: (String) -> Unit,
    surfaceVariantColor: Color,
    onSurfaceVariantColor: Color
) {
    val isVideo = remember(mediaDetail) {
        mediaDetail.isVideo
    }

    val imageUrl = remember(mediaDetail) {
        if (isVideo) {
            mediaDetail.thumbnailUrl
        } else {
            mediaDetail.photoUrl
        }
    }

    val mediaUrl = remember(mediaDetail) {
        if (isVideo) {
            mediaDetail.videoUrl
        } else {
            mediaDetail.photoUrl
        }
    }

    Box(
        modifier = Modifier
            .padding(end = 8.dp)
            .clickable {
                mediaUrl?.takeIf { it.isNotEmpty() }?.let { url ->
                    if (isVideo) {
                        onVideoClick(url)
                    } else {
                        onPhotoClick(url)
                    }
                }
            }
    ) {
        AsyncImage(
            model = imageUrl,
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

        if (isVideo) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = "Play Video",
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(surfaceVariantColor.copy(alpha = 0.7f)),
                tint = onSurfaceVariantColor
            )
        }
    }
}