package com.jiahan.smartcamera.domain

import java.util.Date

data class User(
    val email: String,
    val password: String,
    val fullName: String,
    val username: String,
    val profilePicture: String?,
    val createdDate: Date,
    val documentPath: String
)