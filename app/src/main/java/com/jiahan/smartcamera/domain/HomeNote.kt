package com.jiahan.smartcamera.domain

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.time.Instant

@Parcelize
data class HomeNote(
    val text: String? = null,
    val createdDate: Instant? = null,
    val documentPath: String,
    val favorite: Boolean = false,
    val mediaList: List<MediaDetail>? = null,
    val username: String,
    val profilePictureUrl: String? = null
) : Parcelable