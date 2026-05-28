package com.jiahan.smartcamera.data.datastore

data class UserPreferences(
    val isDarkTheme: Boolean,
    val username: String,
    val profilePicture: String?
)