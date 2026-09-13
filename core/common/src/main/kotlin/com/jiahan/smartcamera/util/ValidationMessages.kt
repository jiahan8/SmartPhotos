package com.jiahan.smartcamera.util

import com.jiahan.smartcamera.core.common.R

/*
 * ValidationError -> string resource mapping.
 *
 * The presentation half of the split ValidationError describes: the validators in :core:domain
 * name the rule that failed, a ViewModel puts that identity on its UiState, and the screen renders
 * it as `stringResource(validationErrorMessageResId(reason))`.
 *
 * It sits beside `ErrorMessages.kt`, and was for a while the exception to where mappers went:
 * `appErrorMessageResId` lived in :app, applied for the features inside `getErrorMessage`, and
 * this could not follow it there -- a ValidationResult reaches a ViewModel from a function it
 * called itself, with no seam in between to apply a mapper on the feature's behalf. Moving string
 * resolution from the ViewModels to the screens ended the difference: both mappers are called from
 * feature screens only, and this module is where every one of those can see them.
 *
 * :feature:profile's ProfileScreenTest asserts `name_empty` and `username_invalid_characters`
 * through `CommonR`, so the strings stay here regardless.
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