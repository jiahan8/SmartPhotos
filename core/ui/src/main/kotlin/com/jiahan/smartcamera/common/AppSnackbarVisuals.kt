package com.jiahan.smartcamera.common

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals

data class AppSnackbarVisuals(
    override val message: String,
    val isError: Boolean = false,
    override val actionLabel: String? = null,
    override val withDismissAction: Boolean = false,
    override val duration: SnackbarDuration,
) : SnackbarVisuals

suspend fun SnackbarHostState.showAppSnackbar(
    message: String,
    isError: Boolean = false,
    actionLabel: String? = null,
    withDismissAction: Boolean = false,
    duration: SnackbarDuration =
        if (actionLabel == null) SnackbarDuration.Short else SnackbarDuration.Indefinite,
): SnackbarResult = showSnackbar(
    AppSnackbarVisuals(
        message = message,
        isError = isError,
        actionLabel = actionLabel,
        withDismissAction = withDismissAction,
        duration = duration,
    )
)