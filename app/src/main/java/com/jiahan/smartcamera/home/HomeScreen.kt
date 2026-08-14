package com.jiahan.smartcamera.home

import android.content.ClipData
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.app.ShareCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.common.BottomSheetActionItem
import com.jiahan.smartcamera.common.CustomSnackbarHost
import com.jiahan.smartcamera.common.ScrollDirectionEffect
import com.jiahan.smartcamera.common.ScrollToTopEffect
import com.jiahan.smartcamera.common.rememberShouldLoadMore
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.domain.MediaDetail
import com.jiahan.smartcamera.ui.theme.SmartCameraTheme
import com.jiahan.smartcamera.util.AppConstants.ANIMATION_DURATION_SHORT_MS
import com.jiahan.smartcamera.util.toFormattedDateTime
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToNotePreview: (documentPath: String) -> Unit,
    onNavigateToPhotoPreview: (url: String) -> Unit,
    onNavigateToVideoPreview: (url: String) -> Unit,
    onNavigateToExplore: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    onScrollDirectionChanged: (Boolean) -> Unit = {},
    scrollToTop: Long?,
    onScrollToTopConsumed: () -> Unit
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val pullToRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ScrollDirectionEffect(listState, onScrollDirectionChanged)

    ScrollToTopEffect(
        scrollToTop = scrollToTop,
        listState = listState,
        hasItems = uiState.notes?.isNotEmpty() == true,
        onConsumed = onScrollToTopConsumed
    )

    LaunchedEffect(Unit) {
        viewModel.actionError.collect { message -> snackbarHostState.showSnackbar(message) }
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

    val shouldLoadMore by rememberShouldLoadMore(listState) { uiState.notes?.size ?: 0 }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !uiState.isLoadingMore) {
            viewModel.loadMoreNotes()
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
                actions = {
                    IconButton(onClick = onNavigateToExplore) {
                        Icon(
                            imageVector = Icons.Outlined.Explore,
                            contentDescription = stringResource(R.string.explore)
                        )
                    }
                }
            )
        },
        snackbarHost = { CustomSnackbarHost(snackbarHostState, isError = true) }
    ) { padding ->
        when (val state = uiState.content) {
            is HomeContent.Loading ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(strokeWidth = 1.5.dp)
                }

            is HomeContent.Error ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(state.message)
                }

            is HomeContent.Success ->
                if (state.notes.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.no_notes_found))
                    }
                } else {
                    PullToRefreshBox(
                        modifier = Modifier.padding(
                            top = padding.calculateTopPadding(),
                            start = padding.calculateStartPadding(LayoutDirection.Ltr),
                            end = padding.calculateEndPadding(LayoutDirection.Ltr)
                        ),
                        state = pullToRefreshState,
                        isRefreshing = uiState.isRefreshing,
                        onRefresh = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                            viewModel.refresh()
                        },
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp)
                        ) {
                            items(
                                count = state.notes.size,
                                key = { index -> state.notes[index].documentPath }
                            ) { index ->
                                val note = state.notes[index]
                                HomeItem(
                                    note = note,
                                    onNavigateToNotePreview = {
                                        onNavigateToNotePreview(note.documentPath)
                                    },
                                    onFavoriteNote = { viewModel.favoriteNote(note) },
                                    onDeleteNote = { viewModel.setNoteToDelete(note) },
                                    onPhotoClick = { url ->
                                        onNavigateToPhotoPreview(url)
                                    },
                                    onVideoClick = { url ->
                                        onNavigateToVideoPreview(url)
                                    },
                                    onProfilePictureClick = { url ->
                                        onNavigateToPhotoPreview(url)
                                    },
                                    onShareNote = { viewModel.shareNote(note) }
                                )
                            }

                            if (uiState.isLoadingMore) {
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
}

@Stable
data class HomeItemCallbacks(
    val onNavigateToNotePreview: () -> Unit,
    val onFavoriteNote: () -> Unit,
    val onDeleteNote: () -> Unit,
    val onPhotoClick: (String) -> Unit,
    val onVideoClick: (String) -> Unit,
    val onProfilePictureClick: (String) -> Unit,
    val onShareNote: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeItem(
    note: HomeNote,
    onNavigateToNotePreview: () -> Unit,
    onFavoriteNote: () -> Unit,
    onDeleteNote: () -> Unit,
    onPhotoClick: (String) -> Unit,
    onVideoClick: (String) -> Unit,
    onProfilePictureClick: (String) -> Unit,
    onShareNote: () -> Unit
) {
    val callbacks = remember(
        onNavigateToNotePreview,
        onFavoriteNote,
        onDeleteNote,
        onPhotoClick,
        onVideoClick,
        onProfilePictureClick,
        onShareNote
    ) {
        HomeItemCallbacks(
            onNavigateToNotePreview = onNavigateToNotePreview,
            onFavoriteNote = onFavoriteNote,
            onDeleteNote = onDeleteNote,
            onPhotoClick = onPhotoClick,
            onVideoClick = onVideoClick,
            onProfilePictureClick = onProfilePictureClick,
            onShareNote = onShareNote
        )
    }

    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant

    val formattedDate = remember(note.createdDate) {
        note.createdDate?.toEpochMilli()?.toFormattedDateTime() ?: ""
    }

    var showActionsSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val coroutineScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val clipboard = LocalClipboard.current

    fun openActionsSheet() {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        showActionsSheet = true
    }

    fun closeSheetThen(action: () -> Unit) {
        coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
            showActionsSheet = false
            action()
        }
    }

    Column(
        modifier = Modifier
            .pointerInput(callbacks) {
                detectTapGestures(
                    onTap = { callbacks.onNavigateToNotePreview() },
                    onDoubleTap = { callbacks.onFavoriteNote() },
                    onLongPress = { openActionsSheet() }
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp)
        ) {
            note.profilePictureUrl?.let { profilePictureUrl ->
                AsyncImage(
                    model = profilePictureUrl,
                    contentDescription = stringResource(R.string.cd_profile_picture),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable {
                            callbacks.onProfilePictureClick(profilePictureUrl)
                        }
                )
            } ?: Image(
                imageVector = Icons.Rounded.AccountCircle,
                contentDescription = stringResource(R.string.cd_profile_picture),
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
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

                        AnimatedVisibility(
                            visible = note.favorite,
                            enter = scaleIn(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                ),
                                initialScale = 5f
                            ) + fadeIn(tween(ANIMATION_DURATION_SHORT_MS)),
                            exit = scaleOut(tween(ANIMATION_DURATION_SHORT_MS)) +
                                    fadeOut(tween(ANIMATION_DURATION_SHORT_MS))
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Favorite,
                                contentDescription = stringResource(R.string.cd_marked_as_favorite),
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable(
                                        interactionSource = null,
                                        indication = null
                                    ) {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        callbacks.onFavoriteNote()
                                    },
                                tint = primaryColor
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Rounded.MoreHoriz,
                        contentDescription = stringResource(R.string.cd_more_options),
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(18.dp)
                            .clickable(
                                interactionSource = null,
                                indication = null
                            ) {
                                openActionsSheet()
                            },
                        tint = onSurfaceVariantColor
                    )
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

    if (showActionsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showActionsSheet = false },
            sheetState = sheetState
        ) {
            BottomSheetActionItem(
                icon = Icons.Outlined.FavoriteBorder,
                label = if (note.favorite)
                    stringResource(R.string.remove_like)
                else
                    stringResource(R.string.like),
                onClick = { closeSheetThen(callbacks.onFavoriteNote) }
            )
            BottomSheetActionItem(
                icon = Icons.Outlined.Share,
                label = stringResource(R.string.share),
                onClick = { closeSheetThen(callbacks.onShareNote) }
            )
            note.text?.let { text ->
                BottomSheetActionItem(
                    icon = Icons.Outlined.ContentCopy,
                    label = stringResource(R.string.copy_text),
                    onClick = {
                        closeSheetThen {
                            coroutineScope.launch {
                                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, text)))
                            }
                        }
                    }
                )
            }
            BottomSheetActionItem(
                icon = Icons.Outlined.Delete,
                label = stringResource(R.string.delete),
                onClick = { closeSheetThen(callbacks.onDeleteNote) },
                tint = MaterialTheme.colorScheme.error
            )
        }
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
    val isVideo = mediaDetail.isVideo
    val imageUrl = if (isVideo) mediaDetail.thumbnailUrl else mediaDetail.photoUrl
    val mediaUrl = if (isVideo) mediaDetail.videoUrl else mediaDetail.photoUrl

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
            contentDescription = stringResource(R.string.cd_note_photo),
            contentScale = ContentScale.Crop,
            onError = {
                it.result.throwable.printStackTrace()
            }
        )

        if (isVideo) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = stringResource(R.string.cd_play_video),
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

@Preview(showBackground = true, name = "HomeItem – text only")
@Composable
private fun HomeItemPreview() {
    SmartCameraTheme {
        HomeItem(
            note = HomeNote(
                documentPath = "preview/1",
                username = "john_doe",
                text = "Hello, this is a preview note with some sample text that wraps across multiple lines.",
                mediaList = null,
                profilePictureUrl = null,
                favorite = false,
                createdDate = null
            ),
            onNavigateToNotePreview = {},
            onFavoriteNote = {},
            onDeleteNote = {},
            onPhotoClick = {},
            onVideoClick = {},
            onProfilePictureClick = {},
            onShareNote = {}
        )
    }
}

@Preview(showBackground = true, name = "HomeItem – favorited")
@Composable
private fun HomeItemFavoritedPreview() {
    SmartCameraTheme {
        HomeItem(
            note = HomeNote(
                documentPath = "preview/2",
                username = "jane_doe",
                text = "This note is marked as a favourite.",
                mediaList = null,
                profilePictureUrl = null,
                favorite = true,
                createdDate = null
            ),
            onNavigateToNotePreview = {},
            onFavoriteNote = {},
            onDeleteNote = {},
            onPhotoClick = {},
            onVideoClick = {},
            onProfilePictureClick = {},
            onShareNote = {}
        )
    }
}