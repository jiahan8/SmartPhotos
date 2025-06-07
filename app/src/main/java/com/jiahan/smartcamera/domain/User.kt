package com.jiahan.smartcamera.domain

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

@Parcelize
data class User(
    val email: String,
    val password: String,
    val fullName: String,
    val username: String,
    val profilePicture: String?,
    val createdDate: Date,
    val documentPath: String
) : Parcelable