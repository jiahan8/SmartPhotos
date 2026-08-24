package com.jiahan.smartcamera.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.jiahan.smartcamera.R

/**
 * Confirms deleting a note — identical across every screen with a delete action (Home, Favorite,
 * Search, NotePreview). [onConfirmDelete] carries whatever screen-specific cleanup follows to
 * delete (e.g. NotePreview also navigates back).
 */
@Composable
fun DeleteNoteConfirmationDialog(
    onDismissRequest: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.delete_note)) },
        text = { Text(stringResource(R.string.delete_note_desc)) },
        confirmButton = {
            TextButton(onClick = onConfirmDelete) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}