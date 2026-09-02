package com.jiahan.smartcamera.util

/**
 * The result of validating one user-entered field.
 *
 * Lives here rather than beside the validators because there is no one place beside them:
 * `validateUsername` and `validateDisplayName` are in `:core:common` (`util/ValidationUtils.kt`),
 * `validateNewPassword` is in `:feature:settings` (`settings/PasswordValidation.kt`), and every one
 * of them returns this type. `:core:common` is not the answer either, even though it holds two of
 * the three -- `:feature:settings` does not depend on it. This module is what every feature gets as
 * an `api` edge from `smartphotos.android.feature`, so it is the only one all three homes can see,
 * which is the same reason `ResourceProvider` is here.
 *
 * [Error.messageResId] is a bare `Int` for exactly the reason `ResourceProvider.getString` takes
 * one: it compiles in a pure-JVM module while being an Android concept in everything but its type.
 * That makes it a testable seam, not a KMP asset -- a `commonMain` source set would need this to
 * carry an identity the way `AppError` does, not a resource id.
 *
 * That res id is also the only thing keeping the validators themselves out of this module. All
 * three are pure Kotlin -- a blank check, a length check, a regex, a reserved-name set -- and they
 * resolve an `R` for no reason except to fill in [Error]. Give it an identity plus a mapper, the
 * split `AppError`/`appErrorMessageResId` already models one layer over, and they follow it down.
 */
sealed class ValidationResult {
    object Success : ValidationResult()
    data class Error(val messageResId: Int) : ValidationResult()
}