package com.jiahan.smartcamera.auth

import androidx.annotation.DrawableRes
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.jiahan.smartcamera.common.PasswordField
import com.jiahan.smartcamera.common.bounceScale
import com.jiahan.smartcamera.core.common.R as CommonR
import com.jiahan.smartcamera.feature.auth.R
import com.jiahan.smartcamera.ui.theme.SmartCameraTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    onNavigateToHome: () -> Unit,
    // The launcher icon, passed down rather than read here: mipmap/ic_launcher belongs to the
    // application module, and this is a library. Same hoist as SettingsScreen's `versionName`,
    // and the same reason -- a library cannot reach :app's resources any more than its BuildConfig.
    @DrawableRes logoRes: Int,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val scrollState = rememberScrollState()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val email = uiState.email
    val password = uiState.password
    val displayName = uiState.displayName
    val username = uiState.username
    val passwordVisible = uiState.passwordVisible
    val authStatus = uiState.status
    val isLoading = authStatus is AuthStatus.Loading
    val isLoginMode = uiState.isLoginMode

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                is AuthNavigationEvent.NavigateToHome -> onNavigateToHome()
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AsyncImage(
                model = logoRes,
                contentDescription = stringResource(R.string.cd_app_logo),
                contentScale = ContentScale.Crop,
            )

            OutlinedTextField(
                value = email,
                onValueChange = { viewModel.updateEmailText(it) },
                label = { Text(stringResource(CommonR.string.email)) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null) }
            )

            if (!isLoginMode) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { viewModel.updateDisplayNameText(it) },
                    label = { Text(stringResource(CommonR.string.name)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { viewModel.updateUsernameText(it) },
                    label = { Text(stringResource(CommonR.string.username)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Rounded.AccountCircle, contentDescription = null)
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
            }

            PasswordField(
                value = password,
                onValueChange = { viewModel.updatePasswordText(it) },
                label = stringResource(R.string.password),
                visible = passwordVisible,
                onVisibilityChange = { viewModel.updatePasswordVisibility(it) },
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(
                    onDone = { viewModel.submit() }
                ),
                leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
            )

            when (authStatus) {
                is AuthStatus.Error -> Text(
                    text = authStatus.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                is AuthStatus.Info -> Text(
                    text = authStatus.message,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                else -> {}
            }

            Spacer(modifier = Modifier.height(8.dp))

            val submitInteractionSource = remember { MutableInteractionSource() }
            Button(
                onClick = { viewModel.submit() },
                interactionSource = submitInteractionSource,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .bounceScale(submitInteractionSource),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 1.5.dp
                    )
                } else {
                    Text(
                        text = stringResource(if (isLoginMode) R.string.login else R.string.sign_up),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            if (!isLoginMode) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.email_verification_note),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            TextButton(onClick = { viewModel.toggleAuthMode() }) {
                Text(stringResource(if (isLoginMode) R.string.need_account else R.string.already_have_account))
            }

            if (isLoginMode) {
                TextButton(onClick = { viewModel.resetPassword() }) {
                    Text(stringResource(R.string.forgot_password))
                }
            }

            if (uiState.showResendButton) {
                TextButton(onClick = { viewModel.resendVerificationEmail() }) {
                    Text(stringResource(R.string.resend_verification_email))
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Auth – Login mode")
@Composable
private fun AuthScreenLoginPreview() {
    SmartCameraTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = "user@example.com",
                onValueChange = {},
                label = { Text(stringResource(CommonR.string.email)) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null) }
            )
            OutlinedTextField(
                value = "••••••••",
                onValueChange = {},
                label = { Text(stringResource(R.string.password)) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) }
            )
            Button(
                onClick = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(stringResource(R.string.login), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}