package com.jiahan.smartcamera.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.common.CustomSnackbarHost

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToAuth: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val packageName = remember { context.packageName }
    val locale = ConfigurationCompat.getLocales(configuration).get(0)
    val snackbarHostState = remember { SnackbarHostState() }

    val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dialogState by viewModel.dialogState.collectAsStateWithLifecycle()

    val isLoading = uiState is SettingsUiState.Loading
    val isErrorSnackBar = uiState is SettingsUiState.Error

    val actionFailureMessage = stringResource(R.string.action_failure)

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

    LaunchedEffect(uiState) {
        if (uiState is SettingsUiState.Error) {
            snackbarHostState.showSnackbar(actionFailureMessage, duration = SnackbarDuration.Short)
            viewModel.resetActionError()
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
                        Text(stringResource(R.string.cancel))
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
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        else -> {}
    }

    Scaffold(
        topBar = {
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
        },
        snackbarHost = { CustomSnackbarHost(snackbarHostState, isErrorSnackBar) }
    ) { padding ->
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
                        text = locale.displayLanguage,
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
    }
}