package com.jiahan.smartcamera.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiahan.smartcamera.data.repository.PhotoRepository
import com.jiahan.smartcamera.domain.Photo
import com.jiahan.smartcamera.util.AppConstants.DEFAULT_PAGE_SIZE
import com.jiahan.smartcamera.util.ErrorHandler
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val isLoadingMore: Boolean = false
) {
    val photos: List<Photo>?
        get() = (content as? ExploreContent.Success)?.photos
}

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val photoRepository: PhotoRepository,
    private val errorHandler: ErrorHandler
) : ViewModel() {

    companion object {
        // Unsplash pagination is 1-indexed.
        private const val FIRST_PAGE = 1
    }

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState = _uiState.asStateFlow()

    private var currentPage = FIRST_PAGE
    private val pageSize = DEFAULT_PAGE_SIZE
    private var hasMoreData = true

    init {
        viewModelScope.launch { fetchPhotos(initialLoading = true) }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            fetchPhotos(initialLoading = true)
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun loadMorePhotos() {
        if (_uiState.value.isLoadingMore || !hasMoreData) return

        viewModelScope.launch {
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
            currentPage = FIRST_PAGE
            hasMoreData = true
        }
        if (!hasMoreData) return

        photoRepository.listPhotos(page = currentPage, pageSize = pageSize)
            .onSuccess { result ->
                val prev = if (initialLoading) emptyList()
                else _uiState.value.photos ?: emptyList()
                _uiState.update { it.copy(content = ExploreContent.Success(prev + result)) }
                hasMoreData = result.size >= pageSize
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
}