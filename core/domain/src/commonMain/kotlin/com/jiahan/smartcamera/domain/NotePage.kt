package com.jiahan.smartcamera.domain

/**
 * One page of notes, plus the position to resume from.
 *
 * [nextCursor] is derived from the raw rows the data source returned, never from [notes].size: a
 * note whose author lookup fails is dropped from [notes], so a short list does not mean the feed
 * has ended. It is null exactly when this page was the last one.
 */
data class NotePage(
    val notes: List<HomeNote>,
    val nextCursor: NoteCursor? = null
) {
    val hasMore: Boolean get() = nextCursor != null
}