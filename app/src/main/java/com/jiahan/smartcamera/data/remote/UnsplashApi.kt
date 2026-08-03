package com.jiahan.smartcamera.data.remote

import com.jiahan.smartcamera.data.remote.dto.PhotoDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import javax.inject.Inject

class UnsplashApi @Inject constructor(
    private val httpClient: HttpClient
) {

    companion object {
        private const val PHOTOS_PATH = "photos"
        private const val ORDER_BY_LATEST = "latest"
    }

    suspend fun listPhotos(
        page: Int,
        perPage: Int,
        orderBy: String = ORDER_BY_LATEST
    ): List<PhotoDto> = httpClient.get(PHOTOS_PATH) {
        parameter("page", page)
        parameter("per_page", perPage)
        parameter("order_by", orderBy)
    }.body()
}