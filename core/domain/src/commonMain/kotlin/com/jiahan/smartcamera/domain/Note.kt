package com.jiahan.smartcamera.domain

import kotlin.time.Instant

data class Note(
    val noteId: String,
    val text: String? = null,
    val createdDate: Instant? = null,
    val isFavorite: Boolean = false,
    val mediaList: List<MediaDetail>? = null,
    val username: String,
    val profilePictureUrl: String? = null
)