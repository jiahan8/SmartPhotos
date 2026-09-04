package com.jiahan.smartcamera.util

import com.jiahan.smartcamera.util.AppConstants.MAX_DISPLAY_NAME_LENGTH
import com.jiahan.smartcamera.util.AppConstants.MAX_USERNAME_LENGTH

/*
 * The three field validators `auth/`, `profile/` and `settings/` call.
 *
 * They arrived from two directions: `validateUsername`/`validateDisplayName` from :core:common,
 * `validateNewPassword` from :feature:settings, which was its only caller and the right home for
 * it while the function resolved that module's `password_empty`. What moved both is that
 * [ValidationResult.Error] now carries a [ValidationError] instead of a string resource id -- with
 * no `R` to resolve, all three are plain Kotlin over a String and belong in the module every
 * caller already sees.
 *
 * One file rather than three, because the only thing that had kept them apart was which module's
 * resources each resolved.
 *
 * Nothing here touches Android, which is the point beyond tidiness: this is the first business
 * logic in the build a `commonMain` source set could take unchanged. See the Kotlin Multiplatform
 * readiness section of AGENTS.md.
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

// Mirrors USERNAME_PATTERN in functions/index.js. A property rather than a literal inside the
// `when` so it is compiled once: ProfileViewModel validates on every keystroke.
private val USERNAME_PATTERN = Regex("^[a-zA-Z0-9._]+$")

fun validateUsername(username: String, requireNonBlank: Boolean = false): ValidationResult = when {
    requireNonBlank && username.isBlank() -> ValidationResult.Error(ValidationError.USERNAME_EMPTY)
    username.length > MAX_USERNAME_LENGTH ->
        ValidationResult.Error(ValidationError.USERNAME_TOO_LONG)

    !username.matches(USERNAME_PATTERN) ->
        ValidationResult.Error(ValidationError.USERNAME_INVALID_CHARACTERS)

    username.lowercase() in RESERVED_USERNAMES ->
        ValidationResult.Error(ValidationError.USERNAME_RESERVED)

    else -> ValidationResult.Success
}

fun validateDisplayName(displayName: String, requireNonBlank: Boolean = false): ValidationResult =
    when {
        requireNonBlank && displayName.isBlank() ->
            ValidationResult.Error(ValidationError.NAME_EMPTY)

        displayName.length > MAX_DISPLAY_NAME_LENGTH ->
            ValidationResult.Error(ValidationError.NAME_TOO_LONG)

        else -> ValidationResult.Success
    }

fun validateNewPassword(password: String, requireNonBlank: Boolean = false): ValidationResult =
    if (requireNonBlank && password.isBlank()) {
        ValidationResult.Error(ValidationError.PASSWORD_EMPTY)
    } else {
        ValidationResult.Success
    }