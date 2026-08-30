package com.jiahan.smartcamera.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jiahan.smartcamera.BuildConfig
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.common.showAppSnackbar
import com.jiahan.smartcamera.core.ui.R as UiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToAuth: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val hapticFeedback = LocalHapticFeedback.current
    val packageName = remember { context.packageName }
    val locale = ConfigurationCompat.getLocales(configuration).get(0)

    val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dialogState = uiState.dialogState

    val isLoading = uiState.status is SettingsStatus.Loading

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                SettingsNavigationEvent.NavigateToAuth -> onNavigateToAuth()
                SettingsNavigationEvent.OpenLanguageSettings -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS)
                        intent.data = android.net.Uri.fromParts("package", packageName, null)
                        context.startActivity(intent)
                    } else {
                        context.startActivity(Intent(Settings.ACTION_LOCALE_SETTINGS))
                    }
                }
            }
        }
    }

    LaunchedEffect(uiState.status) {
        val status = uiState.status
        if (status is SettingsStatus.Error) {
            snackbarHostState.showAppSnackbar(status.message, isError = true)
            viewModel.resetActionError()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.changePasswordEvent.collect { event ->
            when (event) {
                is SettingsChangePasswordEvent.Success ->
                    snackbarHostState.showAppSnackbar(event.message)
            }
        }
    }

    when (dialogState) {
        is SettingsDialogState.Logout -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissDialog() },
                title = { Text(stringResource(R.string.log_out)) },
                text = { Text(stringResource(R.string.log_out_desc)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.signOut()
                            viewModel.dismissDialog()
                        }
                    ) {
                        Text(stringResource(R.string.log_out))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissDialog() }) {
                        Text(stringResource(UiR.string.cancel))
                    }
                }
            )
        }

        is SettingsDialogState.ChangePassword -> {
            AlertDialog(
                onDismissRequest = { if (!isLoading) viewModel.dismissDialog() },
                title = { Text(stringResource(R.string.change_password)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = dialogState.currentPassword,
                            onValueChange = { viewModel.updateCurrentPasswordText(it) },
                            label = { Text(stringResource(R.string.current_password)) },
                            shape = MaterialTheme.shapes.large,
                            singleLine = true,
                            visualTransformation = if (dialogState.currentPasswordVisible)
                                VisualTransformation.None
                            else
                                PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Next
                            ),
                            trailingIcon = {
                                Icon(
                                    modifier = Modifier.clickable(
                                        interactionSource = null,
                                        indication = null
                                    ) {
                                        viewModel.updateCurrentPasswordVisibility(!dialogState.currentPasswordVisible)
                                    },
                                    painter = if (dialogState.currentPasswordVisible)
                                        painterResource(R.drawable.visibility)
                                    else
                                        painterResource(R.drawable.visibility_off),
                                    contentDescription = if (dialogState.currentPasswordVisible)
                                        stringResource(R.string.cd_hide_password)
                                    else
                                        stringResource(R.string.cd_show_password)
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = dialogState.newPassword,
                            onValueChange = { viewModel.updateNewPasswordText(it) },
                            label = { Text(stringResource(R.string.new_password)) },
                            isError = dialogState.newPasswordErrorMessage != null,
                            supportingText = dialogState.newPasswordErrorMessage?.let { { Text(it) } },
                            shape = MaterialTheme.shapes.large,
                            singleLine = true,
                            visualTransformation = if (dialogState.newPasswordVisible)
                                VisualTransformation.None
                            else
                                PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Next
                            ),
                            trailingIcon = {
                                Icon(
                                    modifier = Modifier.clickable(
                                        interactionSource = null,
                                        indication = null
                                    ) {
                                        viewModel.updateNewPasswordVisibility(!dialogState.newPasswordVisible)
                                    },
                                    painter = if (dialogState.newPasswordVisible)
                                        painterResource(R.drawable.visibility)
                                    else
                                        painterResource(R.drawable.visibility_off),
                                    contentDescription = if (dialogState.newPasswordVisible)
                                        stringResource(R.string.cd_hide_password)
                                    else
                                        stringResource(R.string.cd_show_password)
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = dialogState.confirmNewPassword,
                            onValueChange = { viewModel.updateConfirmNewPasswordText(it) },
                            label = { Text(stringResource(R.string.confirm_new_password)) },
                            isError = dialogState.confirmNewPasswordErrorMessage != null,
                            supportingText = dialogState.confirmNewPasswordErrorMessage?.let {
                                { Text(it) }
                            },
                            shape = MaterialTheme.shapes.large,
                            singleLine = true,
                            visualTransformation = if (dialogState.confirmNewPasswordVisible)
                                VisualTransformation.None
                            else
                                PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { viewModel.changePassword() }
                            ),
                            trailingIcon = {
                                Icon(
                                    modifier = Modifier.clickable(
                                        interactionSource = null,
                                        indication = null
                                    ) {
                                        viewModel.updateConfirmNewPasswordVisibility(!dialogState.confirmNewPasswordVisible)
                                    },
                                    painter = if (dialogState.confirmNewPasswordVisible)
                                        painterResource(R.drawable.visibility)
                                    else
                                        painterResource(R.drawable.visibility_off),
                                    contentDescription = if (dialogState.confirmNewPasswordVisible)
                                        stringResource(R.string.cd_hide_password)
                                    else
                                        stringResource(R.string.cd_show_password)
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.changePassword() },
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 1.5.dp
                            )
                        } else {
                            Text(stringResource(R.string.change_password))
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.dismissDialog() },
                        enabled = !isLoading
                    ) {
                        Text(stringResource(UiR.string.cancel))
                    }
                }
            )
        }

        is SettingsDialogState.DeleteAccount -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissDialog() },
                title = { Text(stringResource(R.string.delete_account)) },
                text = { Text(stringResource(R.string.delete_account_desc)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteAccount()
                            viewModel.dismissDialog()
                        }
                    ) {
                        Text(stringResource(R.string.delete_account))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissDialog() }) {
                        Text(stringResource(UiR.string.cancel))
                    }
                }
            )
        }

        else -> {}
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            ) {
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 1.5.dp
                        )
                    }
                }
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Row(
                            modifier = Modifier
                                .wrapContentHeight()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.dark_mode),
                                modifier = Modifier.padding(end = 12.dp),
                                contentDescription = null
                            )
                            Text(
                                text = stringResource(R.string.dark_theme),
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = isDarkTheme,
                                onCheckedChange = { newValue ->
                                    hapticFeedback.performHapticFeedback(if (newValue) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                                    viewModel.updateDarkThemeVisibility(newValue)
                                },
                                thumbContent = if (isDarkTheme) {
                                    {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                } else {
                                    null
                                }
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.openLanguageSettings() }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.translate),
                                modifier = Modifier.padding(end = 12.dp),
                                contentDescription = null
                            )
                            Text(
                                text = stringResource(R.string.language),
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            locale?.let {
                                Text(
                                    text = it.displayLanguage,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 12.dp),
                                )
                            }
                        }
                        HorizontalDivider(thickness = 1.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.showChangePasswordDialog()
                                }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.change_password),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.showLogoutDialog()
                                }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.log_out),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.showDeleteAccountDialog()
                                }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.delete_account),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.app_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                    )
                }
            }
        }
    }
}