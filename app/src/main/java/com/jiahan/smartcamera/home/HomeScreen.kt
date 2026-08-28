package com.jiahan.smartcamera.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ShareCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.common.DeleteNoteConfirmationDialog
import com.jiahan.smartcamera.common.FullScreenMessage
import com.jiahan.smartcamera.common.NoteItem
import com.jiahan.smartcamera.common.NoteListSkeleton
import com.jiahan.smartcamera.common.ScrollDirectionEffect
import com.jiahan.smartcamera.common.ScrollToTopEffect
import com.jiahan.smartcamera.common.rememberShouldLoadMore
import com.jiahan.smartcamera.common.showAppSnackbar
import com.jiahan.smartcamera.util.AppConstants.ANIMATION_DURATION_SHORT_MS

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToNotePreview: (noteId: String) -> Unit,
    onNavigateToEditNote: (noteId: String) -> Unit,
    onNavigateToPhotoPreview: (url: String) -> Unit,
    onNavigateToVideoPreview: (url: String) -> Unit,
    onNavigateToExplore: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    onScrollDirectionChanged: (Boolean) -> Unit = {},
    scrollToTop: Long?,
    onScrollToTopConsumed: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val pullToRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ScrollDirectionEffect(listState, onScrollDirectionChanged)

    ScrollToTopEffect(
        scrollToTop = scrollToTop,
        listState = listState,
        hasItems = uiState.notes?.isNotEmpty() == true,
        onConsumed = onScrollToTopConsumed
    )

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

    val shouldLoadMore by rememberShouldLoadMore(listState) { uiState.notes?.size ?: 0 }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !uiState.isLoadingMore) {
            viewModel.loadMoreNotes()
        }
    }

    uiState.noteToDelete?.let { note ->
        DeleteNoteConfirmationDialog(
            onDismissRequest = { viewModel.setNoteToDelete(null) },
            onConfirmDelete = {
                viewModel.deleteNote(note.noteId)
                viewModel.setNoteToDelete(null)
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                actions = {
                    if (uiState.isExploreIconVisible) {
                        IconButton(onClick = onNavigateToExplore) {
                            Icon(
                                imageVector = Icons.Outlined.Explore,
                                contentDescription = stringResource(R.string.explore)
                            )
                        }
                    }
                }
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = uiState.content,
                    modifier = Modifier.fillMaxSize(),
                    // Key on the branch, not the payload. Every favorite toggle, deletion and
                    // appended page produces a fresh Success instance; those must recompose the
                    // list in place, not crossfade the whole screen against itself.
                    contentKey = { it::class },
                    transitionSpec = {
                        fadeIn(tween(ANIMATION_DURATION_SHORT_MS)) togetherWith
                                fadeOut(tween(ANIMATION_DURATION_SHORT_MS))
                    },
                    label = "HomeContent"
                ) { state ->
                    when (state) {
                        is HomeContent.Loading -> NoteListSkeleton()

                        is HomeContent.Error -> FullScreenMessage(state.message)

                        is HomeContent.Success ->
                            if (state.notes.isEmpty()) {
                                FullScreenMessage(stringResource(R.string.create_first_note))
                            } else {
                                PullToRefreshBox(
                                    modifier = Modifier.fillMaxSize(),
                                    state = pullToRefreshState,
                                    isRefreshing = uiState.isRefreshing,
                                    onRefresh = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                        viewModel.refresh()
                                    },
                                ) {
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(
                                            count = state.notes.size,
                                            key = { index -> state.notes[index].noteId }
                                        ) { index ->
                                            val note = state.notes[index]
                                            NoteItem(
                                                note = note,
                                                modifier = Modifier.animateItem(),
                                                onNavigateToNotePreview = {
                                                    onNavigateToNotePreview(note.noteId)
                                                },
                                                onEditNote = { onNavigateToEditNote(note.noteId) },
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
                                                onShareNote = { viewModel.shareNote(note) },
                                                onImageLoadError = viewModel::logImageLoadError
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
        }
    }
}