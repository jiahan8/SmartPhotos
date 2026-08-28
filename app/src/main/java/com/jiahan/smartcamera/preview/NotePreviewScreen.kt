package com.jiahan.smartcamera.preview

import android.content.ClipData
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ShareCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.common.BottomSheetActionItem
import com.jiahan.smartcamera.common.DeleteNoteConfirmationDialog
import com.jiahan.smartcamera.common.FullScreenMessage
import com.jiahan.smartcamera.common.MediaThumbnail
import com.jiahan.smartcamera.common.NoteItemSkeleton
import com.jiahan.smartcamera.common.ProfileAvatar
import com.jiahan.smartcamera.common.showAppSnackbar
import com.jiahan.smartcamera.util.AppConstants.ANIMATION_DURATION_SHORT_MS
import com.jiahan.smartcamera.util.toFormattedDateTime
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotePreviewScreen(
    onBack: () -> Unit,
    onNavigateToPhotoPreview: (url: String) -> Unit,
    onNavigateToVideoPreview: (url: String) -> Unit,
    onNavigateToEdit: (noteId: String) -> Unit,
    viewModel: NotePreviewViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showActionsSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    fun openActionsSheet() {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        showActionsSheet = true
    }

    fun closeSheetThen(action: () -> Unit) {
        coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
            showActionsSheet = false
            action()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.actionError.collect { message ->
            snackbarHostState.showAppSnackbar(message, isError = true)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.shareEvent.collect { shareContent ->
            val intentBuilder = ShareCompat.IntentBuilder(context)
                .setType(if (shareContent.uris.isEmpty()) "text/plain" else "*/*")
            shareContent.text?.let { intentBuilder.setText(it) }
            shareContent.uris.forEach { intentBuilder.addStream(it) }
            intentBuilder.startChooser()
        }
    }

    uiState.noteToDelete?.let { note ->
        DeleteNoteConfirmationDialog(
            onDismissRequest = { viewModel.setNoteToDelete(null) },
            onConfirmDelete = {
                viewModel.deleteNote(note.noteId)
                viewModel.setNoteToDelete(null)
                onBack()
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.note),
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
                    .fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = uiState.content,
                    modifier = Modifier.fillMaxSize(),
                    contentKey = { it::class },
                    transitionSpec = {
                        fadeIn(tween(ANIMATION_DURATION_SHORT_MS)) togetherWith
                                fadeOut(tween(ANIMATION_DURATION_SHORT_MS))
                    },
                    label = "NotePreviewContent"
                ) { state ->
                    when (state) {
                        is NotePreviewContent.Loading -> NoteItemSkeleton()

                        is NotePreviewContent.Error -> FullScreenMessage(state.message)

                        is NotePreviewContent.Success -> {
                            val note = state.note
                            Box(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(scrollState)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                                    ) {
                                        ProfileAvatar(
                                            profilePictureUrl = note.profilePictureUrl,
                                            onImageLoadError = viewModel::logImageLoadError,
                                            onClick =
                                                note.profilePictureUrl?.let { url ->
                                                    { onNavigateToPhotoPreview(url) }
                                                }
                                        )

                                        Column(
                                            modifier = Modifier.padding(start = 16.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text(
                                                        text = note.username,
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            fontWeight = FontWeight.Bold
                                                        ),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f, fill = false)
                                                    )

                                                    Text(
                                                        text = note.createdDate?.toEpochMilli()
                                                            ?.toFormattedDateTime()
                                                            ?: "",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(start = 8.dp),
                                                        maxLines = 1
                                                    )
                                                }

                                                Icon(
                                                    imageVector = Icons.Rounded.MoreHoriz,
                                                    contentDescription = stringResource(R.string.cd_more_options),
                                                    modifier = Modifier
                                                        .padding(start = 8.dp)
                                                        .clickable {
                                                            openActionsSheet()
                                                        }
                                                )
                                            }

                                            note.text?.let { text ->
                                                Text(
                                                    text = text,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                        }
                                    }

                                    note.mediaList?.takeIf { it.isNotEmpty() }?.let { mediaList ->
                                        LazyRow(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 8.dp),
                                            contentPadding = PaddingValues(
                                                start = 56.dp,
                                                end = 8.dp
                                            )
                                        ) {
                                            items(
                                                count = mediaList.size,
                                                key = { index ->
                                                    val media = mediaList[index]
                                                    "${index}_${if (media.isVideo) media.videoUrl else media.photoUrl}"
                                                },
                                            ) { index ->
                                                MediaThumbnail(
                                                    mediaDetail = mediaList[index],
                                                    onPhotoClick = onNavigateToPhotoPreview,
                                                    onVideoClick = onNavigateToVideoPreview,
                                                    onImageLoadError = viewModel::logImageLoadError
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 68.dp, end = 16.dp, top = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clickable(
                                                    interactionSource = null,
                                                    indication = null,
                                                    role = Role.Button,
                                                    onClickLabel = stringResource(R.string.favorite)
                                                ) {
                                                    hapticFeedback.performHapticFeedback(
                                                        HapticFeedbackType.LongPress
                                                    )
                                                    viewModel.favoriteNote(note)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AnimatedContent(
                                                targetState = note.favorite,
                                                transitionSpec = {
                                                    (scaleIn(
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessLow
                                                        ),
                                                        initialScale = 5f
                                                    ) + fadeIn(tween(ANIMATION_DURATION_SHORT_MS)))
                                                        .togetherWith(
                                                            scaleOut(
                                                                tween(ANIMATION_DURATION_SHORT_MS)
                                                            ) + fadeOut(
                                                                tween(ANIMATION_DURATION_SHORT_MS)
                                                            )
                                                        )
                                                        .using(SizeTransform(clip = false))
                                                },
                                                label = "favoriteIconAnimation"
                                            ) { isFavorite ->
                                                Icon(
                                                    imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                                                    contentDescription = null,
                                                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                                )
                                            }
                                        }

                                        Icon(
                                            imageVector = Icons.Rounded.EditNote,
                                            contentDescription = stringResource(R.string.cd_edit_note),
                                            modifier = Modifier
                                                .padding(start = 16.dp)
                                                .clickable {
                                                    onNavigateToEdit(note.noteId)
                                                }
                                        )

                                        Icon(
                                            imageVector = Icons.Rounded.IosShare,
                                            contentDescription = stringResource(R.string.share),
                                            modifier = Modifier
                                                .padding(start = 16.dp)
                                                .clickable {
                                                    viewModel.shareNote(note)
                                                }
                                        )
                                    }
                                }

                                if (showActionsSheet) {
                                    ModalBottomSheet(
                                        onDismissRequest = { showActionsSheet = false },
                                        sheetState = sheetState
                                    ) {
                                        note.text?.let { text ->
                                            BottomSheetActionItem(
                                                icon = Icons.Rounded.ContentCopy,
                                                label = stringResource(R.string.copy_text),
                                                onClick = {
                                                    closeSheetThen {
                                                        coroutineScope.launch {
                                                            clipboard.setClipEntry(
                                                                ClipEntry(
                                                                    ClipData.newPlainText(
                                                                        null,
                                                                        text
                                                                    )
                                                                )
                                                            )
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                        BottomSheetActionItem(
                                            icon = Icons.Rounded.Delete,
                                            label = stringResource(R.string.delete),
                                            onClick = {
                                                closeSheetThen { viewModel.setNoteToDelete(note) }
                                            },
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}