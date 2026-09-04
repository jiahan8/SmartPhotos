package com.jiahan.smartcamera.util

import com.jiahan.smartcamera.core.common.R

/*
 * ValidationError -> string resource mapping.
 *
 * The presentation half of the split ValidationError describes: the validators in :core:domain
 * name the rule that failed, this resolves the copy, and a ViewModel renders it as
 * `resourceProvider.getString(validationErrorMessageResId(result.error))`.
 *
 * **It lives here rather than in :app's ErrorMessageMappers.kt, which is where AGENTS.md sends a
 * new mapper, and the exception is worth understanding.** An AppError reaches a feature as a
 * Throwable it already routes through the ErrorHandler interface, so `appErrorMessageResId` can
 * sit in :app and be applied for the feature inside `getErrorMessage`. A ValidationResult has no
 * such seam: the ViewModel called the validator itself and holds the result. Mapping it from :app
 * would mean inventing a seam -- another :core:domain interface, an implementation, a Hilt binding
 * and a test double -- to reach a `when` over an enum.
 *
 * The strings settle it independently. :feature:profile's ProfileScreenTest asserts `name_empty`
 * and `username_invalid_characters` through `CommonR`, so they cannot move up to :app, and a
 * mapper cannot resolve an `R` it cannot see.
 */
fun validationErrorMessageResId(error: ValidationError): Int = when (error) {
    ValidationError.NAME_EMPTY -> R.string.name_empty
    ValidationError.NAME_TOO_LONG -> R.string.name_too_long
    ValidationError.USERNAME_EMPTY -> R.string.username_empty
    ValidationError.USERNAME_TOO_LONG -> R.string.username_too_long
    ValidationError.USERNAME_INVALID_CHARACTERS -> R.string.username_invalid_characters
    ValidationError.USERNAME_RESERVED -> R.string.username_reserved
    // Came up from :feature:settings with this mapper's arrival: SettingsViewModel still resolves
    // it directly for the confirm-password field, so the string has two readers and lands where
    // both can see it.
    ValidationError.PASSWORD_EMPTY -> R.string.password_empty
}