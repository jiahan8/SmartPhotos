package com.jiahan.smartcamera.note

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.jiahan.smartcamera.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteScreen(
    navController: NavController,
    viewModel: NoteViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val postText by viewModel.postText.collectAsState()
    val photoUri by viewModel.photoUri.collectAsState()
    val videoUri by viewModel.videoUri.collectAsState()
    val isUploading by viewModel.uploading.collectAsState()
    val uploadSuccess by viewModel.uploadSuccess.collectAsState()
    val uploadError by viewModel.uploadError.collectAsState()
    val postTextError by viewModel.postTextError.collectAsState()
    val buttonEnabled by viewModel.postButtonEnabled.collectAsState()

    val postSuccessMessage = stringResource(R.string.post_success)
    val postFailMessage = stringResource(R.string.post_fail)

    LaunchedEffect(uploadSuccess) {
        if (uploadSuccess) {
            keyboardController?.hide()
            snackbarHostState.showSnackbar(postSuccessMessage, duration = SnackbarDuration.Short)
            viewModel.resetUploadSuccess()
            viewModel.resetUploading()
            navController.popBackStack()
        }
    }

    LaunchedEffect(uploadError) {
        if (uploadError) {
            keyboardController?.hide()
            snackbarHostState.showSnackbar(postFailMessage, duration = SnackbarDuration.Short)
            viewModel.resetUploadError()
        }
    }

    val libraryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uriList ->
        uriList.forEach { uri ->
            viewModel.uploadImageToFirebase(context, uri)
        }
    }

    val pictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            photoUri?.let { uri ->
                viewModel.uploadImageToFirebase(context, uri)
            }
        }
    }

    val videoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        if (success) {
            videoUri?.let { uri ->
                viewModel.uploadImageToFirebase(context, uri)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.new_note),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                windowInsets = WindowInsets(0.dp),
            )
        },
        snackbarHost = { CustomSnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = padding.calculateTopPadding(),
                    start = padding.calculateStartPadding(LayoutDirection.Ltr),
                    end = padding.calculateEndPadding(LayoutDirection.Ltr)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp)
            ) {
                AsyncImage(
                    model = R.drawable.home_image,
                    contentDescription = "Profile picture",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                )

                Column(
                    modifier = Modifier.padding(start = 16.dp)
                ) {
                    Text(
                        text = "jiahan",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )

                    BasicTextField(
                        value = postText,
                        onValueChange = { text -> viewModel.updatePostText(text) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        maxLines = 20,
                        modifier = Modifier
                            .padding(top = 8.dp, bottom = 8.dp)
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        enabled = !isUploading
                    )

                    postTextError?.let { error ->
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.photo_library_24px),
                            contentDescription = "Choose photos",
                            modifier = Modifier
                                .clickable(enabled = !isUploading) {
                                    libraryLauncher.launch(arrayOf("image/*", "video/*"))
                                },
                            tint = if (isUploading) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            else MaterialTheme.colorScheme.onSurface
                        )

                        Icon(
                            painter = painterResource(R.drawable.photo_camera_24px),
                            contentDescription = "Take Photo",
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .clickable(enabled = !isUploading) {
                                    val uri = viewModel.createImageUri(context)
                                    viewModel.updatePhotoUri(uri)
                                    photoUri?.let { uri ->
                                        pictureLauncher.launch(uri)
                                    }
                                },
                            tint = if (isUploading) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            else MaterialTheme.colorScheme.onSurface
                        )

                        Icon(
                            painter = painterResource(R.drawable.smart_display_24px),
                            contentDescription = "Take Video",
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .clickable(enabled = !isUploading) {
                                    val uri = viewModel.createImageUri(context)
                                    viewModel.updateVideoUri(uri)
                                    videoUri?.let { uri ->
                                        videoLauncher.launch(uri)
                                    }
                                },
                            tint = if (isUploading) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            else MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        TextButton(
                            onClick = {
                                if (postTextError == null) {
                                    viewModel.uploadPost(postText.trim())
                                }
                            },
                            enabled = buttonEnabled
                        ) {
                            Text(text = stringResource(R.string.post))
                        }
                    }

                    LaunchedEffect(Unit) {
                        focusRequester.requestFocus()
                    }
                }
            }
        }
    }
}

@Composable
fun CustomSnackbarHost(
    snackbarHostState: SnackbarHostState
) {
    SnackbarHost(
        hostState = snackbarHostState,
        snackbar = { snackbarData ->
            Snackbar(
                snackbarData = snackbarData,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                actionColor = MaterialTheme.colorScheme.secondary,
                shape = MaterialTheme.shapes.medium
            )
        }
    )
}