package com.jiahan.smartcamera.data.repository

import com.jiahan.smartcamera.domain.Photo
import com.jiahan.smartcamera.util.AppConstants.DEFAULT_PAGE_SIZE

interface PhotoRepository {
    suspend fun listPhotos(page: Int, pageSize: Int = DEFAULT_PAGE_SIZE): Result<List<Photo>>

    suspend fun searchPhotos(
        query: String,
        page: Int,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): Result<List<Photo>>
}