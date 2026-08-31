package com.jiahan.smartcamera.util

import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.core.common.R as CommonR
import com.jiahan.smartcamera.domain.AppError
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A plain JVM test now, and that is the interesting part.
 *
 * It used to need Robolectric, because `noteErrorMessageResId` took a `FirebaseFunctionsException`
 * and that type's companion eagerly builds an `android.util.SparseArray` in a static initializer,
 * which throws "not mocked" under the JVM Android stub. Both Firebase-reading mappers have since
 * been folded into [AppError] by the repositories that raise them, so what is left maps an
 * identity to a resource id and needs no Android runtime at all -- **the module-boundary argument
 * for folding them had a testing dividend nobody was looking for.**
 */
class ErrorMessageMappersTest {

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

    // The five below resolve :core:common's R rather than :app's, because each string has a second
    // reader down there -- the username validators, and note/'s own client-side length checks.

    @Test
    fun `UsernameTaken returns username_not_available`() {
        assertEquals(
            CommonR.string.username_not_available,
            appErrorMessageResId(AppError.UsernameTaken())
        )
    }

    @Test
    fun `UsernameReserved returns username_reserved`() {
        assertEquals(
            CommonR.string.username_reserved,
            appErrorMessageResId(AppError.UsernameReserved())
        )
    }

    @Test
    fun `NoteTextTooLong returns note_validation`() {
        assertEquals(
            CommonR.string.note_validation,
            appErrorMessageResId(AppError.NoteTextTooLong())
        )
    }

    @Test
    fun `NoteMediaLimitExceeded returns note_media_limit`() {
        assertEquals(
            CommonR.string.note_media_limit,
            appErrorMessageResId(AppError.NoteMediaLimitExceeded())
        )
    }

    @Test
    fun `NoteEmpty returns note_empty`() {
        assertEquals(CommonR.string.note_empty, appErrorMessageResId(AppError.NoteEmpty()))
    }
}