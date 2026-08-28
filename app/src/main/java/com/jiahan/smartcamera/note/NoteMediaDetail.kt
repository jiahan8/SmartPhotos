package com.jiahan.smartcamera.note

import android.net.Uri

data class NoteMediaDetail(
    val photoUri: Uri? = null,
    val videoUri: Uri? = null,
    val thumbnailUri: Uri? = null,
    val isVideo: Boolean = false
)