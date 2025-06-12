package com.jiahan.smartcamera.note

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.Screen
import com.jiahan.smartcamera.common.CustomSnackbarHost

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
    val scrollState = rememberScrollState()

    val user by viewModel.user.collectAsState()
    val postText by viewModel.postText.collectAsState()
    val photoUri by viewModel.photoUri.collectAsState()
    val videoUri by viewModel.videoUri.collectAsState()
    val mediaList by viewModel.mediaList.collectAsState()
    val isUploading by viewModel.uploading.collectAsState()
    val uploadSuccess by viewModel.uploadSuccess.collectAsState()
    val uploadError by viewModel.uploadError.collectAsState()
    val postTextError by viewModel.postTextError.collectAsState()
    val buttonEnabled by viewModel.postButtonEnabled.collectAsState()

    val postSuccessMessage = stringResource(R.string.post_success)
    val postFailMessage = stringResource(R.string.post_fail)

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val libraryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uriList ->
        viewModel.updateUriList(context = context, uriList = uriList)
    }

    val pictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            photoUri?.let { uri ->
                viewModel.updateUriList(context = context, uriList = listOf(uri))
            }
        } else {
            photoUri?.let { uri ->
                context.contentResolver.delete(uri, null, null)
                viewModel.updatePhotoUri(null)
            }
        }
    }

    val videoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        if (success) {
            videoUri?.let { uri ->
                viewModel.updateUriList(context = context, uriList = listOf(uri))
            }
        } else {
            videoUri?.let { uri ->
                context.contentResolver.delete(uri, null, null)
                viewModel.updateVideoUri(null)
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

    val videoCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (isGranted) {
            val uri = viewModel.createVideoUri(context)
            viewModel.updateVideoUri(uri)
            uri?.let { videoLauncher.launch(it) }
        }
    }

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
            if (isUploading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp)
                ) {
                    user?.profilePicture?.let {
                        AsyncImage(
                            model = it,
                            contentDescription = "Profile Picture",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                        )
                    } ?: Image(
                        imageVector = Icons.Rounded.AccountCircle,
                        contentDescription = "Profile Picture",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape),
                        colorFilter = ColorFilter.tint(
                            MaterialTheme.colorScheme.onSurface.copy(
                                alpha = 0.7f
                            )
                        )
                    )

                    Column(
                        modifier = Modifier.padding(start = 16.dp)
                    ) {
                        Text(
                            text = user?.username ?: "",
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
                            maxLines = 25,
                            modifier = Modifier
                                .padding(top = 8.dp, bottom = 8.dp)
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            enabled = !isUploading
                        )

                        if (mediaList.isNotEmpty()) {
                            HorizontalMultiBrowseCarousel(
                                state = rememberCarouselState { mediaList.count() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight()
                                    .padding(top = 16.dp, bottom = 16.dp),
                                preferredItemWidth = 186.dp,
                                itemSpacing = 8.dp,
                            ) { index ->
                                val noteMediaDetail = mediaList[index]
                                Box(
                                    modifier = Modifier.clickable {
                                        if (noteMediaDetail.isVideo) {
                                            navController.navigate(
                                                Screen.VideoPreview.createLocalRoute(noteMediaDetail.videoUri.toString())
                                            )
                                        } else {
                                            navController.navigate(
                                                Screen.PhotoPreview.createLocalRoute(noteMediaDetail.photoUri.toString())
                                            )
                                        }
                                    }
                                ) {
                                    AsyncImage(
                                        model = if (noteMediaDetail.isVideo) noteMediaDetail.thumbnailBitmap else noteMediaDetail.photoUri,
                                        modifier = Modifier
                                            .height(205.dp)
                                            .maskClip(MaterialTheme.shapes.extraLarge),
                                        contentDescription = "Image",
                                        contentScale = ContentScale.Crop,
                                        onError = {
                                            it.result.throwable.printStackTrace()
                                        }
                                    )

                                    if (noteMediaDetail.isVideo)
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = "Play Video",
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .size(52.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(
                                                        alpha = 0.7f
                                                    )
                                                ),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Remove Image",
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(
                                                MaterialTheme.colorScheme.surfaceVariant.copy(
                                                    alpha = 0.7f
                                                )
                                            )
                                            .clickable {
                                                viewModel.removeUriFromList(index)
                                            }
                                            .padding(3.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

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
                                painter = painterResource(R.drawable.photo_library),
                                contentDescription = "Choose photos",
                                modifier = Modifier
                                    .clickable(enabled = !isUploading) {
                                        libraryLauncher.launch(
                                            PickVisualMediaRequest(
                                                PickVisualMedia.ImageAndVideo
                                            )
                                        )
                                    },
                                tint = if (isUploading) MaterialTheme.colorScheme.onSurface.copy(
                                    alpha = 0.38f
                                )
                                else MaterialTheme.colorScheme.onSurface
                            )

                            Icon(
                                painter = painterResource(R.drawable.photo_camera),
                                contentDescription = "Take Photo",
                                modifier = Modifier
                                    .padding(start = 16.dp)
                                    .clickable(enabled = !isUploading) {
                                        if (hasCameraPermission) {
                                            val uri = viewModel.createImageUri(context)
                                            viewModel.updatePhotoUri(uri)
                                            uri?.let { pictureLauncher.launch(it) }
                                        } else {
                                            photoCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                        }
                                    },
                                tint = if (isUploading) MaterialTheme.colorScheme.onSurface.copy(
                                    alpha = 0.38f
                                )
                                else MaterialTheme.colorScheme.onSurface
                            )

                            Icon(
                                painter = painterResource(R.drawable.smart_display),
                                contentDescription = "Take Video",
                                modifier = Modifier
                                    .padding(start = 16.dp)
                                    .clickable(enabled = !isUploading) {
                                        if (hasCameraPermission) {
                                            val uri = viewModel.createVideoUri(context)
                                            viewModel.updateVideoUri(uri)
                                            uri?.let { videoLauncher.launch(it) }
                                        } else {
                                            videoCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                        }
                                    },
                                tint = if (isUploading) MaterialTheme.colorScheme.onSurface.copy(
                                    alpha = 0.38f
                                )
                                else MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            TextButton(
                                onClick = {
                                    if (postTextError == null) {
                                        viewModel.uploadPost(
                                            text = postText.trim(),
                                            mediaList = mediaList
                                        )
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
}