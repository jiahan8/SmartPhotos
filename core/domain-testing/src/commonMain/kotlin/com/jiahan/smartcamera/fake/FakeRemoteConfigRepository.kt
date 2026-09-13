package com.jiahan.smartcamera.fake

import com.jiahan.smartcamera.data.repository.RemoteConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * [RemoteConfigRepository] test double returning fixed, deterministic values instead of hitting
 * Firebase. [setExploreIconVisible] lets a test simulate a live Remote Config update pushed while
 * the app is running.
 */
class FakeRemoteConfigRepository(
    exploreIconVisible: Boolean = true
) : RemoteConfigRepository {

    private val exploreIconVisibleFlow = MutableStateFlow(exploreIconVisible)

    override suspend fun fetchAndActivateConfig(): Result<Unit> = Result.success(Unit)

    override fun getStorageUrl(): String = ""

    override fun getStorageFolderName(): String = ""

    override fun getStorageCacheFolderName(): String = ""

    override fun observeExploreIconVisible(): Flow<Boolean> = exploreIconVisibleFlow

    fun setExploreIconVisible(visible: Boolean) {
        exploreIconVisibleFlow.value = visible
    }
}