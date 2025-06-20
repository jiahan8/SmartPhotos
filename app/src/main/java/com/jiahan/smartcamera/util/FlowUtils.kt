package com.jiahan.smartcamera.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

fun <T> Flow<T>.pairwise(): Flow<Pair<T, T>> = flow {
    var previous: T? = null
    collect { value ->
        previous?.let { emit(it to value) }
        previous = value
    }
}