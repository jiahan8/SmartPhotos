package com.jiahan.smartcamera.datastore

data class UserPreferences(
    val isDarkTheme: Boolean,
    val username: String,
    val profilePicture: String?
)