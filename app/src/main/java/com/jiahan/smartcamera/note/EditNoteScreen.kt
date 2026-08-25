package com.jiahan.smartcamera.note

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.common.FullScreenMessage
import com.jiahan.smartcamera.common.ProfileAvatar
import com.jiahan.smartcamera.common.bounceClick
import com.jiahan.smartcamera.common.shimmer
import com.jiahan.smartcamera.common.showAppSnackbar
import com.jiahan.smartcamera.domain.HomeNote

/**
 * Edits an existing note's text. Its media is fixed at creation time, so it is rendered read-only
 * here (tapping still opens the full-screen preview) and there is nothing to attach or remove --
 * that is what separates this screen from [NoteScreen], which composes a brand-new note.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditNoteScreen(
    onBack: () -> Unit,
    onNavigateToPhotoPreview: (url: String) -> Unit,
    onNavigateToVideoPreview: (url: String) -> Unit,
    viewModel: EditNoteViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val saveButtonEnabled by viewModel.saveButtonEnabled.collectAsStateWithLifecycle()
    val hasUnsavedChanges by viewModel.hasUnsavedChanges.collectAsStateWithLifecycle()
    val content = uiState.content
    val noteText = uiState.noteText
    val noteTextError = uiState.noteTextError
    val saveStatus = uiState.saveStatus
    val isSaving = saveStatus is SaveStatus.Saving

    // Leaving without saving discards the edit; the note itself is untouched until Save runs.
    val discardAndLeave = {
        keyboardController?.hide()
        onBack()
    }

    // Every way out of an edited note routes through the confirmation: the top bar arrow, Cancel,
    // and -- via BackHandler below -- the system back gesture, which would otherwise skip it.
    val leaveScreen = {
        if (hasUnsavedChanges) viewModel.setShowDiscardDialog(true) else discardAndLeave()
    }

    BackHandler(enabled = hasUnsavedChanges && !isSaving) {
        viewModel.setShowDiscardDialog(true)
    }

    if (uiState.showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.setShowDiscardDialog(false) },
            title = { Text(stringResource(R.string.discard_changes)) },
            text = { Text(stringResource(R.string.discard_changes_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setShowDiscardDialog(false)
                        discardAndLeave()
                    }
                ) {
                    Text(stringResource(R.string.discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setShowDiscardDialog(false) }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    LaunchedEffect(saveStatus) {
        when (saveStatus) {
            is SaveStatus.Success -> {
                keyboardController?.hide()
                viewModel.resetSaveStatus()
                onBack()
            }

            is SaveStatus.Error -> {
                keyboardController?.hide()
                snackbarHostState.showAppSnackbar(saveStatus.message, isError = true)
                viewModel.resetSaveStatus()
            }

            else -> {}
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.edit_note),
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            navigationIcon = {
                IconButton(onClick = leaveScreen, enabled = !isSaving) {
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
                .fillMaxWidth()
        ) {
            when (content) {
                is EditNoteContent.Loading ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(strokeWidth = 1.5.dp)
                    }

                is EditNoteContent.Error -> FullScreenMessage(content.message)

                is EditNoteContent.Success -> {
                    val note = content.note

                    LaunchedEffect(Unit) {
                        focusRequester.requestFocus()
                    }

                    // Places the cursor at the end of the pre-filled text rather than wherever a
                    // String-only BasicTextField would default it to, and re-applies on any
                    // external change (e.g. the trailing clear icon).
                    var noteTextFieldValue by remember {
                        mutableStateOf(
                            TextFieldValue(text = noteText, selection = TextRange(noteText.length))
                        )
                    }
                    LaunchedEffect(noteText) {
                        if (noteTextFieldValue.text != noteText) {
                            noteTextFieldValue = TextFieldValue(
                                text = noteText,
                                selection = TextRange(noteText.length)
                            )
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
                            ProfileAvatar(
                                profilePictureUrl = note.profilePictureUrl,
                                onImageLoadError = viewModel::logImageLoadError,
                                onClick = note.profilePictureUrl?.let { url ->
                                    { onNavigateToPhotoPreview(url) }
                                }
                            )

                            Column(modifier = Modifier.padding(start = 16.dp)) {
                                Text(
                                    text = note.username,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                BasicTextField(
                                    value = noteTextFieldValue,
                                    onValueChange = { newValue ->
                                        noteTextFieldValue = newValue
                                        viewModel.updateNoteText(newValue.text)
                                    },
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    maxLines = 25,
                                    modifier = Modifier
                                        .padding(top = 8.dp, bottom = 8.dp)
                                        .fillMaxWidth()
                                        .focusRequester(focusRequester),
                                    enabled = !isSaving,
                                    decorationBox = { innerTextField ->
                                        Row {
                                            Box(modifier = Modifier.weight(1f)) {
                                                innerTextField()
                                            }
                                            if (noteText.isNotBlank() && !isSaving) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Clear,
                                                    contentDescription = stringResource(R.string.cd_clear_field),
                                                    modifier = Modifier
                                                        .padding(end = 12.dp)
                                                        .size(16.dp)
                                                        .clickable { viewModel.updateNoteText("") },
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                )

                                ReadOnlyNoteMedia(
                                    note = note,
                                    onNavigateToPhotoPreview = onNavigateToPhotoPreview,
                                    onNavigateToVideoPreview = onNavigateToVideoPreview,
                                    onImageLoadError = viewModel::logImageLoadError
                                )

                                noteTextError?.let { error ->
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
                                    Spacer(modifier = Modifier.weight(1f))

                                    TextButton(
                                        onClick = leaveScreen,
                                        enabled = !isSaving
                                    ) {
                                        Text(
                                            text = stringResource(R.string.cancel),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    TextButton(
                                        onClick = { viewModel.saveNote() },
                                        enabled = saveButtonEnabled
                                    ) {
                                        Text(text = stringResource(R.string.save))
                                    }
                                }
                            }
                        }
                    }

                    if (isSaving) {
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadOnlyNoteMedia(
    note: HomeNote,
    onNavigateToPhotoPreview: (url: String) -> Unit,
    onNavigateToVideoPreview: (url: String) -> Unit,
    onImageLoadError: (Throwable) -> Unit
) {
    val mediaList = note.mediaList.orEmpty()
    if (mediaList.isEmpty()) return

    HorizontalMultiBrowseCarousel(
        state = rememberCarouselState { mediaList.size },
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(top = 16.dp, bottom = 16.dp),
        preferredItemWidth = 186.dp,
        itemSpacing = 8.dp,
    ) { index ->
        val mediaDetail = mediaList[index]
        val imageUrl = if (mediaDetail.isVideo) mediaDetail.thumbnailUrl else mediaDetail.photoUrl

        // Keyed on imageUrl so a recycled carousel slot resets to loading for its new media
        // instead of keeping the previous item's loaded state.
        var isImageLoading by remember(imageUrl) { mutableStateOf(true) }

        Box(
            modifier = Modifier.bounceClick {
                if (mediaDetail.isVideo) {
                    onNavigateToVideoPreview(mediaDetail.videoUrl.orEmpty())
                } else {
                    onNavigateToPhotoPreview(mediaDetail.photoUrl.orEmpty())
                }
            },
        ) {
            // Drawn before (and therefore behind) the media, so it never tints the image during
            // the frame where both are on screen. maskClip already shapes/sizes this to the
            // carousel item, so RectangleShape here only adds the shimmer sweep, not a re-clip.
            if (isImageLoading) {
                Box(
                    modifier =
                        Modifier
                            .height(212.dp)
                            .maskClip(MaterialTheme.shapes.extraLarge)
                            .shimmer(RectangleShape)
                )
            }

            AsyncImage(
                model = imageUrl,
                modifier =
                    Modifier
                        .height(212.dp)
                        .maskClip(MaterialTheme.shapes.extraLarge),
                contentDescription = stringResource(R.string.cd_note_photo),
                contentScale = ContentScale.Crop,
                onLoading = { isImageLoading = true },
                onSuccess = { isImageLoading = false },
                onError = {
                    isImageLoading = false
                    onImageLoadError(it.result.throwable)
                }
            )

            if (mediaDetail.isVideo) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.cd_play_video),
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}