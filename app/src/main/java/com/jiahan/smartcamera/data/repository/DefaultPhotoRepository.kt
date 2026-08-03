package com.jiahan.smartcamera.data.repository

import com.jiahan.smartcamera.data.remote.UnsplashApi
import com.jiahan.smartcamera.data.remote.dto.toDomain
import com.jiahan.smartcamera.domain.Photo
import com.jiahan.smartcamera.util.safeCall
import javax.inject.Inject

class DefaultPhotoRepository @Inject constructor(
    private val unsplashApi: UnsplashApi
) : PhotoRepository {

    override suspend fun listPhotos(page: Int, pageSize: Int): Result<List<Photo>> = safeCall {
        unsplashApi.listPhotos(page = page, perPage = pageSize).map { it.toDomain() }
    }
}