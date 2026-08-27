package com.jiahan.smartcamera.domain

/**
 * Where an in-app update currently stands, expressed without any Play Core types so the
 * ViewModel and UI can branch on it (and be tested) without the Play libraries on the classpath.
 *
 * Mirrors the states `AppUpdateManager` reports; the mapping from Play Core's `AppUpdateResult`
 * lives in `DefaultAppUpdateRepository`.
 */
sealed interface AppUpdateState {

    /** No update is available, or availability hasn't been determined yet. */
    data object NotAvailable : AppUpdateState

    /** An update is available but hasn't been started. */
    data object Available : AppUpdateState

    /**
     * A flexible update is downloading. [bytesDownloaded] and [totalBytesToDownload] come
     * straight from Play and are `0` while it hasn't started reporting progress yet.
     */
    data class InProgress(
        val bytesDownloaded: Long,
        val totalBytesToDownload: Long
    ) : AppUpdateState

    /** The update finished downloading and is waiting to be installed. */
    data object Downloaded : AppUpdateState
}