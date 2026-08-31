package com.jiahan.smartcamera.data.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import com.google.firebase.messaging.FirebaseMessaging
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.core.common.R as CommonR
import com.jiahan.smartcamera.domain.AppError
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
 * Covers the [DefaultUserRepository] branches that raise an error of their own.
 *
 * Asserts the string a user would see, resolved through a real [DefaultErrorHandler], so the test
 * pins the user-facing contract rather than the repository's internal encoding of the failure.
 * The two username cases assert the [AppError] as well, because that identity is the part
 * ProfileViewModel branches on to decide whether the message belongs under the username field.
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

    private fun functionsFailWith(code: FirebaseFunctionsException.Code) {
        val exception: FirebaseFunctionsException = mockk(relaxed = true)
        every { exception.code } returns code
        val callable: HttpsCallableReference = mockk()
        every { callable.call(any()) } returns Tasks.forException<HttpsCallableResult>(exception)
        every { functions.getHttpsCallable(any()) } returns callable
    }

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

    /*
     * The createUserProfile/updateUsername Cloud Functions report a conflict as an HttpsError whose
     * text is hardcoded English on the server. These two pin the fold to the app's own vocabulary,
     * which is what keeps that text off the screen -- and what let :feature:auth be extracted
     * without firebase-functions on its classpath, since AuthViewModel no longer inspects the code
     * itself.
     */

    @Test
    fun `createUserProfile ALREADY_EXISTS fails as UsernameTaken`() = runTest {
        functionsFailWith(FirebaseFunctionsException.Code.ALREADY_EXISTS)

        val result = repository.createUserProfile(metadata = "secret", username = "taken")

        assertTrue(result.exceptionOrNull() is AppError.UsernameTaken)
        assertEquals(
            context.getString(CommonR.string.username_not_available),
            userFacingMessage(result)
        )
    }

    @Test
    fun `createUserProfile INVALID_ARGUMENT fails as UsernameReserved`() = runTest {
        functionsFailWith(FirebaseFunctionsException.Code.INVALID_ARGUMENT)

        val result = repository.createUserProfile(metadata = "secret", username = "admin")

        assertTrue(result.exceptionOrNull() is AppError.UsernameReserved)
        assertEquals(
            context.getString(CommonR.string.username_reserved),
            userFacingMessage(result)
        )
    }

    @Test
    fun `createUserProfile other codes surface unchanged`() = runTest {
        functionsFailWith(FirebaseFunctionsException.Code.UNAVAILABLE)

        val result = repository.createUserProfile(metadata = "secret", username = "someone")

        assertTrue(result.exceptionOrNull() is FirebaseFunctionsException)
    }
}