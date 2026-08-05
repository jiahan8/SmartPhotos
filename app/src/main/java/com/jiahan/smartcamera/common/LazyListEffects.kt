package com.jiahan.smartcamera.common

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import com.jiahan.smartcamera.util.pairwise
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
fun ScrollDirectionEffect(
    listState: LazyListState,
    onScrollDirectionChanged: (Boolean) -> Unit
) {
    LaunchedEffect(listState) {
        onScrollDirectionChanged(true)
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .pairwise()
            .map { (prev, curr) ->
                val (prevIndex, prevOffset) = prev
                val (currIndex, currOffset) = curr
                currIndex < prevIndex || (currIndex == prevIndex && currOffset < prevOffset)
            }
            .distinctUntilChanged()
            .collect { isScrollingUp -> onScrollDirectionChanged(isScrollingUp) }
    }
}

@Composable
fun ScrollToTopEffect(
    scrollToTop: Long?,
    listState: LazyListState,
    hasItems: Boolean,
    onConsumed: () -> Unit
) {
    LaunchedEffect(scrollToTop) {
        scrollToTop?.let {
            if (hasItems) {
                listState.animateScrollToItem(0)
                onConsumed()
            }
        }
    }
}

@Composable
fun rememberShouldLoadMore(
    listState: LazyListState,
    itemCount: () -> Int
): State<Boolean> = remember(listState) {
    derivedStateOf {
        val count = itemCount()
        if (count == 0) return@derivedStateOf false
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
        lastVisible != null && lastVisible >= count - 1
    }
}