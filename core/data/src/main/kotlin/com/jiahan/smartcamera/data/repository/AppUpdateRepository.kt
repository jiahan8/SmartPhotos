package com.jiahan.smartcamera.data.repository

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.jiahan.smartcamera.domain.AppUpdateState
import kotlinx.coroutines.flow.Flow

/**
 * Data-layer contract for Play's in-app update flow.
 *
 * Play Core types stay behind this interface: callers see [AppUpdateState] and never touch
 * `AppUpdateResult`/`AppUpdateManager`, so `MainViewModel` stays unit testable against a fake.
 *
 * The one platform handle that does cross the boundary is the [ActivityResultLauncher] in
 * [startFlexibleUpdate] — Play offers no way to start its consent flow without one, and only an
 * Activity can own it. It is an `androidx.activity` type the UI layer already holds rather than a
 * Play Core type, and it is passed per call instead of retained here.
 */
interface AppUpdateRepository {

    /**
     * Emits the current update state, then keeps emitting as a flexible update progresses
     * (`Available` -> `InProgress` -> `Downloaded`). Availability is checked once per
     * subscription.
     */
    fun observeUpdateState(): Flow<AppUpdateState>

    /**
     * Starts Play's flexible-update flow, which shows Play's own confirmation UI before
     * downloading. Returns `false` when the last observed state wasn't
     * [AppUpdateState.Available], so there is nothing to start.
     *
     * Requires an active [observeUpdateState] subscription: the update handle this needs comes
     * from that stream.
     */
    fun startFlexibleUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>): Boolean

    /**
     * Installs an update that has finished downloading and restarts the app. No-ops unless the
     * last observed state was [AppUpdateState.Downloaded].
     *
     * Play's install call can fail -- the handle is stale once the update is consumed or
     * removed -- so this carries a [Result] like every other fallible repository operation,
     * rather than throwing into the caller's scope.
     */
    suspend fun completeUpdate(): Result<Unit>
}