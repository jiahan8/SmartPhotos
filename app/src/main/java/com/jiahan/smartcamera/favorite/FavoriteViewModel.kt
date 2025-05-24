package com.jiahan.smartcamera.favorite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jiahan.smartcamera.data.repository.SearchRepository
import com.jiahan.smartcamera.data.repository.AnalyticsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavoriteViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val analyticsRepository: AnalyticsRepository
) : ViewModel() {

    val photos = searchRepository.photos

    fun deleteImage(imageId: Int) {
        viewModelScope.launch {
            searchRepository.deleteImage(imageId)
        }
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