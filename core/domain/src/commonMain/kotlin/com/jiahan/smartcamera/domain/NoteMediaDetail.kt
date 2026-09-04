package com.jiahan.smartcamera.domain

data class NoteMediaDetail(
    val photoUri: MediaUri? = null,
    val videoUri: MediaUri? = null,
    val thumbnailUri: MediaUri? = null,
    val isVideo: Boolean = false
)