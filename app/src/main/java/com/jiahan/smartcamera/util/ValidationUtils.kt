package com.jiahan.smartcamera.util

import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.util.AppConstants.MAX_DISPLAY_NAME_LENGTH
import com.jiahan.smartcamera.util.AppConstants.MAX_USERNAME_LENGTH

sealed class ValidationResult {
    object Success : ValidationResult()
    data class Error(val messageResId: Int) : ValidationResult()
}

fun validateUsername(username: String, requireNonBlank: Boolean = false): ValidationResult = when {
    requireNonBlank && username.isBlank() -> ValidationResult.Error(R.string.username_empty)
    username.length > MAX_USERNAME_LENGTH -> ValidationResult.Error(R.string.username_too_long)
    !username.matches(Regex("^[a-zA-Z0-9._]+$")) -> ValidationResult.Error(R.string.username_invalid_characters)
    else -> ValidationResult.Success
}

fun validateDisplayName(displayName: String, requireNonBlank: Boolean = false): ValidationResult =
    when {
        requireNonBlank && displayName.isBlank() -> ValidationResult.Error(R.string.name_empty)
        displayName.length > MAX_DISPLAY_NAME_LENGTH -> ValidationResult.Error(R.string.name_too_long)
        else -> ValidationResult.Success
    }