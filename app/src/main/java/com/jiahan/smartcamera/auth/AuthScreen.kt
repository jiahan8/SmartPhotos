package com.jiahan.smartcamera.auth

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.jiahan.smartcamera.MainViewModel
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    navController: NavController,
    mainViewModel: MainViewModel,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val scrollState = rememberScrollState()

    val email by viewModel.email.collectAsStateWithLifecycle()
    val password by viewModel.password.collectAsStateWithLifecycle()
    val displayName by viewModel.displayName.collectAsStateWithLifecycle()
    val username by viewModel.username.collectAsStateWithLifecycle()
    val passwordVisible by viewModel.passwordVisible.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val isLoginMode by viewModel.isLoginMode.collectAsStateWithLifecycle()
    val navigationEvent by viewModel.navigationEvent.collectAsStateWithLifecycle()

    LaunchedEffect(navigationEvent) {
        when (navigationEvent) {
            is AuthViewModel.NavigationEvent.NavigateToHome -> {
                mainViewModel.updateStartDestination(Screen.Home.route)
                navController.navigate(Screen.Home.route) {
                    popUpTo(0) { inclusive = true }
                }
                viewModel.navigationEventConsumed()
            }

            null -> {}
        }
    }

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
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
                    model = R.mipmap.ic_launcher,
                    contentDescription = "Background image",
                    contentScale = ContentScale.Crop,
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { viewModel.updateEmailText(it) },
                    label = { Text(stringResource(R.string.email)) },
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
                        label = { Text(stringResource(R.string.name)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )

                    OutlinedTextField(
                        value = username,
                        onValueChange = { viewModel.updateUsernameText(it) },
                        label = { Text(stringResource(R.string.username)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Rounded.AccountCircle, contentDescription = null)
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                }

                OutlinedTextField(
                    value = password,
                    onValueChange = { viewModel.updatePasswordText(it) },
                    label = { Text(stringResource(R.string.password)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    singleLine = true,
                    visualTransformation = if (passwordVisible)
                        VisualTransformation.None
                    else
                        PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (isLoginMode) {
                                viewModel.signIn()
                            } else {
                                viewModel.signUp()
                            }
                        }
                    ),
                    leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                    trailingIcon = {
                        Icon(
                            modifier = Modifier.clickable(
                                interactionSource = null,
                                indication = null
                            ) {
                                viewModel.updatePasswordVisibility(!passwordVisible)
                            },
                            painter = if (passwordVisible)
                                painterResource(R.drawable.visibility)
                            else
                                painterResource(R.drawable.visibility_off),
                            contentDescription = if (passwordVisible)
                                "Hide password"
                            else
                                "Show password"
                        )
                    }
                )

                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        if (isLoginMode) {
                            viewModel.signIn()
                        } else {
                            viewModel.signUp()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
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

                if (errorMessage.contains(stringResource(R.string.verification_email_sent)) ||
                    errorMessage.contains(stringResource(R.string.email_not_verified))
                ) {
                    TextButton(onClick = { viewModel.resendVerificationEmail() }) {
                        Text(stringResource(R.string.resend_verification_email))
                    }
                }
            }
        }
    }
}