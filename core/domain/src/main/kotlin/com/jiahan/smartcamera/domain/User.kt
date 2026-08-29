package com.jiahan.smartcamera.domain

import kotlin.time.Instant

data class User(
    val userId: String,
    val email: String,
    val metadata: String,
    val displayName: String,
    val username: String,
    val profilePicture: String?,
    val createdDate: Instant,
)