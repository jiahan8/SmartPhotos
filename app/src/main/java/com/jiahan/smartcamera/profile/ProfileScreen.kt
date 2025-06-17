package com.jiahan.smartcamera.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.Screen
import com.jiahan.smartcamera.common.CustomSnackbarHost

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }

    val email by viewModel.email.collectAsState()
    val fullName by viewModel.fullName.collectAsState()
    val username by viewModel.username.collectAsState()
    val profilePictureUrl by viewModel.profilePictureUrl.collectAsState()
    val fullNameErrorMessage by viewModel.fullNameErrorMessage.collectAsState()
    val usernameErrorMessage by viewModel.usernameErrorMessage.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isErrorFree by viewModel.isErrorFree.collectAsState()
    val isFormChanged by viewModel.isFormChanged.collectAsState()
    val isSaving by viewModel.isLoading.collectAsState()
    val updateSuccess by viewModel.updateSuccess.collectAsState()
    val updateError by viewModel.updateError.collectAsState()

    val updateSuccessMessage = stringResource(R.string.info_updated_success)
    val updateFailureMessage = stringResource(R.string.info_updated_failure)

    LaunchedEffect(updateSuccess) {
        if (updateSuccess) {
            snackbarHostState.showSnackbar(updateSuccessMessage, duration = SnackbarDuration.Short)
            viewModel.resetUpdateSuccess()
        }
    }

    LaunchedEffect(updateError) {
        if (updateError) {
            snackbarHostState.showSnackbar(updateFailureMessage, duration = SnackbarDuration.Short)
            viewModel.resetUpdateError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.profile),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                actions = {
                    IconButton(onClick = {
                        navController.navigate(Screen.Settings.route)
                    }) {
                        Icon(
                            imageVector = Icons.Rounded.Menu,
                            contentDescription = "Menu"
                        )
                    }
                },
                windowInsets = WindowInsets(0.dp),
            )
        },
        snackbarHost = { CustomSnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = padding.calculateTopPadding(),
                    start = padding.calculateStartPadding(LayoutDirection.Ltr) + 16.dp,
                    end = padding.calculateEndPadding(LayoutDirection.Ltr) + 16.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            profilePictureUrl?.let {
                AsyncImage(
                    model = it,
                    contentDescription = "Profile Picture",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape),
                    alignment = Alignment.Center
                )
            } ?: Image(
                imageVector = Icons.Rounded.AccountCircle,
                contentDescription = "Profile Picture",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape),
                colorFilter = ColorFilter.tint(
                    MaterialTheme.colorScheme.onSurface.copy(
                        alpha = 0.7f
                    )
                )
            )

            TextButton(
                onClick = {}
            ) {
                Text(text = stringResource(R.string.change_profile_picture))
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = email,
                    onValueChange = {},
                    label = { Text(stringResource(R.string.email)) },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null) }
                )

                OutlinedTextField(
                    value = fullName,
                    onValueChange = { viewModel.updateFullNameText(it) },
                    label = { Text(stringResource(R.string.name)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )

                fullNameErrorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

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
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )

                usernameErrorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .fillMaxWidth()
                        .height(52.dp),
                    onClick = { viewModel.updateUserProfile() },
                    enabled = isFormChanged && isErrorFree && !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 1.5.dp
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.save_changes),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}