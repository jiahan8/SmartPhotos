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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val isErrorSnackBar by viewModel.isErrorSnackBar.collectAsStateWithLifecycle()
    val bottomSheetState = rememberModalBottomSheetState()
    val showBottomSheet by viewModel.showBottomSheet.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    val email by viewModel.email.collectAsStateWithLifecycle()
    val displayName by viewModel.displayName.collectAsStateWithLifecycle()
    val username by viewModel.username.collectAsStateWithLifecycle()
    val profilePictureUrl by viewModel.profilePictureUrl.collectAsStateWithLifecycle()
    val displayNameErrorMessage by viewModel.displayNameErrorMessage.collectAsStateWithLifecycle()
    val usernameErrorMessage by viewModel.usernameErrorMessage.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val isErrorFree by viewModel.isErrorFree.collectAsStateWithLifecycle()
    val isFormChanged by viewModel.isFormChanged.collectAsStateWithLifecycle()
    val isSaving by viewModel.isLoading.collectAsStateWithLifecycle()
    val isUploading by viewModel.isUploading.collectAsStateWithLifecycle()
    val updateSuccess by viewModel.updateSuccess.collectAsStateWithLifecycle()
    val uploadSuccess by viewModel.uploadSuccess.collectAsStateWithLifecycle()
    val updateError by viewModel.updateError.collectAsStateWithLifecycle()
    val dialogState by viewModel.dialogState.collectAsStateWithLifecycle()
    val photoUri by viewModel.photoUri.collectAsStateWithLifecycle()

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
            photoUri?.let { uri ->
                viewModel.uploadProfilePicture(uri)
            }
        } else {
            photoUri?.let { uri ->
                context.contentResolver.delete(uri, null, null)
                viewModel.updatePhotoUri(null)
            }
        }
    }

    val photoCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (isGranted) {
            val uri = viewModel.createImageUri(context)
            viewModel.updatePhotoUri(uri)
            uri?.let { pictureLauncher.launch(it) }
        }
    }

    LaunchedEffect(updateSuccess) {
        if (updateSuccess) {
            viewModel.updateErrorSnackBar(false)
            viewModel.updateBottomSheetVisibility(false)
            snackbarHostState.showSnackbar(updateSuccessMessage, duration = SnackbarDuration.Short)
            viewModel.resetUpdateSuccess()
        }
    }

    LaunchedEffect(uploadSuccess) {
        if (uploadSuccess) {
            viewModel.updateErrorSnackBar(false)
            viewModel.updateBottomSheetVisibility(false)
            snackbarHostState.showSnackbar(updateSuccessMessage, duration = SnackbarDuration.Short)
            viewModel.resetUploadSuccess()
        }
    }

    LaunchedEffect(updateError) {
        if (updateError) {
            viewModel.updateErrorSnackBar(true)
            viewModel.updateBottomSheetVisibility(false)
            snackbarHostState.showSnackbar(updateFailureMessage, duration = SnackbarDuration.Short)
            viewModel.resetUpdateError()
        }
    }

    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.updateBottomSheetVisibility(false) },
            sheetState = bottomSheetState
        ) {
            Column(
                modifier = Modifier
                    .wrapContentHeight()
                    .padding(bottom = 36.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            libraryLauncher.launch(
                                PickVisualMediaRequest(
                                    PickVisualMedia.ImageOnly
                                )
                            )
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.photo_library),
                        modifier = Modifier.padding(end = 12.dp),
                        contentDescription = "Choose photo"
                    )
                    Text(
                        text = stringResource(R.string.choose_from_library),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (hasCameraPermission) {
                                val uri = viewModel.createImageUri(context)
                                viewModel.updatePhotoUri(uri)
                                uri?.let { pictureLauncher.launch(it) }
                            } else {
                                photoCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.photo_camera),
                        modifier = Modifier.padding(end = 12.dp),
                        contentDescription = "Take photo"
                    )
                    Text(
                        text = stringResource(R.string.take_photo),
                        modifier = Modifier.weight(1f)
                    )
                }
                profilePictureUrl?.let {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.showDeletePictureDialog()
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.delete),
                            modifier = Modifier.padding(end = 12.dp),
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = stringResource(R.string.remove_current_picture),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    when (dialogState) {
        is ProfileViewModel.DialogState.DeletePicture -> {
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
                    IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                        Icon(
                            imageVector = Icons.Rounded.Menu,
                            contentDescription = "Menu"
                        )
                    }
                }
            )
        },
        snackbarHost = { CustomSnackbarHost(snackbarHostState, isErrorSnackBar) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = padding.calculateTopPadding(),
                    start = padding.calculateStartPadding(LayoutDirection.Ltr) + 16.dp,
                    end = padding.calculateEndPadding(LayoutDirection.Ltr) + 16.dp
                )
        ) {
            Column(
                modifier = Modifier.verticalScroll(scrollState),
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
                            .clip(CircleShape)
                            .clickable {
                                navController.navigate(
                                    Screen.PhotoPreview.createRemoteRoute(it)
                                )
                            },
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
                                        contentDescription = "Clear field",
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
                                        contentDescription = "Clear field",
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