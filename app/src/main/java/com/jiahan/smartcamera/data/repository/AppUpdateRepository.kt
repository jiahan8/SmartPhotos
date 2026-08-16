package com.jiahan.smartcamera.data.repository

import com.google.android.play.core.ktx.AppUpdateResult
import kotlinx.coroutines.flow.Flow

interface AppUpdateRepository {
    fun observeUpdateState(): Flow<AppUpdateResult>
}