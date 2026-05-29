package com.jiahan.smartcamera.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.common.CustomSnackbarHost
import com.jiahan.smartcamera.common.rememberCyclingPlaceholder
import com.jiahan.smartcamera.home.HomeItem
import com.jiahan.smartcamera.util.pairwise
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateToNotePreview: (documentPath: String) -> Unit,
    onNavigateToPhotoPreview: (url: String) -> Unit,
    onNavigateToVideoPreview: (url: String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
    onScrollDirectionChanged: (Boolean) -> Unit = {},
    scrollToTop: Long?,
    onScrollToTopConsumed: () -> Unit
) {
    val pullToRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val noteToDelete by viewModel.noteToDelete.collectAsStateWithLifecycle()

    val (placeholder, placeholderAlpha) = rememberCyclingPlaceholder(
        options = listOf(
            stringResource(R.string.search_notes_photos),
            stringResource(R.string.find_photos),
            stringResource(R.string.find_notes),
            stringResource(R.string.what_you_looking_for),
            stringResource(R.string.look_up)
        )
    )

    val onRefresh: () -> Unit = { viewModel.refresh() }

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

    LaunchedEffect(Unit) {
        viewModel.actionError.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    LaunchedEffect(scrollToTop) {
        scrollToTop?.let {
            val notes = (uiState as? SearchUiState.Success)?.notes
            if (notes?.isNotEmpty() == true) {
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
            TextField(
                value = searchQuery,
                onValueChange = { text -> viewModel.updateSearchQuery(text) },
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                shape = CircleShape,
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = stringResource(R.string.search),
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(
                                imageVector = Icons.Rounded.Clear,
                                contentDescription = stringResource(R.string.cd_clear_field),
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(20.dp)
                            )
                        }
                    }
                },
                placeholder = {
                    Text(
                        text = placeholder,
                        modifier = Modifier
                            .graphicsLayer(alpha = placeholderAlpha)
                    )
                },
                colors = TextFieldDefaults.colors(
                    cursorColor = Color.Gray,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                )
            )
        },
        snackbarHost = { CustomSnackbarHost(snackbarHostState, isError = true) }
    ) { padding ->
        when (val state = uiState) {
            is SearchUiState.Idle ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.no_results_found))
                }

            is SearchUiState.Loading ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(strokeWidth = 1.5.dp)
                }

            is SearchUiState.Error ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(state.message)
                }

            is SearchUiState.Success ->
                if (state.notes.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.no_results_found))
                    }
                } else {
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
                                count = state.notes.size,
                                key = { index -> state.notes[index].documentPath }
                            ) { index ->
                                val note = state.notes[index]
                                HomeItem(
                                    note = note,
                                    onTap = {
                                        onNavigateToNotePreview(note.documentPath)
                                    },
                                    onDoubleTap = { viewModel.favoriteNote(note) },
                                    onLongPress = { viewModel.setNoteToDelete(note) },
                                    onPhotoClick = { url ->
                                        onNavigateToPhotoPreview(url)
                                    },
                                    onVideoClick = { url ->
                                        onNavigateToVideoPreview(url)
                                    },
                                    onProfilePictureClick = { url ->
                                        onNavigateToPhotoPreview(url)
                                    }
                                )
                            }
                        }
                    }
                }
        }
    }
}