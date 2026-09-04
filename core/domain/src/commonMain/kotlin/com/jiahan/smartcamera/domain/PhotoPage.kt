package com.jiahan.smartcamera.domain

/**
 * One page of photos, plus whether the backing query had more rows after it.
 *
 * [hasMore] is derived from the raw row count the data source returned, never from [photos].size:
 * an entry that fails to parse is dropped from [photos], so a short list does not mean the feed
 * has ended. Unlike notes, Unsplash pages by page number, so the page index stays the key and
 * there is no cursor to carry.
 */
data class PhotoPage(
    val photos: List<Photo>,
    val hasMore: Boolean
)