package com.jiahan.smartcamera.search

import androidx.lifecycle.ViewModel
import com.jiahan.smartcamera.data.repository.SearchRepository
import com.jiahan.smartcamera.repository.AnalyticsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val analyticsRepository: AnalyticsRepository
) : ViewModel() {

    val photos = searchRepository.photos

    suspend fun deleteImage(imageId: Int) {
        searchRepository.deleteImage(imageId)
    }

    fun searchPhotos(query: String) =
        if (query.isEmpty()) {
            searchRepository.photos
        } else {
            analyticsRepository.logSearchEvent(query)
            analyticsRepository.logSearchCustomEvent(query)
            searchRepository.searchPhotos(query)
        }
}