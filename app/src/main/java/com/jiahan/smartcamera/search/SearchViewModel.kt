package com.jiahan.smartcamera.search

import android.os.Bundle
import androidx.lifecycle.ViewModel
import com.google.firebase.analytics.FirebaseAnalytics
import com.jiahan.smartcamera.data.repository.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val firebaseAnalytics: FirebaseAnalytics
) : ViewModel() {

    val photos = searchRepository.photos

    suspend fun deleteImage(imageId: Int) {
        searchRepository.deleteImage(imageId)
    }

    fun searchPhotos(query: String) =
        if (query.isEmpty()) {
            searchRepository.photos
        } else {
            logSearchEvent(query)
            searchRepository.searchPhotos(query)
        }

    private fun logSearchEvent(searchQuery: String) {
        val params = Bundle().apply {
            putString(FirebaseAnalytics.Param.SEARCH_TERM, searchQuery)
        }
        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SEARCH, params)
    }
}