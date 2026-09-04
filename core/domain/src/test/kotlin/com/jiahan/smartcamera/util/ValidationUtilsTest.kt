package com.jiahan.smartcamera.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The username and display-name cases came from :core:common's ValidationUtilsTest, the password
 * ones from :feature:settings' PasswordValidationTest, each following its subject here.
 *
 * They assert the [ValidationError] a rule raises, not just that *some* error came back. That is
 * new, and it is the test-side dividend of [ValidationResult.Error] carrying an identity: while it
 * carried an `R.string` id, naming the expected failure meant resolving a resource this module
 * cannot see, so both files could only assert `is ValidationResult.Error` -- which passes just as
 * happily when a reserved name is rejected for its character set. The string each identity maps to
 * is `ValidationMessagesTest`'s to pin, one layer up.
 */
class ValidationUtilsTest {

    // -------------------------------------------------------------------------
    // validateUsername
    // -------------------------------------------------------------------------

    @Test
    fun `validateUsername valid alphanumeric name returns Success`() {
        assertTrue(validateUsername("john123") is ValidationResult.Success)
    }

    @Test
    fun `validateUsername with dot and underscore returns Success`() {
        assertTrue(validateUsername("user.name_here") is ValidationResult.Success)
    }

    @Test
    fun `validateUsername empty without requireNonBlank returns Error due to regex`() {
        // Regex uses + (one-or-more), so empty string fails character validation
        assertEquals(
            ValidationResult.Error(ValidationError.USERNAME_INVALID_CHARACTERS),
            validateUsername("")
        )
    }

    @Test
    fun `validateUsername empty with requireNonBlank returns USERNAME_EMPTY`() {
        assertEquals(
            ValidationResult.Error(ValidationError.USERNAME_EMPTY),
            validateUsername("", requireNonBlank = true)
        )
    }

    @Test
    fun `validateUsername blank with requireNonBlank returns USERNAME_EMPTY`() {
        assertEquals(
            ValidationResult.Error(ValidationError.USERNAME_EMPTY),
            validateUsername("   ", requireNonBlank = true)
        )
    }

    @Test
    fun `validateUsername exactly at max length returns Success`() {
        val name = "a".repeat(AppConstants.MAX_USERNAME_LENGTH)
        assertTrue(validateUsername(name) is ValidationResult.Success)
    }

    @Test
    fun `validateUsername one over max length returns USERNAME_TOO_LONG`() {
        val name = "a".repeat(AppConstants.MAX_USERNAME_LENGTH + 1)
        assertEquals(ValidationResult.Error(ValidationError.USERNAME_TOO_LONG), validateUsername(name))
    }

    @Test
    fun `validateUsername with space returns USERNAME_INVALID_CHARACTERS`() {
        assertEquals(
            ValidationResult.Error(ValidationError.USERNAME_INVALID_CHARACTERS),
            validateUsername("invalid user")
        )
    }

    @Test
    fun `validateUsername with at-sign returns USERNAME_INVALID_CHARACTERS`() {
        assertEquals(
            ValidationResult.Error(ValidationError.USERNAME_INVALID_CHARACTERS),
            validateUsername("user@name")
        )
    }

    @Test
    fun `validateUsername with hash returns USERNAME_INVALID_CHARACTERS`() {
        assertEquals(
            ValidationResult.Error(ValidationError.USERNAME_INVALID_CHARACTERS),
            validateUsername("user#1")
        )
    }

    @Test
    fun `validateUsername single valid character returns Success`() {
        assertTrue(validateUsername("a") is ValidationResult.Success)
    }

    @Test
    fun `validateUsername reserved name returns USERNAME_RESERVED`() {
        // Mirrors functions/index.js RESERVED_USERNAMES — keep both lists in sync.
        assertEquals(ValidationResult.Error(ValidationError.USERNAME_RESERVED), validateUsername("admin"))
    }

    @Test
    fun `validateUsername reserved name check is case-insensitive`() {
        assertEquals(ValidationResult.Error(ValidationError.USERNAME_RESERVED), validateUsername("Admin"))
    }

    @Test
    fun `validateUsername non-reserved name returns Success`() {
        assertTrue(validateUsername("legituser") is ValidationResult.Success)
    }

    // -------------------------------------------------------------------------
    // validateDisplayName
    // -------------------------------------------------------------------------

    @Test
    fun `validateDisplayName simple name returns Success`() {
        assertTrue(validateDisplayName("John Doe") is ValidationResult.Success)
    }

    @Test
    fun `validateDisplayName empty without requireNonBlank returns Success`() {
        assertTrue(validateDisplayName("") is ValidationResult.Success)
    }

    @Test
    fun `validateDisplayName blank with requireNonBlank returns NAME_EMPTY`() {
        assertEquals(
            ValidationResult.Error(ValidationError.NAME_EMPTY),
            validateDisplayName("   ", requireNonBlank = true)
        )
    }

    @Test
    fun `validateDisplayName empty with requireNonBlank returns NAME_EMPTY`() {
        assertEquals(
            ValidationResult.Error(ValidationError.NAME_EMPTY),
            validateDisplayName("", requireNonBlank = true)
        )
    }

    @Test
    fun `validateDisplayName exactly at max length returns Success`() {
        val name = "a".repeat(AppConstants.MAX_DISPLAY_NAME_LENGTH)
        assertTrue(validateDisplayName(name) is ValidationResult.Success)
    }

    @Test
    fun `validateDisplayName one over max length returns NAME_TOO_LONG`() {
        val name = "a".repeat(AppConstants.MAX_DISPLAY_NAME_LENGTH + 1)
        assertEquals(ValidationResult.Error(ValidationError.NAME_TOO_LONG), validateDisplayName(name))
    }

    @Test
    fun `validateDisplayName with special characters returns Success`() {
        // Display names have no character-set restriction, only length
        assertTrue(validateDisplayName("Ñoño 日本語") is ValidationResult.Success)
    }

    // -------------------------------------------------------------------------
    // validateNewPassword
    // -------------------------------------------------------------------------

    @Test
    fun `validateNewPassword empty without requireNonBlank returns Success`() {
        assertTrue(validateNewPassword("") is ValidationResult.Success)
    }

    @Test
    fun `validateNewPassword empty with requireNonBlank returns PASSWORD_EMPTY`() {
        assertEquals(
            ValidationResult.Error(ValidationError.PASSWORD_EMPTY),
            validateNewPassword("", requireNonBlank = true)
        )
    }

    @Test
    fun `validateNewPassword blank with requireNonBlank returns PASSWORD_EMPTY`() {
        assertEquals(
            ValidationResult.Error(ValidationError.PASSWORD_EMPTY),
            validateNewPassword("   ", requireNonBlank = true)
        )
    }

    @Test
    fun `validateNewPassword non-blank value returns Success`() {
        assertTrue(
            validateNewPassword("hunter2", requireNonBlank = true) is ValidationResult.Success
        )
    }
}