package com.jiahan.smartcamera.util

import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.util.AppConstants.MAX_DISPLAY_NAME_LENGTH
import com.jiahan.smartcamera.util.AppConstants.MAX_USERNAME_LENGTH

/*
 * The two validators `auth/` and `profile/` share. They resolve :app string resources, so they stay
 * here until both of those packages become modules; `validateNewPassword` left with
 * `:feature:settings`, which was its only caller. [ValidationResult] itself went down to
 * :core:domain, where every caller can reach it.
 */

// Mirrors the RESERVED_USERNAMES set in functions/index.js so the UI can
// reject these immediately instead of waiting on a round trip to
// createUserProfile/updateUsername. Keep both lists in sync.
private val RESERVED_USERNAMES = setOf(
    "admin", "administrator", "root", "superuser", "moderator", "mod",
    "support", "help", "helpdesk", "contact", "info", "about",
    "security", "webmaster", "postmaster", "hostmaster",
    "system", "staff", "official", "team", "owner",
    "api", "www", "mail", "ftp", "firebase", "smartphotos", "smartcamera",
    "null", "undefined", "anonymous", "everyone", "here", "channel",
    "test", "sample", "guest",
    "login", "logout", "signup", "signin", "register", "password",
    "settings", "profile", "billing", "payment", "terms", "privacy",
)

fun validateUsername(username: String, requireNonBlank: Boolean = false): ValidationResult = when {
    requireNonBlank && username.isBlank() -> ValidationResult.Error(R.string.username_empty)
    username.length > MAX_USERNAME_LENGTH -> ValidationResult.Error(R.string.username_too_long)
    !username.matches(Regex("^[a-zA-Z0-9._]+$")) -> ValidationResult.Error(R.string.username_invalid_characters)
    username.lowercase() in RESERVED_USERNAMES -> ValidationResult.Error(R.string.username_reserved)
    else -> ValidationResult.Success
}

fun validateDisplayName(displayName: String, requireNonBlank: Boolean = false): ValidationResult =
    when {
        requireNonBlank && displayName.isBlank() -> ValidationResult.Error(R.string.name_empty)
        displayName.length > MAX_DISPLAY_NAME_LENGTH -> ValidationResult.Error(R.string.name_too_long)
        else -> ValidationResult.Success
    }