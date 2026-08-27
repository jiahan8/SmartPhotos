package com.jiahan.smartcamera.data.repository

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.ktx.AppUpdateResult
import com.google.android.play.core.ktx.bytesDownloaded
import com.google.android.play.core.ktx.requestUpdateFlow
import com.google.android.play.core.ktx.totalBytesToDownload
import com.jiahan.smartcamera.domain.AppUpdateState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAppUpdateRepository @Inject constructor(
    private val appUpdateManager: AppUpdateManager
) : AppUpdateRepository {

    /**
     * The most recent Play result, kept because both [startFlexibleUpdate] and [completeUpdate]
     * need the handle it carries, and neither can take one as a parameter without putting a Play
     * Core type back into the interface. Written from the [observeUpdateState] stream and read
     * from arbitrary callers, hence `@Volatile`.
     */
    @Volatile
    private var latestResult: AppUpdateResult? = null

    override fun observeUpdateState(): Flow<AppUpdateState> =
        appUpdateManager.requestUpdateFlow()
            .onEach { latestResult = it }
            .map { it.toAppUpdateState() }

    override fun startFlexibleUpdate(
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ): Boolean {
        val available = latestResult as? AppUpdateResult.Available ?: return false
        return available.startFlexibleUpdate(launcher)
    }

    override suspend fun completeUpdate() {
        (latestResult as? AppUpdateResult.Downloaded)?.completeUpdate()
    }

    private fun AppUpdateResult.toAppUpdateState(): AppUpdateState = when (this) {
        is AppUpdateResult.NotAvailable -> AppUpdateState.NotAvailable
        is AppUpdateResult.Available -> AppUpdateState.Available
        is AppUpdateResult.InProgress -> AppUpdateState.InProgress(
            bytesDownloaded = installState.bytesDownloaded,
            totalBytesToDownload = installState.totalBytesToDownload
        )

        is AppUpdateResult.Downloaded -> AppUpdateState.Downloaded
    }
}