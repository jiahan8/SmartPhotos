package com.jiahan.smartcamera.note

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class NoteMediaDetail(
    val photoUri: Uri? = null,
    val videoUri: Uri? = null,
    val thumbnailUri: Uri? = null,
    val isVideo: Boolean = false
) : Parcelable