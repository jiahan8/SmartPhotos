package com.jiahan.smartcamera.util

import com.jiahan.smartcamera.core.common.R
import com.jiahan.smartcamera.util.AppConstants.MAX_DISPLAY_NAME_LENGTH
import com.jiahan.smartcamera.util.AppConstants.MAX_USERNAME_LENGTH

/*
 * The two validators `auth/` and `profile/` share.
 *
 * They used to sit in :app, waiting for both of those packages to become modules -- the comment
 * here said so. Extracting `:feature:auth` made that wait unnecessary rather than over: a shared
 * function goes *down* to where both callers can see it, exactly as `cd_back` and `PasswordField`
 * did, and it does not have to wait for the second caller to move. `validateNewPassword` left with
 * `:feature:settings` instead, because that module was its only caller; [ValidationResult] went to
 * :core:domain, where all three can reach it.
 *
 * The `R` below is this module's own. With android.nonTransitiveRClass=true it holds only the ten
 * strings :core:common declares, and it needs importing even here, because the Kotlin package of
 * this file is com.jiahan.smartcamera.util while the namespace is com.jiahan.smartcamera.core.common.
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