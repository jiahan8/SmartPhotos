package com.jiahan.smartcamera.domain

import android.graphics.Bitmap
import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

@Parcelize
data class HomeNote(
    val text: String? = null,
    val createdDate: Date? = null,
    val documentPath: String,
    val favorite: Boolean = false,
    val mediaList: List<MediaDetail>? = null,
    val username: String,
    val profilePictureUrl: String? = null
) : Parcelable

@Parcelize
data class MediaDetail(
    val photoUrl: String? = null,
    val videoUrl: String? = null,
    val thumbnailUrl: String? = null,
    val isVideo: Boolean = false,
    val text: String? = null,
    val generatedText: String? = null
) : Parcelable

@Parcelize
data class NoteMediaDetail(
    val photoUri: Uri? = null,
    val videoUri: Uri? = null,
    val thumbnailBitmap: Bitmap? = null,
    val isVideo: Boolean = false,
    val text: String? = null
) : Parcelable