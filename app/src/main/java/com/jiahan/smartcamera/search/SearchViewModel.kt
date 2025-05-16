package com.jiahan.smartcamera.search

import androidx.lifecycle.ViewModel
import com.jiahan.smartcamera.data.repository.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
) : ViewModel() {

    val photos = searchRepository.photos

    suspend fun deleteImage(imageId: Int) {
        searchRepository.deleteImage(imageId)
    }
}