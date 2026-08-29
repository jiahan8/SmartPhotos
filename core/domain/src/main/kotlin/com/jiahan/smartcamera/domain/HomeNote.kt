package com.jiahan.smartcamera.domain

import kotlin.time.Instant

data class HomeNote(
    val noteId: String,
    val text: String? = null,
    val createdDate: Instant? = null,
    val favorite: Boolean = false,
    val mediaList: List<MediaDetail>? = null,
    val username: String,
    val profilePictureUrl: String? = null
)