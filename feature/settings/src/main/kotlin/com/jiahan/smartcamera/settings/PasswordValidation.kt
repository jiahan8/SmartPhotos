package com.jiahan.smartcamera.settings

import com.jiahan.smartcamera.feature.settings.R
import com.jiahan.smartcamera.util.ValidationResult

/**
 * Came here from `:app`'s `util/ValidationUtils.kt` when this module was extracted, because
 * [SettingsViewModel] was its only caller -- the same "a resource moves to the module that owns it"
 * rule the strings follow, applied to a function. Its `password_empty` string travelled with it.
 *
 * `validateUsername` and `validateDisplayName` stayed behind: `auth/` and `profile/` share them, so
 * they move when the second of those two becomes a module. [ValidationResult] went down to
 * :core:domain instead, since it is what all three return.
 *
 * The package is `settings`, not `util`. A one-function `util` package here would collide by name
 * with three others across the build while sharing nothing with them.
 */
fun validateNewPassword(password: String, requireNonBlank: Boolean = false): ValidationResult =
    if (requireNonBlank && password.isBlank()) ValidationResult.Error(R.string.password_empty)
    else ValidationResult.Success