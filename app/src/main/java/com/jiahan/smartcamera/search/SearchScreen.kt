package com.jiahan.smartcamera.search

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.Screen
import com.jiahan.smartcamera.home.HomeItem
import com.jiahan.smartcamera.util.AppConstants.TEXT_FIELD_PLACEHOLDER_ROTATION_DELAY_MS
import com.jiahan.smartcamera.util.AppConstants.TEXT_FIELD_TRANSITION_DELAY_MS
import com.jiahan.smartcamera.util.AppConstants.TEXT_FIELD_TRANSITION_FADE_DURATION_MS
import com.jiahan.smartcamera.util.pairwise
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    navController: NavController,
    viewModel: SearchViewModel = hiltViewModel(),
    onScrollDirectionChanged: (Boolean) -> Unit = {},
    scrollToTop: Long?,
    onScrollToTopConsumed: () -> Unit
) {
    val pullToRefreshState = rememberPullToRefreshState()
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isLoadingMore.collectAsStateWithLifecycle()
    val noteToDelete by viewModel.noteToDelete.collectAsStateWithLifecycle()

    val placeholderOptions =
        listOf(
            stringResource(R.string.search_notes_photos),
            stringResource(R.string.find_photos),
            stringResource(R.string.find_notes),
            stringResource(R.string.what_you_looking_for),
            stringResource(R.string.look_up)
        )
    val placeholderList = remember { placeholderOptions }
    val currentPlaceholderIndex by viewModel.currentPlaceholderIndex.collectAsStateWithLifecycle()
    val placeholder = placeholderList[currentPlaceholderIndex]
    var isTransitioning by remember { mutableStateOf(false) }
    val placeholderAlpha by animateFloatAsState(
        targetValue = if (isTransitioning) 0f else 1f,
        animationSpec = tween(durationMillis = TEXT_FIELD_TRANSITION_FADE_DURATION_MS),
        label = "placeholderAlpha"
    )

    val onRefresh: () -> Unit = {
        coroutineScope.launch {
            viewModel.setRefreshing(true)
            viewModel.searchNotes()
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

    LaunchedEffect(Unit) {
        while (true) {
            delay(TEXT_FIELD_PLACEHOLDER_ROTATION_DELAY_MS)
            isTransitioning = true
            delay(TEXT_FIELD_TRANSITION_DELAY_MS)
            viewModel.updateCurrentPlaceholderIndex((currentPlaceholderIndex + 1) % placeholderList.size)
            isTransitioning = false
            delay(TEXT_FIELD_TRANSITION_DELAY_MS)
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
                        contentDescription = "Search",
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
                                contentDescription = "Clear field",
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
        }
    ) { padding ->
        when {
            isLoading ->
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
                    Text(stringResource(R.string.no_results_found))
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