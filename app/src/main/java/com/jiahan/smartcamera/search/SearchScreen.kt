package com.jiahan.smartcamera.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ShareCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.common.ScrollDirectionEffect
import com.jiahan.smartcamera.common.ScrollToTopEffect
import com.jiahan.smartcamera.common.SearchBar
import com.jiahan.smartcamera.common.rememberCyclingPlaceholder
import com.jiahan.smartcamera.common.showAppSnackbar
import com.jiahan.smartcamera.home.HomeItem
import com.jiahan.smartcamera.home.HomeListSkeleton
import com.jiahan.smartcamera.util.AppConstants.ANIMATION_DURATION_SHORT_MS

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateToNotePreview: (noteId: String) -> Unit,
    onNavigateToEditNote: (noteId: String) -> Unit,
    onNavigateToPhotoPreview: (url: String) -> Unit,
    onNavigateToVideoPreview: (url: String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
    onScrollDirectionChanged: (Boolean) -> Unit = {},
    scrollToTop: Long?,
    onScrollToTopConsumed: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val pullToRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    val (placeholder, placeholderAlpha) = rememberCyclingPlaceholder(
        options = listOf(
            stringResource(R.string.search_notes_photos),
            stringResource(R.string.find_photos),
            stringResource(R.string.find_notes),
            stringResource(R.string.what_you_looking_for),
            stringResource(R.string.look_up)
        )
    )

    ScrollDirectionEffect(listState, onScrollDirectionChanged)

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

    ScrollToTopEffect(
        scrollToTop = scrollToTop,
        listState = listState,
        hasItems = uiState.notes?.isNotEmpty() == true,
        onConsumed = onScrollToTopConsumed
    )

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
            SearchBar(
                searchQuery = searchQuery,
                onSearchQueryChange = { text -> viewModel.updateSearchQuery(text) },
                placeholder = {
                    Text(
                        text = placeholder,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.graphicsLayer(alpha = placeholderAlpha)
                    )
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
                    contentKey = { it::class },
                    transitionSpec = {
                        fadeIn(tween(ANIMATION_DURATION_SHORT_MS)) togetherWith
                                fadeOut(tween(ANIMATION_DURATION_SHORT_MS))
                    },
                    label = "SearchContent"
                ) { state ->
                    when (state) {
                        is SearchContent.Idle ->
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(stringResource(R.string.no_results_found))
                            }

                        is SearchContent.Loading -> HomeListSkeleton()

                        is SearchContent.Error ->
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(state.message)
                            }

                        is SearchContent.Success ->
                            if (state.notes.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(stringResource(R.string.no_results_found))
                                }
                            } else {
                                PullToRefreshBox(
                                    modifier = Modifier.fillMaxSize(),
                                    state = pullToRefreshState,
                                    isRefreshing = uiState.isRefreshing,
                                    onRefresh = { viewModel.refresh() },
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
                                            HomeItem(
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
                                                onShareNote = { viewModel.shareNote(note) }
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