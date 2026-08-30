package com.jiahan.smartcamera.common

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * Height reserved for the app's bottom navigation bar on the current screen, as measured by the
 * root Scaffold in SmartPhotosApp -- 0.dp on screens where it isn't shown. Composables that fill
 * the screen (e.g. [FullScreenMessage]) read this to center within the area actually visible
 * above the bar, rather than the full screen height, part of which the bar sits on top of.
 */
val LocalBottomBarPadding = compositionLocalOf { 0.dp }