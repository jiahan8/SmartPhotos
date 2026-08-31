package com.jiahan.smartcamera.util

/**
 * The result of validating one user-entered field.
 *
 * Lives here rather than beside the validators because the validators do not: `validateUsername`
 * and `validateDisplayName` are in `:app`, `validateNewPassword` is in `:feature:settings`, and
 * every one of them returns this type. A shared return type has to sit where all of them can see
 * it, which is the same reason `ResourceProvider` is in this module.
 *
 * [Error.messageResId] is a bare `Int` for exactly the reason `ResourceProvider.getString` takes
 * one: it compiles in a pure-JVM module while being an Android concept in everything but its type.
 * That makes it a testable seam, not a KMP asset -- a `commonMain` source set would need this to
 * carry an identity the way `AppError` does, not a resource id.
 */
sealed class ValidationResult {
    object Success : ValidationResult()
    data class Error(val messageResId: Int) : ValidationResult()
}