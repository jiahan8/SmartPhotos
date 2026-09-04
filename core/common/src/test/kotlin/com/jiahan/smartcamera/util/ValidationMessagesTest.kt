package com.jiahan.smartcamera.util

import com.jiahan.smartcamera.core.common.R
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins each [ValidationError] to its string, the way `ErrorMessageMappersTest` pins `AppError`'s
 * -- and for the same reason: this is the half of a validator's behaviour its own test cannot see,
 * since :core:domain has no `R`.
 *
 * A plain JVM test: a resource id is an `Int` at compile time, so nothing here needs Robolectric.
 */
class ValidationMessagesTest {

    @Test
    fun `every case maps to its own string`() {
        assertEquals(R.string.name_empty, validationErrorMessageResId(ValidationError.NAME_EMPTY))
        assertEquals(
            R.string.name_too_long,
            validationErrorMessageResId(ValidationError.NAME_TOO_LONG)
        )
        assertEquals(
            R.string.username_empty,
            validationErrorMessageResId(ValidationError.USERNAME_EMPTY)
        )
        assertEquals(
            R.string.username_too_long,
            validationErrorMessageResId(ValidationError.USERNAME_TOO_LONG)
        )
        assertEquals(
            R.string.username_invalid_characters,
            validationErrorMessageResId(ValidationError.USERNAME_INVALID_CHARACTERS)
        )
        assertEquals(
            R.string.username_reserved,
            validationErrorMessageResId(ValidationError.USERNAME_RESERVED)
        )
        assertEquals(
            R.string.password_empty,
            validationErrorMessageResId(ValidationError.PASSWORD_EMPTY)
        )
    }

    /**
     * The `when` is exhaustive, so a case with no string cannot compile -- but two cases sharing
     * one string compiles fine, and would show the user the wrong copy. This is the half the
     * compiler does not check.
     */
    @Test
    fun `no two cases share a string`() {
        val resIds = ValidationError.entries.map { validationErrorMessageResId(it) }

        assertEquals(ValidationError.entries.size, resIds.toSet().size)
    }
}