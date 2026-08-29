package com.jiahan.smartcamera.data.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.domain.MediaUri
import com.jiahan.smartcamera.util.DefaultErrorHandler
import com.jiahan.smartcamera.util.ErrorHandler
import com.jiahan.smartcamera.util.ResourceProviderImpl
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Covers the one [DefaultUserRepository] branch that raises an error of its own.
 *
 * Asserts the string a user would see, resolved through a real [DefaultErrorHandler], so the test
 * pins the user-facing contract rather than the repository's internal encoding of the failure.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class DefaultUserRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private val auth: FirebaseAuth = mockk()
    private val firestore: FirebaseFirestore = mockk(relaxed = true)
    private val functions: FirebaseFunctions = mockk(relaxed = true)
    private val messaging: FirebaseMessaging = mockk(relaxed = true)
    private val remoteConfigRepository: RemoteConfigRepository = mockk(relaxed = true)

    private val repository = DefaultUserRepository(
        auth = auth,
        firestore = firestore,
        functions = functions,
        messaging = messaging,
        remoteConfigRepository = remoteConfigRepository,
    )

    private val messageResolver: ErrorHandler =
        DefaultErrorHandler(ResourceProviderImpl(context))

    private fun userFacingMessage(result: Result<*>): String =
        messageResolver.getErrorMessage(result.exceptionOrNull()!!)

    @Test
    fun `uploadProfilePicture signed out fails with the not-signed-in message`() = runTest {
        every { auth.uid } returns null

        val result = repository.uploadProfilePicture(MediaUri("file:///tmp/avatar.jpg"))

        assertTrue(result.isFailure)
        assertEquals(
            context.getString(R.string.user_not_authenticated),
            userFacingMessage(result)
        )
    }
}