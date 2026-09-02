package com.jiahan.smartcamera.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiahan.smartcamera.data.repository.AnalyticsRepository
import com.jiahan.smartcamera.data.repository.PhotoRepository
import com.jiahan.smartcamera.domain.Photo
import com.jiahan.smartcamera.util.AppConstants.UNSPLASH_FIRST_PAGE
import com.jiahan.smartcamera.util.AppConstants.UNSPLASH_MAX_PAGE_SIZE
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.ErrorTag
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
    data class Error(val message: String) : ExploreContent
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

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val photoRepository: PhotoRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val errorHandler: ErrorHandler
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState = _uiState.asStateFlow()

    private var currentPage = UNSPLASH_FIRST_PAGE
    private val pageSize = UNSPLASH_MAX_PAGE_SIZE
    private var hasMoreData = true

    private var reloadJob: Job? = null
    private var loadMoreJob: Job? = null

    private var searchCurrentPage = UNSPLASH_FIRST_PAGE
    private var searchHasMoreData = true
    private var lastSubmittedQuery = ""
    private var searchReloadJob: Job? = null
    private var searchLoadMoreJob: Job? = null

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
            analyticsRepository.logExploreSearchCustomEvent(query)
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

        searchReloadJob?.cancel()
        searchReloadJob = viewModelScope.launch {
            searchLoadMoreJob?.cancelAndJoin()
            lastSubmittedQuery = query
            searchCurrentPage = UNSPLASH_FIRST_PAGE
            searchHasMoreData = true
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
        if (searchReloadJob?.isActive == true) return
        if (_uiState.value.isSearchLoadingMore ||
            !searchHasMoreData ||
            !_uiState.value.hasSubmittedSearch
        ) {
            return
        }

        searchLoadMoreJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearchLoadingMore = true) }
            fetchSearchResults(initialLoading = false)
            _uiState.update { it.copy(isSearchLoadingMore = false) }
        }
    }

    fun refresh() {
        reload(showRefreshIndicator = true)
    }

    /**
     * Rebuilds the browse feed from page 1 — the one path that resets [currentPage], so every
     * caller wanting a fresh list goes through it.
     *
     * A page load still in flight is cancelled first: it was issued against a page index this
     * reset invalidates, so letting it land would splice a stale window into the new list.
     */
    private fun reload(showRefreshIndicator: Boolean) {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            loadMoreJob?.cancelAndJoin()
            _uiState.update {
                it.copy(isRefreshing = showRefreshIndicator, isLoadingMore = false)
            }
            fetchPhotos(initialLoading = true)
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun loadMorePhotos() {
        if (reloadJob?.isActive == true) return
        if (_uiState.value.isLoadingMore || !hasMoreData) return

        loadMoreJob = viewModelScope.launch {
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
            currentPage = UNSPLASH_FIRST_PAGE
            hasMoreData = true
        }
        if (!hasMoreData) return

        photoRepository.listPhotos(page = currentPage, pageSize = pageSize)
            .onSuccess { photoPage ->
                val prev = if (initialLoading) emptyList()
                else _uiState.value.photos ?: emptyList()
                _uiState.update {
                    it.copy(content = ExploreContent.Success(prev + photoPage.photos))
                }
                hasMoreData = photoPage.hasMore
                currentPage++
            }
            .onFailure { e ->
                errorHandler.logError(e)
                if (initialLoading) {
                    _uiState.update {
                        it.copy(content = ExploreContent.Error(errorHandler.getErrorMessage(e)))
                    }
                }
            }
    }

    private suspend fun fetchSearchResults(initialLoading: Boolean) {
        if (initialLoading) {
            _uiState.update { it.copy(searchContent = ExploreContent.Loading) }
        }
        if (!searchHasMoreData) return

        photoRepository.searchPhotos(
            query = lastSubmittedQuery,
            page = searchCurrentPage,
            pageSize = pageSize
        )
            .onSuccess { photoPage ->
                val prev = if (initialLoading) emptyList()
                else _uiState.value.searchPhotos ?: emptyList()
                _uiState.update {
                    it.copy(searchContent = ExploreContent.Success(prev + photoPage.photos))
                }
                searchHasMoreData = photoPage.hasMore
                searchCurrentPage++
            }
            .onFailure { e ->
                errorHandler.logError(e)
                if (initialLoading) {
                    _uiState.update {
                        it.copy(
                            searchContent = ExploreContent.Error(errorHandler.getErrorMessage(e))
                        )
                    }
                }
            }
    }
}