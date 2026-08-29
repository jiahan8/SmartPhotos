package com.jiahan.smartcamera.note

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.common.ProfileAvatar
import com.jiahan.smartcamera.common.bounceClick
import com.jiahan.smartcamera.common.rememberCyclingPlaceholder
import com.jiahan.smartcamera.common.showAppSnackbar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteScreen(
    onBack: () -> Unit,
    onNavigateToPhotoPreview: (uri: String) -> Unit,
    onNavigateToVideoPreview: (uri: String) -> Unit,
    viewModel: NoteViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()

    val profilePicture by viewModel.profilePicture.collectAsStateWithLifecycle(null)
    val username by viewModel.username.collectAsStateWithLifecycle("")
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val postText = uiState.postText
    val photoUri = uiState.photoUri
    val videoUri = uiState.videoUri
    val mediaList = uiState.mediaList
    val uploadStatus = uiState.uploadStatus
    val isUploading = uploadStatus is UploadStatus.Uploading
    val postTextError = uiState.postTextError
    val postButtonEnabled by viewModel.postButtonEnabled.collectAsStateWithLifecycle()

    val (placeholder, placeholderAlpha) = rememberCyclingPlaceholder(
        options = listOf(
            stringResource(R.string.whats_new),
            stringResource(R.string.create_note),
            stringResource(R.string.write_this_moment),
            stringResource(R.string.whats_on_mind),
            stringResource(R.string.write_a_thought)
        )
    )

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
        viewModel.updateUriList(uriList = uriList)
    }

    val pictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            photoUri?.let { uri -> viewModel.updateUriList(uriList = listOf(uri)) }
        } else {
            photoUri?.let { uri -> viewModel.cancelPhotoCapture(uri) }
        }
    }

    val videoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        if (success) {
            videoUri?.let { uri -> viewModel.updateUriList(uriList = listOf(uri)) }
        } else {
            videoUri?.let { uri -> viewModel.cancelVideoCapture(uri) }
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

    val videoCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (isGranted) {
            val uri = viewModel.createVideoUri()
            viewModel.updateVideoUri(uri)
            uri?.let { videoLauncher.launch(it) }
        }
    }

    LaunchedEffect(uploadStatus) {
        when (uploadStatus) {
            is UploadStatus.Success -> {
                keyboardController?.hide()
                viewModel.resetUploadState()
                onBack()
            }

            is UploadStatus.Error -> {
                keyboardController?.hide()
                snackbarHostState.showAppSnackbar(uploadStatus.message, isError = true)
                viewModel.resetUploadState()
            }

            else -> {}
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.new_note),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
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
                        ProfileAvatar(
                            profilePictureUrl = profilePicture,
                            onImageLoadError = viewModel::logImageLoadError,
                            onClick = profilePicture?.let { url ->
                                { onNavigateToPhotoPreview(url) }
                            }
                        )

                        Column(modifier = Modifier.padding(start = 16.dp)) {
                            Text(
                                text = username,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
                                enabled = !isUploading,
                                decorationBox = { innerTextField ->
                                    Row {
                                        Box(modifier = Modifier.weight(1f)) {
                                            innerTextField()
                                            if (postText.isBlank()) {
                                                Text(
                                                    text = placeholder,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                        alpha = 0.7f
                                                    ),
                                                    modifier = Modifier.graphicsLayer(alpha = placeholderAlpha)
                                                )
                                            }
                                        }
                                        if (postText.isNotBlank() && !isUploading) {
                                            Icon(
                                                imageVector = Icons.Rounded.Clear,
                                                contentDescription = stringResource(R.string.cd_clear_field),
                                                modifier = Modifier
                                                    .padding(end = 12.dp)
                                                    .size(16.dp)
                                                    .clickable { viewModel.updatePostText("") },
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            )

                            NoteMediaCarousel(
                                mediaList = mediaList,
                                onPhotoClick = onNavigateToPhotoPreview,
                                onVideoClick = onNavigateToVideoPreview,
                                onRemoveMedia = { index -> viewModel.removeUriFromList(index) },
                                onImageLoadError = viewModel::logImageLoadError
                            )

                            postTextError?.let { error ->
                                Text(
                                    text = error,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            NoteActionRow(
                                isUploading = isUploading,
                                postButtonEnabled = postButtonEnabled,
                                onPickFromLibrary = {
                                    libraryLauncher.launch(
                                        PickVisualMediaRequest(PickVisualMedia.ImageAndVideo)
                                    )
                                },
                                onTakePhoto = {
                                    if (hasCameraPermission) {
                                        val uri = viewModel.createImageUri()
                                        viewModel.updatePhotoUri(uri)
                                        uri?.let { pictureLauncher.launch(it) }
                                    } else {
                                        photoCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                },
                                onTakeVideo = {
                                    if (hasCameraPermission) {
                                        val uri = viewModel.createVideoUri()
                                        viewModel.updateVideoUri(uri)
                                        uri?.let { videoLauncher.launch(it) }
                                    } else {
                                        videoCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                },
                                onSave = { viewModel.uploadPost() }
                            )
                        }
                    }
                }
                if (isUploading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(strokeWidth = 1.5.dp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteMediaCarousel(
    mediaList: List<NoteMediaDetail>,
    onPhotoClick: (uri: String) -> Unit,
    onVideoClick: (uri: String) -> Unit,
    onRemoveMedia: (index: Int) -> Unit,
    onImageLoadError: (Throwable) -> Unit
) {
    if (mediaList.isEmpty()) return

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
        val displayedUri =
            if (noteMediaDetail.isVideo) noteMediaDetail.thumbnailUri else noteMediaDetail.photoUri
        Box(
            modifier = Modifier.clickable {
                if (noteMediaDetail.isVideo) {
                    onVideoClick(noteMediaDetail.videoUri?.value.orEmpty())
                } else {
                    onPhotoClick(noteMediaDetail.photoUri?.value.orEmpty())
                }
            }
        ) {
            AsyncImage(
                model = displayedUri?.value,
                modifier = Modifier
                    .height(212.dp)
                    .maskClip(MaterialTheme.shapes.extraLarge),
                contentDescription = stringResource(R.string.cd_note_photo),
                contentScale = ContentScale.Crop,
                onError = { onImageLoadError(it.result.throwable) }
            )

            if (noteMediaDetail.isVideo)
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.cd_play_video),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )

            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.cd_remove_image),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    )
                    .clickable { onRemoveMedia(index) }
                    .padding(3.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NoteActionRow(
    isUploading: Boolean,
    postButtonEnabled: Boolean,
    onPickFromLibrary: () -> Unit,
    onTakePhoto: () -> Unit,
    onTakeVideo: () -> Unit,
    onSave: () -> Unit
) {
    val tint = if (isUploading)
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    else MaterialTheme.colorScheme.onSurface

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            painter = painterResource(R.drawable.photo_library),
            contentDescription = stringResource(R.string.cd_choose_photos),
            modifier = Modifier.bounceClick(
                enabled = !isUploading,
                scaleDown = 0.85f,
                onClick = onPickFromLibrary
            ),
            tint = tint
        )

        Icon(
            painter = painterResource(R.drawable.photo_camera),
            contentDescription = stringResource(R.string.cd_take_photo),
            modifier = Modifier
                .padding(start = 16.dp)
                .bounceClick(enabled = !isUploading, scaleDown = 0.85f, onClick = onTakePhoto),
            tint = tint
        )

        Icon(
            painter = painterResource(R.drawable.smart_display),
            contentDescription = stringResource(R.string.cd_take_video),
            modifier = Modifier
                .padding(start = 16.dp)
                .bounceClick(enabled = !isUploading, scaleDown = 0.85f, onClick = onTakeVideo),
            tint = tint
        )

        Spacer(modifier = Modifier.weight(1f))

        TextButton(
            onClick = onSave,
            enabled = postButtonEnabled
        ) {
            Text(text = stringResource(R.string.save))
        }
    }
}