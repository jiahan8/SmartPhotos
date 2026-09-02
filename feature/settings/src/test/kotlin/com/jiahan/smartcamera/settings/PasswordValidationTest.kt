package com.jiahan.smartcamera.settings

import com.jiahan.smartcamera.util.ValidationResult
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Split out of :app's ValidationUtilsTest when `validateNewPassword` moved here with the module.
 * The username/display-name cases stayed behind with their validators.
 */
class PasswordValidationTest {

    @Test
    fun `validateNewPassword empty without requireNonBlank returns Success`() {
        assertTrue(validateNewPassword("") is ValidationResult.Success)
    }

    @Test
    fun `validateNewPassword empty with requireNonBlank returns Error`() {
        assertTrue(validateNewPassword("", requireNonBlank = true) is ValidationResult.Error)
    }

    @Test
    fun `validateNewPassword blank with requireNonBlank returns Error`() {
        assertTrue(validateNewPassword("   ", requireNonBlank = true) is ValidationResult.Error)
    }

    @Test
    fun `validateNewPassword non-blank value returns Success`() {
        assertTrue(
            validateNewPassword("hunter2", requireNonBlank = true) is ValidationResult.Success
        )
    }
}