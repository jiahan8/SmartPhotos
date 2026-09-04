package com.jiahan.smartcamera.data.datastore

data class UserPreferences(
    val isDarkTheme: Boolean,
    val username: String,
    val profilePictureUrl: String?
)