package com.jiahan.smartcamera.data.remote.dto

import com.jiahan.smartcamera.domain.Photo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PhotoDto(
    val id: String,
    @SerialName("created_at") val createdAt: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val color: String? = null,
    @SerialName("blur_hash") val blurHash: String? = null,
    val likes: Int = 0,
    val description: String? = null,
    @SerialName("alt_description") val altDescription: String? = null,
    val urls: PhotoUrlsDto,
    val user: PhotoUserDto
)

@Serializable
data class PhotoUrlsDto(
    val raw: String? = null,
    val full: String? = null,
    val regular: String? = null,
    val small: String? = null,
    val thumb: String? = null
)

@Serializable
data class PhotoUserDto(
    val id: String,
    val username: String,
    val name: String? = null,
    @SerialName("profile_image") val profileImage: PhotoUserProfileImageDto? = null
)

@Serializable
data class PhotoUserProfileImageDto(
    val small: String? = null
)

fun PhotoDto.toDomain(): Photo = Photo(
    id = id,
    description = description ?: altDescription,
    imageUrl = urls.regular ?: urls.full ?: urls.raw.orEmpty(),
    thumbUrl = urls.small ?: urls.thumb.orEmpty(),
    width = width,
    height = height,
    color = color,
    likes = likes,
    username = user.name ?: user.username,
    userProfileImageUrl = user.profileImage?.small
)