package com.jiahan.smartcamera.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * A centered, full-size message — used for both a screen's error state (server-provided text) and
 * its empty state (a static "nothing here yet" string), since the two render identically.
 *
 * Pads away [LocalBottomBarPadding] before centering (0.dp where the bottom bar isn't shown), so
 * the message centers within the area actually visible above the bar rather than the full screen
 * height, part of which the bar sits on top of.
 */
@Composable
fun FullScreenMessage(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = LocalBottomBarPadding.current),
        contentAlignment = Alignment.Center
    ) {
        Text(message)
    }
}