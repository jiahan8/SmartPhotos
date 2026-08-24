package com.jiahan.smartcamera.explore

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.common.ProfileAvatar
import com.jiahan.smartcamera.common.rememberShouldLoadMore
import com.jiahan.smartcamera.common.shimmer
import com.jiahan.smartcamera.domain.Photo
import com.jiahan.smartcamera.util.AppConstants.ANIMATION_DURATION_SHORT_MS

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    onBack: () -> Unit,
    onNavigateToPhotoPreview: (url: String) -> Unit,
    viewModel: ExploreViewModel = hiltViewModel(),
) {
    val pullToRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val shouldLoadMore by rememberShouldLoadMore(listState) { uiState.photos?.size ?: 0 }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !uiState.isLoadingMore) {
            viewModel.loadMorePhotos()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.explore),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
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
                    contentKey = { it::class },
                    transitionSpec = {
                        fadeIn(tween(ANIMATION_DURATION_SHORT_MS)) togetherWith
                                fadeOut(tween(ANIMATION_DURATION_SHORT_MS))
                    },
                    label = "ExploreContent"
                ) { state ->
                    when (state) {
                        is ExploreContent.Loading -> ExploreListSkeleton()

                        is ExploreContent.Error ->
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(state.message)
                            }

                        is ExploreContent.Success ->
                            if (state.photos.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(stringResource(R.string.no_photos_found))
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
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp)
                                    ) {
                                        items(
                                            count = state.photos.size,
                                            key = { index -> state.photos[index].id }
                                        ) { index ->
                                            val photo = state.photos[index]
                                            ExploreItem(
                                                photo = photo,
                                                modifier = Modifier.animateItem(),
                                                onClick = { onNavigateToPhotoPreview(photo.imageUrl) },
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

/**
 * Placeholder shown in place of an [ExploreItem] while the first page loads. Uses a 3:2 block for
 * the photo, since real heights vary per photo's aspect ratio and aren't known until they load.
 */
@Composable
private fun ExploreItemSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(bottom = 16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .shimmer(CircleShape)
            )
            Box(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .fillMaxWidth(0.4f)
                    .height(14.dp)
                    .shimmer()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 2f)
                .shimmer(RectangleShape)
        )
    }
}

/** A short run of [ExploreItemSkeleton]s filling the list area while the first page loads. */
@Composable
private fun ExploreListSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        repeat(SKELETON_ITEM_COUNT) { ExploreItemSkeleton() }
    }
}

private const val SKELETON_ITEM_COUNT = 2

@Composable
private fun ExploreItem(
    photo: Photo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onImageLoadError: (Throwable) -> Unit = {}
) {
    val aspectRatio = remember(photo.width, photo.height) {
        if (photo.width > 0 && photo.height > 0) {
            photo.width.toFloat() / photo.height.toFloat()
        } else {
            1f
        }
    }

    Column(modifier = modifier.padding(bottom = 16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            ProfileAvatar(
                profilePictureUrl = photo.userProfileImageUrl,
                onImageLoadError = onImageLoadError,
                size = 32.dp
            )

            Text(
                text = photo.username,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(start = 12.dp)
            )
        }

        AsyncImage(
            model = photo.thumbUrl,
            contentDescription = photo.description ?: stringResource(R.string.photo),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .clickable { onClick() },
            onError = { onImageLoadError(it.result.throwable) }
        )
    }
}