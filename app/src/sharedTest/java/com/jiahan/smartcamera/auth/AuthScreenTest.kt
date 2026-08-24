package com.jiahan.smartcamera.auth

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.fake.FakeAnalyticsRepository
import com.jiahan.smartcamera.fake.FakeAuthRepository
import com.jiahan.smartcamera.fake.FakeErrorHandler
import com.jiahan.smartcamera.fake.FakeResourceProvider
import com.jiahan.smartcamera.fake.FakeUserPreferencesRepository
import com.jiahan.smartcamera.fake.FakeUserRepository
import com.jiahan.smartcamera.ui.theme.SmartCameraTheme
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for [AuthScreen].
 *
 * The screen's [AuthViewModel] is constructed with in-memory test doubles and passed in explicitly,
 * so the UI is exercised end-to-end (state + recomposition + navigation side-effects) without Hilt,
 * Firebase, or the network. This is the officially recommended approach of injecting fakes instead
 * of mocking the framework.
 *
 * Lives in `sharedTest`, so it runs on the JVM (Robolectric) for fast CI and on-device via the
 * instrumentation runner. The [AndroidJUnit4] runner resolves to Robolectric on the JVM and to the
 * real Android runner on-device.
 */
@RunWith(AndroidJUnit4::class)
class AuthScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val authRepository = FakeAuthRepository()
    private var navigatedToHome = false

    private fun launchAuthScreen(): AuthViewModel {
        val activity = composeTestRule.activity
        val viewModel = AuthViewModel(
            authRepository = authRepository,
            userRepository = FakeUserRepository(),
            userPreferencesRepository = FakeUserPreferencesRepository(),
            analyticsRepository = FakeAnalyticsRepository(),
            resourceProvider = FakeResourceProvider(activity),
            errorHandler = FakeErrorHandler(),
        )
        composeTestRule.setContent {
            SmartCameraTheme {
                AuthScreen(
                    onNavigateToHome = { navigatedToHome = true },
                    viewModel = viewModel,
                )
            }
        }
        return viewModel
    }

    private fun string(resId: Int) = composeTestRule.activity.getString(resId)

    @Test
    fun loginMode_showsEmailPasswordAndLoginButton_andHidesSignUpOnlyFields() {
        launchAuthScreen()

        composeTestRule.onNodeWithText(string(R.string.email)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.password)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.login)).assertExists()

        composeTestRule.onNodeWithText(string(R.string.name)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.username)).assertDoesNotExist()
    }

    @Test
    fun togglingMode_revealsSignUpFields_andSwitchesPrimaryButton() {
        launchAuthScreen()

        composeTestRule.onNodeWithText(string(R.string.need_account)).performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(string(R.string.name)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.username)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.sign_up)).assertExists()
    }

    // -------------------------------------------------------------------------
    // Sign up
    // -------------------------------------------------------------------------

    private fun toggleToSignUpMode() {
        composeTestRule.onNodeWithText(string(R.string.need_account)).performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
    }

    private fun fillSignUpFields(
        name: String = "New User",
        username: String = "newuser",
        email: String = "new@test.com",
        password: String = "password123",
    ) {
        if (name.isNotEmpty()) {
            composeTestRule.onNodeWithText(string(R.string.name)).performTextInput(name)
        }
        if (username.isNotEmpty()) {
            composeTestRule.onNodeWithText(string(R.string.username)).performTextInput(username)
        }
        composeTestRule.onNodeWithText(string(R.string.email)).performTextInput(email)
        composeTestRule.onNodeWithText(string(R.string.password)).performTextInput(password)
    }

    private fun tapSignUp() {
        composeTestRule.onNodeWithText(string(R.string.sign_up)).performScrollTo().performClick()
    }

    @Test
    fun successfulSignUp_showsVerificationEmailInfo_andResendButton() {
        launchAuthScreen()
        toggleToSignUpMode()
        fillSignUpFields()

        tapSignUp()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(string(R.string.verification_email_sent))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(string(R.string.resend_verification_email))
            .performScrollTo().assertIsDisplayed()
        assertEquals(1, authRepository.signUpCallCount)
    }

    @Test
    fun signUpWithMissingNameAndUsername_showsValidationError_andDoesNotCallRepository() {
        launchAuthScreen()
        toggleToSignUpMode()
        fillSignUpFields(name = "", username = "")

        tapSignUp()

        composeTestRule.onNodeWithText(string(R.string.all_fields_required)).assertIsDisplayed()
        assertEquals(0, authRepository.signUpCallCount)
    }

    @Test
    fun signUpWithUnavailableUsername_showsError_andDoesNotCallSignUp() {
        authRepository.usernameAvailableResult = Result.success(false)
        launchAuthScreen()
        toggleToSignUpMode()
        fillSignUpFields()

        tapSignUp()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(string(R.string.username_not_available))
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0, authRepository.signUpCallCount)
    }

    @Test
    fun signUpFailure_showsErrorMessage() {
        val errorMessage = "Email already in use"
        authRepository.signUpResult = Result.failure(RuntimeException(errorMessage))
        launchAuthScreen()
        toggleToSignUpMode()
        fillSignUpFields()

        tapSignUp()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(errorMessage).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(errorMessage).assertIsDisplayed()
        assertEquals(1, authRepository.signUpCallCount)
    }

    @Test
    fun submittingWithEmptyCredentials_showsValidationError() {
        launchAuthScreen()

        composeTestRule.onNodeWithText(string(R.string.login)).performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(string(R.string.email_password_empty)).assertExists()
    }

    @Test
    fun typingEmail_updatesViewModelState() {
        val viewModel = launchAuthScreen()

        composeTestRule.onNodeWithText(string(R.string.email)).performTextInput("user@test.com")
        composeTestRule.waitForIdle()

        assertEquals("user@test.com", viewModel.uiState.value.email)
    }

    @Test
    fun successfulSignIn_navigatesToHome() {
        // Defaults: signIn success, email verified, getUser success -> should navigate home.
        launchAuthScreen()

        composeTestRule.onNodeWithText(string(R.string.email)).performTextInput("user@test.com")
        composeTestRule.onNodeWithText(string(R.string.password)).performTextInput("password123")
        composeTestRule.onNodeWithText(string(R.string.login)).performScrollTo().performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) { navigatedToHome }

        assertEquals(1, authRepository.signInCallCount)
        assertEquals("user@test.com", authRepository.lastSignInEmail)
    }

    @Test
    fun failedSignIn_showsErrorMessage_andDoesNotNavigate() {
        val errorMessage = "Invalid credentials"
        authRepository.signInResult = Result.failure(RuntimeException(errorMessage))
        launchAuthScreen()

        composeTestRule.onNodeWithText(string(R.string.email)).performTextInput("user@test.com")
        composeTestRule.onNodeWithText(string(R.string.password)).performTextInput("wrong-password")
        composeTestRule.onNodeWithText(string(R.string.login)).performScrollTo().performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(errorMessage).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText(errorMessage).assertIsDisplayed()
        assertEquals(1, authRepository.signInCallCount)
        assertEquals(false, navigatedToHome)
    }

    @Test
    fun unverifiedEmail_showsVerificationError_withResendOption() {
        authRepository.checkEmailVerifiedResult = Result.success(false)
        launchAuthScreen()

        composeTestRule.onNodeWithText(string(R.string.email)).performTextInput("user@test.com")
        composeTestRule.onNodeWithText(string(R.string.password)).performTextInput("password123")
        composeTestRule.onNodeWithText(string(R.string.login)).performScrollTo().performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(string(R.string.email_not_verified))
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText(string(R.string.resend_verification_email))
            .performScrollTo().assertIsDisplayed()
        assertEquals(false, navigatedToHome)
    }
}