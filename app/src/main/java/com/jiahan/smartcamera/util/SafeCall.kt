package com.jiahan.smartcamera.util

import kotlinx.coroutines.CancellationException

/**
 * Wraps a suspending [block] in a [Result], ensuring [CancellationException]
 * is **always re-thrown** so structured concurrency is never broken.
 *
 * ### Where to use
 * - **Repository implementations** — every public suspend function that can
 *   fail should return `Result<T> = safeCall { ... }`.
 * - **ViewModels** — should NOT need `safeCall` directly; they simply call
 *   `repository.method()` which already returns `Result<T>`, then chain
 *   `.onSuccess { }` / `.onFailure { errorHandler.logError(it) }`.
 *
 * ### Why not `runCatching`?
 * Kotlin's `runCatching` silently catches [CancellationException], which
 * breaks cooperative cancellation in coroutines. `safeCall` fixes this.
 */
suspend fun <T> safeCall(block: suspend () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}