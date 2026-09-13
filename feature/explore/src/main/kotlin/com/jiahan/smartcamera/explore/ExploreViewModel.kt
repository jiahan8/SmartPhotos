package com.jiahan.smartcamera.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiahan.smartcamera.data.repository.AnalyticsRepository
import com.jiahan.smartcamera.data.repository.PhotoRepository
import com.jiahan.smartcamera.domain.Photo
import com.jiahan.smartcamera.util.AppConstants.UNSPLASH_FIRST_PAGE
import com.jiahan.smartcamera.util.AppConstants.UNSPLASH_MAX_PAGE_SIZE
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.ErrorMessage
import com.jiahan.smartcamera.util.ErrorTag
import com.jiahan.smartcamera.util.toErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ExploreContent {
    data object Loading : ExploreContent
    data class Success(val photos: List<Photo>) : ExploreContent
    data class Error(val message: ErrorMessage) : ExploreContent
}

data class ExploreUiState(
    val content: ExploreContent = ExploreContent.Loading,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val searchContent: ExploreContent? = null,
    val isSearchLoadingMore: Boolean = false,
    /** Bumped on every new (non-load-more) search submission, so the UI can reset scroll position. */
    val searchResultsVersion: Int = 0
) {
    val photos: List<Photo>?
        get() = (content as? ExploreContent.Success)?.photos

    val searchPhotos: List<Photo>?
        get() = (searchContent as? ExploreContent.Success)?.photos

    /** True once the user has submitted at least one search this session. */
    val hasSubmittedSearch: Boolean
        get() = searchContent != null
}

/**
 * One paginated list's position and the two jobs that drive it.
 *
 * Explore runs two of these -- the browse feed and the search results -- and every field below used
 * to exist twice, once bare and once behind a `search` prefix. That duplication is what this type
 * removes; the invariants it encodes (a reload owns the reset, load-more stands down while one is
 * active) are unchanged and documented at the call sites.
 */
private class FeedPagination {
    var page = UNSPLASH_FIRST_PAGE
        private set
    var hasMore = true
        private set
    var reloadJob: Job? = null
    var loadMoreJob: Job? = null

    /** True while a reload is rebuilding the list, which is when load-more must no-op. */
    val isReloading: Boolean get() = reloadJob?.isActive == true

    /** Back to the first page. Only a reload path may call this. */
    fun reset() {
        page = UNSPLASH_FIRST_PAGE
        hasMore = true
    }

    /** Records what the page just fetched said about the next one, and moves onto it. */
    fun advance(hasMore: Boolean) {
        this.hasMore = hasMore
        page++
    }
}

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val photoRepository: PhotoRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val errorHandler: ErrorHandler
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState = _uiState.asStateFlow()

    private val pageSize = UNSPLASH_MAX_PAGE_SIZE

    private val browse = FeedPagination()
    private val search = FeedPagination()
    private var lastSubmittedQuery = ""

    init {
        reload(showRefreshIndicator = false)
    }

    fun logImageLoadError(throwable: Throwable) {
        errorHandler.logError(throwable, tag = ErrorTag.IMAGE_LOAD)
    }

    fun toggleSearch() {
        // Closing only hides the field — the search query/results/pagination are left in
        // place, same as the browse feed, so reopening search shows them again for free.
        _uiState.update { it.copy(isSearchActive = !it.isSearchActive) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query.isNotBlank()) {
            analyticsRepository.logExploreSearch(query)
        }
    }

    /**
     * Runs the query from page 1 — the one path that resets the search pagination, so it owns
     * cancelling whatever the previous query left in flight.
     *
     * That page belongs to the *previous* query and page counter; letting it land would splice one
     * search's results into another's list and then advance the wrong counter.
     */
    fun submitSearch() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isBlank()) return

        val previousSearchReloadJob = search.reloadJob
        search.reloadJob = viewModelScope.launch {
            previousSearchReloadJob?.cancelAndJoin()
            search.loadMoreJob?.cancelAndJoin()
            lastSubmittedQuery = query
            search.reset()
            _uiState.update {
                it.copy(
                    searchResultsVersion = it.searchResultsVersion + 1,
                    isSearchLoadingMore = false
                )
            }
            fetchSearchResults(initialLoading = true)
        }
    }

    fun loadMoreSearchResults() {
        if (search.isReloading) return
        if (_uiState.value.isSearchLoadingMore ||
            !search.hasMore ||
            !_uiState.value.hasSubmittedSearch
        ) {
            return
        }

        search.loadMoreJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearchLoadingMore = true) }
            fetchSearchResults(initialLoading = false)
            _uiState.update { it.copy(isSearchLoadingMore = false) }
        }
    }

    fun refresh() {
        reload(showRefreshIndicator = true)
    }

    /**
     * Rebuilds the browse feed from page 1 — the one path that resets [browse], so every
     * caller wanting a fresh list goes through it.
     *
     * A page load still in flight is cancelled first: it was issued against a page index this
     * reset invalidates, so letting it land would splice a stale window into the new list.
     */
    private fun reload(showRefreshIndicator: Boolean) {
        val previousReloadJob = browse.reloadJob
        browse.reloadJob = viewModelScope.launch {
            previousReloadJob?.cancelAndJoin()
            browse.loadMoreJob?.cancelAndJoin()
            _uiState.update {
                it.copy(isRefreshing = showRefreshIndicator, isLoadingMore = false)
            }
            fetchPhotos(initialLoading = true)
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun loadMorePhotos() {
        if (browse.isReloading) return
        if (_uiState.value.isLoadingMore || !browse.hasMore) return

        browse.loadMoreJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            fetchPhotos(initialLoading = false)
            _uiState.update { it.copy(isLoadingMore = false) }
        }
    }

    private suspend fun fetchPhotos(initialLoading: Boolean) {
        if (initialLoading) {
            if (!_uiState.value.isRefreshing) {
                _uiState.update { it.copy(content = ExploreContent.Loading) }
            }
            browse.reset()
        }
        if (!browse.hasMore) return

        photoRepository.listPhotos(page = browse.page, pageSize = pageSize)
            .onSuccess { photoPage ->
                val prev = if (initialLoading) emptyList()
                else _uiState.value.photos ?: emptyList()
                _uiState.update {
                    it.copy(content = ExploreContent.Success(prev + photoPage.photos))
                }
                browse.advance(photoPage.hasMore)
            }
            .onFailure { e ->
                errorHandler.logError(e)
                if (initialLoading) {
                    _uiState.update {
                        it.copy(content = ExploreContent.Error(e.toErrorMessage()))
                    }
                }
            }
    }

    private suspend fun fetchSearchResults(initialLoading: Boolean) {
        if (initialLoading) {
            _uiState.update { it.copy(searchContent = ExploreContent.Loading) }
        }
        if (!search.hasMore) return

        photoRepository.searchPhotos(
            query = lastSubmittedQuery,
            page = search.page,
            pageSize = pageSize
        )
            .onSuccess { photoPage ->
                val prev = if (initialLoading) emptyList()
                else _uiState.value.searchPhotos ?: emptyList()
                _uiState.update {
                    it.copy(searchContent = ExploreContent.Success(prev + photoPage.photos))
                }
                search.advance(photoPage.hasMore)
            }
            .onFailure { e ->
                errorHandler.logError(e)
                if (initialLoading) {
                    _uiState.update {
                        it.copy(searchContent = ExploreContent.Error(e.toErrorMessage()))
                    }
                }
            }
    }
}