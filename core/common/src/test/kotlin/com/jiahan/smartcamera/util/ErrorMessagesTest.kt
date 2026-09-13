package com.jiahan.smartcamera.util

import android.content.res.Resources
import com.jiahan.smartcamera.core.common.R
import com.jiahan.smartcamera.domain.AppError
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins each [AppError] to its string, and each [ErrorMessage] case to the text it resolves to.
 *
 * The first half was `ErrorMessageMappersTest` in :app and the second `DefaultErrorHandlerTest`,
 * which needed Robolectric for real strings. A mocked [Resources] is enough now: the question is
 * which id gets looked up, and whether [ErrorMessage.Unlocalized] looks one up at all -- not what
 * the English copy says, which a data-layer test has no business asserting anyway.
 */
class ErrorMessagesTest {

    private val resources: Resources = mockk {
        every { getString(any()) } answers { "string #${firstArg<Int>()}" }
    }

    @Test
    fun `a Known message resolves its AppError's string, not the developer message`() {
        assertEquals(
            "string #${R.string.note_unavailable}",
            ErrorMessage.Known(AppError.NoteUnavailable()).resolve(resources)
        )
    }

    @Test
    fun `an Unlocalized message is shown verbatim without touching resources`() {
        assertEquals("boom", ErrorMessage.Unlocalized("boom").resolve(resources))
        verify(exactly = 0) { resources.getString(any()) }
    }

    @Test
    fun `a Generic message resolves the generic string`() {
        assertEquals(
            "string #${R.string.error_occurred}",
            ErrorMessage.Generic.resolve(resources)
        )
    }

    @Test
    fun `NotAuthenticated returns user_not_authenticated`() {
        assertEquals(
            R.string.user_not_authenticated,
            appErrorMessageResId(AppError.NotAuthenticated())
        )
    }

    @Test
    fun `NoteUnavailable returns note_unavailable`() {
        assertEquals(R.string.note_unavailable, appErrorMessageResId(AppError.NoteUnavailable()))
    }

    @Test
    fun `NoMediaAvailable returns no_media_available`() {
        assertEquals(R.string.no_media_available, appErrorMessageResId(AppError.NoMediaAvailable()))
    }

    @Test
    fun `UsernameTaken returns username_not_available`() {
        assertEquals(
            R.string.username_not_available,
            appErrorMessageResId(AppError.UsernameTaken())
        )
    }

    @Test
    fun `UsernameReserved returns username_reserved`() {
        assertEquals(R.string.username_reserved, appErrorMessageResId(AppError.UsernameReserved()))
    }

    @Test
    fun `NoteTextTooLong returns note_validation`() {
        assertEquals(R.string.note_validation, appErrorMessageResId(AppError.NoteTextTooLong()))
    }

    @Test
    fun `NoteMediaLimitExceeded returns note_media_limit`() {
        assertEquals(
            R.string.note_media_limit,
            appErrorMessageResId(AppError.NoteMediaLimitExceeded())
        )
    }

    @Test
    fun `NoteEmpty returns note_empty`() {
        assertEquals(R.string.note_empty, appErrorMessageResId(AppError.NoteEmpty()))
    }
}