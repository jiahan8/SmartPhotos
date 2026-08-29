package com.jiahan.smartcamera.note

import com.jiahan.smartcamera.domain.MediaUri

data class NoteMediaDetail(
    val photoUri: MediaUri? = null,
    val videoUri: MediaUri? = null,
    val thumbnailUri: MediaUri? = null,
    val isVideo: Boolean = false
)