package com.jiahan.smartcamera.domain

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.time.Instant

@Parcelize
data class User(
    val email: String,
    val metadata: String,
    val displayName: String,
    val username: String,
    val profilePicture: String?,
    val createdDate: Instant,
    val documentPath: String
) : Parcelable