package com.jiahan.smartcamera.explore

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.common.FullScreenMessage
import com.jiahan.smartcamera.common.ProfileAvatar
import com.jiahan.smartcamera.common.bounceClick
import com.jiahan.smartcamera.common.rememberShouldLoadMore
import com.jiahan.smartcamera.common.shimmer
import com.jiahan.smartcamera.core.ui.R as UiR
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
    val browseListState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(uiState.isSearchActive) {
        if (uiState.isSearchActive) {
            searchFocusRequester.requestFocus()
        }
    }
    // Each new search submission (not load-more) replaces the result set entirely, so jump the
    // list back to the top rather than leaving it wherever the previous query's results scrolled to.
    LaunchedEffect(uiState.searchResultsVersion) {
        searchListState.scrollToItem(0)
    }
    BackHandler(enabled = uiState.isSearchActive) {
        viewModel.toggleSearch()
    }

    // Search results are only shown while the search bar is active -- closing it (isSearchActive
    // false) reverts to the browse list/state even if a search was submitted this session, since
    // toggleSearch() deliberately leaves searchContent/pagination in place so reopening search
    // restores it for free rather than re-fetching.
    val activeListState =
        if (uiState.isSearchActive && uiState.hasSubmittedSearch) searchListState
        else browseListState
    val displayedIsLoadingMore =
        if (uiState.isSearchActive && uiState.hasSubmittedSearch) uiState.isSearchLoadingMore
        else uiState.isLoadingMore

    // itemCount reads uiState directly (rather than through an intermediate local) since
    // rememberShouldLoadMore caches its derivedStateOf per listState identity -- a captured local
    // val would freeze at whatever it was when that listState was first seen, while a direct
    // property-delegate read stays live even from an old, cached lambda instance.
    val shouldLoadMore by rememberShouldLoadMore(activeListState) {
        if (uiState.isSearchActive && uiState.hasSubmittedSearch) {
            uiState.searchPhotos?.size ?: 0
        } else {
            uiState.photos?.size ?: 0
        }
    }
    LaunchedEffect(shouldLoadMore, uiState.isSearchActive, uiState.hasSubmittedSearch) {
        if (shouldLoadMore && !displayedIsLoadingMore) {
            if (uiState.isSearchActive && uiState.hasSubmittedSearch) {
                viewModel.loadMoreSearchResults()
            } else {
                viewModel.loadMorePhotos()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    if (uiState.isSearchActive) {
                        ExploreSearchField(
                            searchQuery = uiState.searchQuery,
                            onSearchQueryChange = viewModel::updateSearchQuery,
                            onSearch = {
                                viewModel.submitSearch()
                                keyboardController?.hide()
                            },
                            focusRequester = searchFocusRequester
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.explore),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (uiState.isSearchActive) viewModel.toggleSearch() else onBack()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                actions = {
                    // A single icon does double duty instead of showing a redundant clear icon
                    // in the field plus a close icon here: search icon to open; once open, it
                    // clears the query while there's text, then closes search once it's empty.
                    when {
                        !uiState.isSearchActive -> IconButton(onClick = viewModel::toggleSearch) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = stringResource(UiR.string.search)
                            )
                        }

                        uiState.searchQuery.isNotEmpty() -> IconButton(
                            onClick = { viewModel.updateSearchQuery("") }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Clear,
                                contentDescription = stringResource(UiR.string.cd_clear_field)
                            )
                        }

                        else -> IconButton(onClick = viewModel::toggleSearch) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.cd_close_search)
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
                val displayedContent =
                    if (uiState.isSearchActive) uiState.searchContent ?: uiState.content
                    else uiState.content

                AnimatedContent(
                    targetState = displayedContent,
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

                        is ExploreContent.Error -> FullScreenMessage(state.message)

                        is ExploreContent.Success ->
                            when {
                                state.photos.isEmpty() ->
                                    FullScreenMessage(stringResource(R.string.no_photos_found))

                                uiState.isSearchActive && uiState.hasSubmittedSearch -> Box(
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    ExplorePhotoList(
                                        listState = searchListState,
                                        photos = state.photos,
                                        isLoadingMore = uiState.isSearchLoadingMore,
                                        onNavigateToPhotoPreview = onNavigateToPhotoPreview,
                                        onImageLoadError = viewModel::logImageLoadError
                                    )
                                }

                                else -> PullToRefreshBox(
                                    modifier = Modifier.fillMaxSize(),
                                    state = pullToRefreshState,
                                    isRefreshing = uiState.isRefreshing,
                                    onRefresh = { viewModel.refresh() },
                                ) {
                                    ExplorePhotoList(
                                        listState = browseListState,
                                        photos = state.photos,
                                        isLoadingMore = uiState.isLoadingMore,
                                        onNavigateToPhotoPreview = onNavigateToPhotoPreview,
                                        onImageLoadError = viewModel::logImageLoadError
                                    )
                                }
                            }
                    }
                }
            }
        }
    }
}

/**
 * A search field sized to sit naturally inside a [TopAppBar]'s title slot. Built on
 * [BasicTextField] rather than the standalone [com.jiahan.smartcamera.common.SearchBar] — that
 * component's Material3 [androidx.compose.material3.TextField] carries a built-in min height
 * (~56.dp) meant for a full-width, top-of-screen field, which forced the app bar to grow to fit
 * it. A bare [BasicTextField] has no such minimum, so it sizes to just the text line height and
 * fits the bar's normal height.
 *
 * Has no inline clear button — the single trailing icon in the app bar's `actions` slot handles
 * both clearing the query and closing search, so as not to show two redundant "X" icons at once.
 *
 * Uses the [TextFieldValue] overload (rather than the plain-`String` one) so the cursor can be
 * placed at the end of the query on every mount/external change — e.g. reopening search shows a
 * preserved query with the cursor after it, ready to keep typing, instead of wherever a
 * String-only field would default the cursor to.
 */
@Composable
private fun ExploreSearchField(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(text = searchQuery, selection = TextRange(searchQuery.length))
        )
    }
    LaunchedEffect(searchQuery) {
        // Resync when the query changes externally without going through this field's own
        // onValueChange (e.g. the trailing clear icon), placing the cursor at the end.
        if (textFieldValue.text != searchQuery) {
            textFieldValue =
                TextFieldValue(text = searchQuery, selection = TextRange(searchQuery.length))
        }
    }

    BasicTextField(
        value = textFieldValue,
        onValueChange = {
            textFieldValue = it
            onSearchQueryChange(it.text)
        },
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        textStyle = MaterialTheme.typography.titleMedium.copy(
            color = MaterialTheme.colorScheme.onSurface
        ),
        singleLine = true,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (searchQuery.isEmpty()) {
                    Text(
                        text = stringResource(R.string.search_photos),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                innerTextField()
            }
        }
    )
}

@Composable
private fun ExplorePhotoList(
    listState: LazyListState,
    photos: List<Photo>,
    isLoadingMore: Boolean,
    onNavigateToPhotoPreview: (url: String) -> Unit,
    onImageLoadError: (Throwable) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp)
    ) {
        items(
            count = photos.size,
            key = { index -> photos[index].id }
        ) { index ->
            val photo = photos[index]
            ExploreItem(
                photo = photo,
                modifier = Modifier.animateItem(),
                onClick = { onNavigateToPhotoPreview(photo.imageUrl) },
                onImageLoadError = onImageLoadError
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
                .bounceClick(scaleDown = 0.97f, onClick = onClick),
            onError = { onImageLoadError(it.result.throwable) }
        )
    }
}