package com.jiahan.smartcamera.profile

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.common.BottomSheetActionItem
import com.jiahan.smartcamera.common.showAppSnackbar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToPhotoPreview: (url: String) -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val bottomSheetState = rememberModalBottomSheetState()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val showBottomSheet = uiState.showBottomSheet
    val scrollState = rememberScrollState()

    val email = uiState.email
    val displayName = uiState.displayName
    val username = uiState.username
    val profilePictureUrl = uiState.profilePictureUrl
    val displayNameErrorMessage = uiState.displayNameErrorMessage
    val usernameErrorMessage = uiState.usernameErrorMessage
    val errorMessage = uiState.errorMessage
    val isErrorFree = uiState.isErrorFree
    val isFormChanged = uiState.isFormChanged
    val isSaving = uiState.isLoading
    val isUploading = uiState.isUploading
    val dialogState = uiState.dialogState
    val photoUri = uiState.photoUri

    val updateSuccessMessage = stringResource(R.string.info_updated_success)
    val updateFailureMessage = stringResource(R.string.info_updated_failure)

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val libraryLauncher = rememberLauncherForActivityResult(
        contract = PickVisualMedia()
    ) { uri ->
        uri?.let {
            viewModel.uploadProfilePicture(uri)
        }
    }

    val pictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            photoUri?.let { uri -> viewModel.uploadProfilePicture(uri) }
        } else {
            photoUri?.let { uri -> viewModel.cancelPhotoCapture(uri) }
        }
    }

    val photoCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (isGranted) {
            val uri = viewModel.createImageUri()
            viewModel.updatePhotoUri(uri)
            uri?.let { pictureLauncher.launch(it) }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                ProfileEvent.UpdateSuccess, ProfileEvent.UploadSuccess -> {
                    snackbarHostState.showAppSnackbar(updateSuccessMessage)
                }

                is ProfileEvent.UpdateError -> {
                    snackbarHostState.showAppSnackbar(
                        event.message ?: updateFailureMessage,
                        isError = true
                    )
                }
            }
        }
    }

    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.updateBottomSheetVisibility(false) },
            sheetState = bottomSheetState
        ) {
            Column(modifier = Modifier.wrapContentHeight()) {
                BottomSheetActionItem(
                    icon = Icons.Outlined.PhotoLibrary,
                    label = stringResource(R.string.choose_from_library),
                    onClick = {
                        libraryLauncher.launch(
                            PickVisualMediaRequest(
                                PickVisualMedia.ImageOnly
                            )
                        )
                    }
                )
                BottomSheetActionItem(
                    icon = Icons.Outlined.PhotoCamera,
                    label = stringResource(R.string.take_photo),
                    onClick = {
                        if (hasCameraPermission) {
                            val uri = viewModel.createImageUri()
                            viewModel.updatePhotoUri(uri)
                            uri?.let { pictureLauncher.launch(it) }
                        } else {
                            photoCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                )
                profilePictureUrl?.let {
                    BottomSheetActionItem(
                        icon = Icons.Outlined.Delete,
                        label = stringResource(R.string.remove_current_picture),
                        onClick = { viewModel.showDeletePictureDialog() },
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    when (dialogState) {
        is ProfileDialogState.DeletePicture -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissDialog() },
                title = { Text(stringResource(R.string.delete_picture)) },
                text = { Text(stringResource(R.string.delete_picture_desc)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteProfilePicture()
                            viewModel.dismissDialog()
                        }
                    ) {
                        Text(stringResource(R.string.delete_picture))
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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.profile),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Rounded.Menu,
                            contentDescription = stringResource(R.string.cd_open_settings)
                        )
                    }
                }
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.verticalScroll(scrollState),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    profilePictureUrl?.let {
                        AsyncImage(
                            model = it,
                            contentDescription = stringResource(R.string.cd_profile_picture),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .clickable {
                                    onNavigateToPhotoPreview(it)
                                },
                            alignment = Alignment.Center,
                            onError = { viewModel.logImageLoadError(it.result.throwable) }
                        )
                    } ?: Image(
                        imageVector = Icons.Rounded.AccountCircle,
                        contentDescription = stringResource(R.string.cd_profile_picture),
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
                        onClick = { viewModel.updateBottomSheetVisibility(true) }
                    ) {
                        Text(text = stringResource(R.string.edit_picture))
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
                            value = displayName,
                            onValueChange = { viewModel.updateDisplayNameText(it) },
                            label = { Text(stringResource(R.string.name)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                            trailingIcon = {
                                if (displayName.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.updateDisplayNameText("") }) {
                                        Icon(
                                            imageVector = Icons.Rounded.Clear,
                                            contentDescription = stringResource(R.string.cd_clear_field),
                                            modifier = Modifier
                                                .size(16.dp)
                                        )
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )

                        displayNameErrorMessage?.let {
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
                            trailingIcon = {
                                if (username.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.updateUsernameText("") }) {
                                        Icon(
                                            imageVector = Icons.Rounded.Clear,
                                            contentDescription = stringResource(R.string.cd_clear_field),
                                            modifier = Modifier
                                                .size(16.dp)
                                        )
                                    }
                                }
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
                if (isUploading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {},
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 1.5.dp
                        )
                    }
                }
            }
        }
    }
}