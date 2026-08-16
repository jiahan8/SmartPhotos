package com.jiahan.smartcamera.data.repository

import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.ktx.AppUpdateResult
import com.google.android.play.core.ktx.requestUpdateFlow
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAppUpdateRepository @Inject constructor(
    private val appUpdateManager: AppUpdateManager
) : AppUpdateRepository {

    override fun observeUpdateState(): Flow<AppUpdateResult> = appUpdateManager.requestUpdateFlow()
}