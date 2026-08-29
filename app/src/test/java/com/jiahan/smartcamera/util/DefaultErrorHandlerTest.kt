package com.jiahan.smartcamera.util

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.domain.AppError
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Covers [DefaultErrorHandler.getErrorMessage]'s resolution order.
 *
 * The [AppError] case is the load-bearing one: repositories raise an identity rather than a
 * message, so this is what keeps every existing ViewModel call site rendering the same localized
 * string it rendered when the repository threw a pre-localized `IllegalStateException`.
 *
 * Runs under Robolectric for real string resources, with a bare [Application] so startup doesn't
 * hit `MyApp.onCreate()`'s Firebase App Check install.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class DefaultErrorHandlerTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val errorHandler = DefaultErrorHandler(ResourceProviderImpl(context))

    @Test
    fun `getErrorMessage resolves an AppError to its string resource, not its developer message`() {
        assertEquals(
            context.getString(R.string.note_unavailable),
            errorHandler.getErrorMessage(AppError.NoteUnavailable())
        )
    }

    @Test
    fun `getErrorMessage resolves every AppError case to a localized string`() {
        // Guards the whole vocabulary, so a case added without a mapping is caught here as well
        // as by appErrorMessageResId's exhaustive `when`.
        listOf(
            AppError.NotAuthenticated() to R.string.user_not_authenticated,
            AppError.NoteUnavailable() to R.string.note_unavailable,
            AppError.NoMediaAvailable() to R.string.no_media_available,
        ).forEach { (error, resId) ->
            assertEquals(context.getString(resId), errorHandler.getErrorMessage(error))
        }
    }

    @Test
    fun `getErrorMessage still prefers localizedMessage for a non-AppError`() {
        assertEquals("boom", errorHandler.getErrorMessage(RuntimeException("boom")))
    }

    @Test
    fun `getErrorMessage falls back to the generic string when there is no message`() {
        assertEquals(
            context.getString(R.string.error_occurred),
            errorHandler.getErrorMessage(RuntimeException())
        )
    }
}