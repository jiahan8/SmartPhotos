package com.jiahan.smartcamera.domain

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Photo(
    val id: String,
    val description: String? = null,
    val imageUrl: String,
    val thumbUrl: String,
    val width: Int,
    val height: Int,
    val color: String? = null,
    val likes: Int = 0,
    val username: String,
    val userProfileImageUrl: String? = null
) : Parcelable