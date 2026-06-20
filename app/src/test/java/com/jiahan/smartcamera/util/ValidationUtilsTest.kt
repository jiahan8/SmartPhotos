package com.jiahan.smartcamera.util

import org.junit.Assert.assertTrue
import org.junit.Test

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
        assertTrue(validateUsername("") is ValidationResult.Error)
    }

    @Test
    fun `validateUsername empty with requireNonBlank returns Error`() {
        assertTrue(validateUsername("", requireNonBlank = true) is ValidationResult.Error)
    }

    @Test
    fun `validateUsername blank with requireNonBlank returns Error`() {
        assertTrue(validateUsername("   ", requireNonBlank = true) is ValidationResult.Error)
    }

    @Test
    fun `validateUsername exactly at max length returns Success`() {
        val name = "a".repeat(AppConstants.MAX_USERNAME_LENGTH)
        assertTrue(validateUsername(name) is ValidationResult.Success)
    }

    @Test
    fun `validateUsername one over max length returns Error`() {
        val name = "a".repeat(AppConstants.MAX_USERNAME_LENGTH + 1)
        assertTrue(validateUsername(name) is ValidationResult.Error)
    }

    @Test
    fun `validateUsername with space returns Error`() {
        assertTrue(validateUsername("invalid user") is ValidationResult.Error)
    }

    @Test
    fun `validateUsername with at-sign returns Error`() {
        assertTrue(validateUsername("user@name") is ValidationResult.Error)
    }

    @Test
    fun `validateUsername with hash returns Error`() {
        assertTrue(validateUsername("user#1") is ValidationResult.Error)
    }

    @Test
    fun `validateUsername single valid character returns Success`() {
        assertTrue(validateUsername("a") is ValidationResult.Success)
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
    fun `validateDisplayName blank with requireNonBlank returns Error`() {
        assertTrue(validateDisplayName("   ", requireNonBlank = true) is ValidationResult.Error)
    }

    @Test
    fun `validateDisplayName empty with requireNonBlank returns Error`() {
        assertTrue(validateDisplayName("", requireNonBlank = true) is ValidationResult.Error)
    }

    @Test
    fun `validateDisplayName exactly at max length returns Success`() {
        val name = "a".repeat(AppConstants.MAX_DISPLAY_NAME_LENGTH)
        assertTrue(validateDisplayName(name) is ValidationResult.Success)
    }

    @Test
    fun `validateDisplayName one over max length returns Error`() {
        val name = "a".repeat(AppConstants.MAX_DISPLAY_NAME_LENGTH + 1)
        assertTrue(validateDisplayName(name) is ValidationResult.Error)
    }

    @Test
    fun `validateDisplayName with special characters returns Success`() {
        // Display names have no character-set restriction, only length
        assertTrue(validateDisplayName("Ñoño 日本語") is ValidationResult.Success)
    }
}