package com.jiahan.smartcamera.common

import android.content.ClipData
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jiahan.smartcamera.core.ui.R
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.ui.theme.SmartCameraTheme
import com.jiahan.smartcamera.util.AppConstants.ANIMATION_DURATION_SHORT_MS
import com.jiahan.smartcamera.util.toFormattedDateTime
import kotlinx.coroutines.launch

@Stable
private data class NoteItemCallbacks(
    val onNavigateToNotePreview: () -> Unit,
    val onEditNote: () -> Unit,
    val onFavoriteNote: () -> Unit,
    val onDeleteNote: () -> Unit,
    val onPhotoClick: (String) -> Unit,
    val onVideoClick: (String) -> Unit,
    val onProfilePictureClick: (String) -> Unit,
    val onShareNote: () -> Unit,
    val onImageLoadError: (Throwable) -> Unit
)

/**
 * A single note row: author, timestamp, text and media, with a long-press action sheet.
 *
 * Shared by the Home, Favorite and Search feeds, which is why it lives here rather than in any
 * one of those feature packages.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteItem(
    note: HomeNote,
    onNavigateToNotePreview: () -> Unit,
    onEditNote: () -> Unit,
    onFavoriteNote: () -> Unit,
    onDeleteNote: () -> Unit,
    onPhotoClick: (String) -> Unit,
    onVideoClick: (String) -> Unit,
    onProfilePictureClick: (String) -> Unit,
    onShareNote: () -> Unit,
    modifier: Modifier = Modifier,
    onImageLoadError: (Throwable) -> Unit = {}
) {
    val callbacks = remember(
        onNavigateToNotePreview,
        onEditNote,
        onFavoriteNote,
        onDeleteNote,
        onPhotoClick,
        onVideoClick,
        onProfilePictureClick,
        onShareNote,
        onImageLoadError
    ) {
        NoteItemCallbacks(
            onNavigateToNotePreview = onNavigateToNotePreview,
            onEditNote = onEditNote,
            onFavoriteNote = onFavoriteNote,
            onDeleteNote = onDeleteNote,
            onPhotoClick = onPhotoClick,
            onVideoClick = onVideoClick,
            onProfilePictureClick = onProfilePictureClick,
            onShareNote = onShareNote,
            onImageLoadError = onImageLoadError
        )
    }

    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary

    val formattedDate = remember(note.createdDate) {
        note.createdDate?.toEpochMilliseconds()?.toFormattedDateTime() ?: ""
    }

    var showActionsSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val coroutineScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val clipboard = LocalClipboard.current

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

    Column(
        modifier = modifier
            .combinedClickable(
                onClick = callbacks.onNavigateToNotePreview,
                onDoubleClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    callbacks.onFavoriteNote()
                },
                onLongClick = { openActionsSheet() }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp)
        ) {
            ProfileAvatar(
                profilePictureUrl = note.profilePictureUrl,
                onImageLoadError = callbacks.onImageLoadError,
                onClick = note.profilePictureUrl?.let { url ->
                    { callbacks.onProfilePictureClick(url) }
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
                            text = formattedDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = onSurfaceVariantColor,
                            modifier = Modifier.padding(start = 8.dp),
                            maxLines = 1
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        AnimatedVisibility(
                            visible = note.favorite,
                            enter = scaleIn(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                ),
                                initialScale = 5f
                            ) + fadeIn(tween(ANIMATION_DURATION_SHORT_MS)),
                            exit = scaleOut(tween(ANIMATION_DURATION_SHORT_MS)) +
                                    fadeOut(tween(ANIMATION_DURATION_SHORT_MS))
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Favorite,
                                contentDescription = stringResource(R.string.cd_marked_as_favorite),
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable(
                                        interactionSource = null,
                                        indication = null
                                    ) {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        callbacks.onFavoriteNote()
                                    },
                                tint = primaryColor
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Rounded.MoreHoriz,
                        contentDescription = stringResource(R.string.cd_more_options),
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(18.dp)
                            .clickable(
                                interactionSource = null,
                                indication = null
                            ) {
                                openActionsSheet()
                            },
                        tint = onSurfaceVariantColor
                    )
                }

                note.text?.let { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 15,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        note.mediaList?.takeIf { it.isNotEmpty() }?.let { mediaList ->
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                contentPadding = PaddingValues(start = 56.dp, end = 8.dp)
            ) {
                items(
                    count = mediaList.size,
                    key = { index ->
                        val media = mediaList[index]
                        "${note.noteId}_${index}_${if (media.isVideo) media.videoUrl else media.photoUrl}"
                    }
                ) { index ->
                    MediaThumbnail(
                        mediaDetail = mediaList[index],
                        onPhotoClick = callbacks.onPhotoClick,
                        onVideoClick = callbacks.onVideoClick,
                        onImageLoadError = callbacks.onImageLoadError
                    )
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = 16.dp, start = 8.dp, end = 8.dp),
            thickness = 0.5.dp
        )
    }

    if (showActionsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showActionsSheet = false },
            sheetState = sheetState
        ) {
            BottomSheetActionItem(
                icon = Icons.Rounded.FavoriteBorder,
                label = if (note.favorite)
                    stringResource(R.string.remove_like)
                else
                    stringResource(R.string.like),
                onClick = {
                    closeSheetThen {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        callbacks.onFavoriteNote()
                    }
                }
            )
            BottomSheetActionItem(
                icon = Icons.Rounded.EditNote,
                label = stringResource(R.string.edit_note),
                onClick = { closeSheetThen(callbacks.onEditNote) }
            )
            BottomSheetActionItem(
                icon = Icons.Rounded.Share,
                label = stringResource(R.string.share),
                onClick = { closeSheetThen(callbacks.onShareNote) }
            )
            note.text?.let { text ->
                BottomSheetActionItem(
                    icon = Icons.Rounded.ContentCopy,
                    label = stringResource(R.string.copy_text),
                    onClick = {
                        closeSheetThen {
                            coroutineScope.launch {
                                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, text)))
                            }
                        }
                    }
                )
            }
            BottomSheetActionItem(
                icon = Icons.Rounded.Delete,
                label = stringResource(R.string.delete),
                onClick = { closeSheetThen(callbacks.onDeleteNote) },
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * Placeholder shown in place of a [NoteItem] while the first page loads. Mirrors the real item's
 * metrics (38dp avatar, 56dp text inset, 220x256 media block) so the list doesn't visibly reflow
 * when the notes arrive.
 */
@Composable
fun NoteItemSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .shimmer(CircleShape)
            )
            Column(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.45f)
                        .height(14.dp)
                        .shimmer()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(12.dp)
                        .shimmer()
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(12.dp)
                        .shimmer()
                )
            }
        }

        Box(
            modifier = Modifier
                .padding(start = 56.dp, top = 8.dp)
                .height(256.dp)
                .width(220.dp)
                .shimmer(MaterialTheme.shapes.medium)
        )

        HorizontalDivider(
            modifier = Modifier.padding(top = 16.dp, start = 8.dp, end = 8.dp),
            thickness = 0.5.dp
        )
    }
}

/** A short run of [NoteItemSkeleton]s filling the list area while the first page loads. */
@Composable
fun NoteListSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        repeat(SKELETON_ITEM_COUNT) { NoteItemSkeleton() }
    }
}

private const val SKELETON_ITEM_COUNT = 3

@Preview(showBackground = true, name = "NoteItem – text only")
@Composable
private fun NoteItemPreview() {
    SmartCameraTheme {
        NoteItem(
            note = HomeNote(
                noteId = "note1",
                username = "john_doe",
                text = "Hello, this is a preview note with some sample text that wraps across multiple lines.",
                mediaList = null,
                profilePictureUrl = null,
                favorite = false,
                createdDate = null
            ),
            onNavigateToNotePreview = {},
            onEditNote = {},
            onFavoriteNote = {},
            onDeleteNote = {},
            onPhotoClick = {},
            onVideoClick = {},
            onProfilePictureClick = {},
            onShareNote = {}
        )
    }
}

@Preview(showBackground = true, name = "NoteItem – favorited")
@Composable
private fun NoteItemFavoritedPreview() {
    SmartCameraTheme {
        NoteItem(
            note = HomeNote(
                noteId = "note2",
                username = "jane_doe",
                text = "This note is marked as a favourite.",
                mediaList = null,
                profilePictureUrl = null,
                favorite = true,
                createdDate = null
            ),
            onNavigateToNotePreview = {},
            onEditNote = {},
            onFavoriteNote = {},
            onDeleteNote = {},
            onPhotoClick = {},
            onVideoClick = {},
            onProfilePictureClick = {},
            onShareNote = {}
        )
    }
}