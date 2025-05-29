package com.jiahan.smartcamera.domain

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

@Parcelize
data class HomeNote(
    val text: String? = null,
    val createdDate: Date? = null,
    val documentPath: String? = null,
    val favorite: Boolean = false
) : Parcelable