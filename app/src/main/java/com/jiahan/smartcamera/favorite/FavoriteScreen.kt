package com.jiahan.smartcamera.favorite

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ShareCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.common.DeleteNoteConfirmationDialog
import com.jiahan.smartcamera.common.FullScreenMessage
import com.jiahan.smartcamera.common.ScrollDirectionEffect
import com.jiahan.smartcamera.common.ScrollToTopEffect
import com.jiahan.smartcamera.common.SearchBar
import com.jiahan.smartcamera.common.showAppSnackbar
import com.jiahan.smartcamera.home.HomeItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteScreen(
    onNavigateToNotePreview: (noteId: String) -> Unit,
    onNavigateToEditNote: (noteId: String) -> Unit,
    onNavigateToPhotoPreview: (url: String) -> Unit,
    onNavigateToVideoPreview: (url: String) -> Unit,
    viewModel: FavoriteViewModel = hiltViewModel(),
    onScrollDirectionChanged: (Boolean) -> Unit = {},
    scrollToTop: Long?,
    onScrollToTopConsumed: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val pullToRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()

    val content by viewModel.content.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery = uiState.searchQuery

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

    ScrollDirectionEffect(listState, onScrollDirectionChanged)

    ScrollToTopEffect(
        scrollToTop = scrollToTop,
        listState = listState,
        hasItems = (content as? FavoriteContent.Success)?.notes?.isNotEmpty() == true,
        onConsumed = onScrollToTopConsumed
    )

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
            SearchBar(
                searchQuery = searchQuery,
                onSearchQueryChange = { text -> viewModel.updateSearchQuery(text) },
                placeholder = {
                    Text(
                        text = stringResource(R.string.search_favorites),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (val state = content) {
                    is FavoriteContent.Loading ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 1.5.dp
                            )
                        }

                    is FavoriteContent.Success ->
                        if (state.notes.isEmpty()) {
                            FullScreenMessage(stringResource(R.string.no_results_found))
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
                                            onNavigateToNotePreview = {
                                                onNavigateToNotePreview(note.noteId)
                                            },
                                            onEditNote = {
                                                onNavigateToEditNote(note.noteId)
                                            },
                                            onFavoriteNote = {
                                                viewModel.favoriteNote(note)
                                            },
                                            onDeleteNote = {
                                                viewModel.setNoteToDelete(note)
                                            },
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