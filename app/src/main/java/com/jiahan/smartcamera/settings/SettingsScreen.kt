package com.jiahan.smartcamera.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val navigationEvent by viewModel.navigationEvent.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val dialogState by viewModel.dialogState.collectAsState()

    LaunchedEffect(navigationEvent) {
        when (navigationEvent) {
            is SettingsViewModel.NavigationEvent.NavigateToAuth -> {
                navController.navigate(Screen.Auth.route) {
                    popUpTo(0) { inclusive = true }
                }
                viewModel.navigationEventConsumed()
            }

            null -> {}
        }
    }

    when (dialogState) {
        is SettingsViewModel.DialogState.Logout -> {
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

        is SettingsViewModel.DialogState.DeleteAccount -> {
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
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                windowInsets = WindowInsets(0.dp),
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
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
                Text(
                    text = stringResource(R.string.dark_theme),
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = isDarkTheme,
                    onCheckedChange = { newValue ->
                        viewModel.updateIsDarkTheme(newValue)
                    },
                    thumbContent = if (isDarkTheme) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    } else {
                        null
                    }
                )
            }
            HorizontalDivider(thickness = 0.5.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                    }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.language),
                    modifier = Modifier.weight(1f)
                )
            }
            HorizontalDivider(thickness = 0.5.dp)
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
            HorizontalDivider(thickness = 0.5.dp)
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