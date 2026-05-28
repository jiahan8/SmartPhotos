package com.jiahan.smartcamera.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Emits `(previous, current)` pairs for each consecutive pair of values in the upstream [Flow].
 *
 * The first element is never emitted on its own; emission starts only once a second value arrives.
 * Example: upstream `[1, 2, 3]` → emits `(1,2)` then `(2,3)`.
 */
fun <T> Flow<T>.pairwise(): Flow<Pair<T, T>> = flow {
    var previous: T? = null
    collect { value ->
        previous?.let { emit(it to value) }
        previous = value
    }
}