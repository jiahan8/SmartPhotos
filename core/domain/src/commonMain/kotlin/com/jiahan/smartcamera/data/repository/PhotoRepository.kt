package com.jiahan.smartcamera.data.repository

import com.jiahan.smartcamera.domain.PhotoPage
import com.jiahan.smartcamera.util.AppConstants.DEFAULT_PAGE_SIZE

interface PhotoRepository {
    suspend fun listPhotos(page: Int, pageSize: Int = DEFAULT_PAGE_SIZE): Result<PhotoPage>

    suspend fun searchPhotos(
        query: String,
        page: Int,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): Result<PhotoPage>
}