package com.jiahan.smartcamera.util

/**
 * The identity of a failed field validation.
 *
 * Each case names one rule the validators in `ValidationUtils.kt` enforce, and
 * `validationErrorMessageResId` (`util/ValidationMessages.kt`, :core:common) maps it to the string
 * a ViewModel renders. That is the split `AppError`/`appErrorMessageResId` already models one layer
 * down, and adopting it here is what let the validators come to this module: [ValidationResult.Error]
 * used to carry an `R.string` id, which was the only reason three pure Kotlin functions -- a blank
 * check, a length check, a regex and a reserved-name set -- needed an Android module to live in.
 *
 * An enum rather than a sealed class, unlike [com.jiahan.smartcamera.domain.AppError]: these carry
 * no payload and cannot carry a stack trace, so there is nothing for a per-case class to hold, and
 * `entries` lets `ValidationMessagesTest` prove every case resolves instead of listing them by
 * hand. That is the reasoning `TopLevelDestination` follows.
 *
 * **Add a case here and its string in the mapper together** -- the mapper's `when` is exhaustive,
 * so the compiler asks for the second half.
 */
enum class ValidationError {
    NAME_EMPTY,
    NAME_TOO_LONG,
    USERNAME_EMPTY,
    USERNAME_TOO_LONG,
    USERNAME_INVALID_CHARACTERS,
    USERNAME_RESERVED,
    PASSWORD_EMPTY,
}

/**
 * The result of validating one user-entered field.
 *
 * It sits beside the validators now. It used to sit here without them, because there was no one
 * place beside them to sit: `validateUsername`/`validateDisplayName` were in :core:common,
 * `validateNewPassword` in :feature:settings, and this module -- the `api` edge every feature gets
 * from `smartphotos.android.feature` -- was the only one all three homes could see. Giving [Error]
 * an identity rather than a resource id removed the reason they were apart, which is the step this
 * doc used to describe as the thing keeping them out.
 *
 * A sealed type rather than a nullable [ValidationError], per the Kotlin conventions in AGENTS.md:
 * [Success] is a state, not the absence of one.
 */
sealed class ValidationResult {
    object Success : ValidationResult()
    data class Error(val error: ValidationError) : ValidationResult()
}