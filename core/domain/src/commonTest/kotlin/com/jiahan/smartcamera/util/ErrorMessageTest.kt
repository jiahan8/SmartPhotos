package com.jiahan.smartcamera.util

import com.jiahan.smartcamera.domain.AppError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

/**
 * [toErrorMessage]'s resolution order, which used to be `DefaultErrorHandlerTest`'s in :app and
 * needed Robolectric there for the strings. Naming the message needs no strings at all, so it runs
 * on every target; the text each case resolves to is `ErrorMessagesTest`'s, in :core:common.
 */
class ErrorMessageTest {

    @Test
    fun `an AppError is named by its identity, not by its developer message`() {
        val error = AppError.NoteUnavailable()

        val message = assertIs<ErrorMessage.Known>(error.toErrorMessage())

        assertSame(error, message.error)
    }

    @Test
    fun `any other failure carries its own message`() {
        assertEquals(ErrorMessage.Unlocalized("boom"), RuntimeException("boom").toErrorMessage())
    }

    @Test
    fun `a failure with no message is generic`() {
        assertEquals(ErrorMessage.Generic, RuntimeException().toErrorMessage())
    }

    @Test
    fun `a blank message is generic rather than an empty line on screen`() {
        assertEquals(ErrorMessage.Generic, RuntimeException("  ").toErrorMessage())
    }
}