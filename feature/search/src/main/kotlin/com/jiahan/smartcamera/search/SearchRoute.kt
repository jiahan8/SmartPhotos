package com.jiahan.smartcamera.search

import kotlinx.serialization.Serializable

/**
 * Navigation route for [SearchScreen]. Routes live in the feature package that owns them rather
 * than in one central hierarchy -- see `smartPhotosNavGraph`.
 */
@Serializable
data object SearchRoute {
    const val SEARCH_DEEP_LINK_URI_PATTERN = "live://jiahan8.github.io/search"
}