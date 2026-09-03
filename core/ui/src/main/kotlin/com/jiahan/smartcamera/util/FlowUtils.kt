package com.jiahan.smartcamera.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Emits `(previous, current)` pairs for each consecutive pair of values in the upstream [Flow].
 *
 * The first element is never emitted on its own; emission starts only once a second value arrives.
 * Example: upstream `[1, 2, 3]` → emits `(1,2)` then `(2,3)`.
 *
 * [T] is bound to `Any` because the implementation uses `null` as its "nothing seen yet" sentinel:
 * on a nullable [T] a real `null` would be indistinguishable from the start of the stream, and the
 * pair following it would be dropped. Supporting one would mean tracking presence separately.
 */
fun <T : Any> Flow<T>.pairwise(): Flow<Pair<T, T>> = flow {
    var previous: T? = null
    collect { value ->
        previous?.let { emit(it to value) }
        previous = value
    }
}