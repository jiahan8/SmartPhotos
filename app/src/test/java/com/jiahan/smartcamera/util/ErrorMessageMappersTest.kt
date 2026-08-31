package com.jiahan.smartcamera.util

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.functions.FirebaseFunctionsException
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.core.common.R as CommonR
import com.jiahan.smartcamera.domain.AppError
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Runs under Robolectric (via [AndroidJUnit4]) because [FirebaseFunctionsException.Code]'s
 * companion object eagerly builds a lookup table with [android.util.SparseArray] in a static
 * initializer, which throws "not mocked" under the plain JVM unit-test Android stub. Overrides
 * the manifest's [com.jiahan.smartcamera.MyApp] with a bare [Application] so startup doesn't hit
 * `MyApp.onCreate()`'s Firebase App Check install, which needs a `FirebaseApp` that Robolectric
 * never initializes here (the same pre-existing gap the other Robolectric-backed ViewModel tests
 * work around).
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class ErrorMessageMappersTest {

    private fun functionsException(
        code: FirebaseFunctionsException.Code,
        details: Any? = null,
    ): FirebaseFunctionsException {
        val exception: FirebaseFunctionsException = mockk()
        every { exception.code } returns code
        every { exception.details } returns details
        return exception
    }

    // -------------------------------------------------------------------------
    // noteErrorMessageResId
    // -------------------------------------------------------------------------

    @Test
    fun `noteErrorMessageResId TEXT_TOO_LONG returns note_validation`() {
        val exception = functionsException(
            code = FirebaseFunctionsException.Code.INVALID_ARGUMENT,
            details = mapOf("reason" to "TEXT_TOO_LONG"),
        )

        assertEquals(R.string.note_validation, noteErrorMessageResId(exception))
    }

    @Test
    fun `noteErrorMessageResId TOO_MANY_MEDIA_ITEMS returns note_media_limit`() {
        val exception = functionsException(
            code = FirebaseFunctionsException.Code.INVALID_ARGUMENT,
            details = mapOf("reason" to "TOO_MANY_MEDIA_ITEMS"),
        )

        assertEquals(R.string.note_media_limit, noteErrorMessageResId(exception))
    }

    @Test
    fun `noteErrorMessageResId EMPTY_NOTE returns note_empty`() {
        val exception = functionsException(
            code = FirebaseFunctionsException.Code.INVALID_ARGUMENT,
            details = mapOf("reason" to "EMPTY_NOTE"),
        )

        assertEquals(R.string.note_empty, noteErrorMessageResId(exception))
    }

    @Test
    fun `noteErrorMessageResId unmapped reason returns null`() {
        val exception = functionsException(
            code = FirebaseFunctionsException.Code.INVALID_ARGUMENT,
            details = mapOf("reason" to "SOME_NEW_REASON"),
        )

        assertNull(noteErrorMessageResId(exception))
    }

    @Test
    fun `noteErrorMessageResId null details returns null`() {
        val exception = functionsException(
            code = FirebaseFunctionsException.Code.INVALID_ARGUMENT,
            details = null,
        )

        assertNull(noteErrorMessageResId(exception))
    }

    @Test
    fun `noteErrorMessageResId non-Map details returns null`() {
        val exception = functionsException(
            code = FirebaseFunctionsException.Code.INVALID_ARGUMENT,
            details = "not a map",
        )

        assertNull(noteErrorMessageResId(exception))
    }

    @Test
    fun `noteErrorMessageResId non-FirebaseFunctionsException returns null`() {
        assertNull(noteErrorMessageResId(RuntimeException("boom")))
    }

    // -------------------------------------------------------------------------
    // appErrorMessageResId
    // -------------------------------------------------------------------------

    @Test
    fun `appErrorMessageResId NotAuthenticated returns user_not_authenticated`() {
        assertEquals(
            R.string.user_not_authenticated,
            appErrorMessageResId(AppError.NotAuthenticated())
        )
    }

    @Test
    fun `appErrorMessageResId NoteUnavailable returns note_unavailable`() {
        assertEquals(R.string.note_unavailable, appErrorMessageResId(AppError.NoteUnavailable()))
    }

    @Test
    fun `appErrorMessageResId NoMediaAvailable returns no_media_available`() {
        assertEquals(
            R.string.no_media_available,
            appErrorMessageResId(AppError.NoMediaAvailable())
        )
    }

    /*
     * These two replace the four usernameErrorMessageResId cases this file used to carry.
     * DefaultUserRepository now folds the ALREADY_EXISTS/INVALID_ARGUMENT codes into these
     * identities, so what used to be a Firebase-shape assertion up here became a data-layer one --
     * see DefaultUserRepositoryTest. The strings resolve against :core:common's R, since
     * validateUsername and AuthScreen read them too.
     */

    @Test
    fun `appErrorMessageResId UsernameTaken returns username_not_available`() {
        assertEquals(
            CommonR.string.username_not_available,
            appErrorMessageResId(AppError.UsernameTaken())
        )
    }

    @Test
    fun `appErrorMessageResId UsernameReserved returns username_reserved`() {
        assertEquals(
            CommonR.string.username_reserved,
            appErrorMessageResId(AppError.UsernameReserved())
        )
    }
}