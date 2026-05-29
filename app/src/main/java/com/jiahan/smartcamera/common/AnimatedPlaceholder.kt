package com.jiahan.smartcamera.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jiahan.smartcamera.util.AppConstants.TEXT_FIELD_PLACEHOLDER_ROTATION_DELAY_MS
import com.jiahan.smartcamera.util.AppConstants.TEXT_FIELD_TRANSITION_DELAY_MS
import com.jiahan.smartcamera.util.AppConstants.TEXT_FIELD_TRANSITION_FADE_DURATION_MS
import kotlinx.coroutines.delay

/**
 * Remembers a cycling animated placeholder. Returns a [Pair] of the current placeholder
 * [String] and its fade [Float] alpha. Pure UI state — never owned by a ViewModel.
 *
 * @param options The list of strings to cycle through. Expected to be stable (e.g. resolved
 * via [androidx.compose.ui.res.stringResource] and passed in once).
 */
@Composable
fun rememberCyclingPlaceholder(options: List<String>): Pair<String, Float> {
    var currentIndex by remember { mutableIntStateOf(0) }
    var isTransitioning by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (isTransitioning) 0f else 1f,
        animationSpec = tween(durationMillis = TEXT_FIELD_TRANSITION_FADE_DURATION_MS),
        label = "placeholderAlpha"
    )

    // Use Unit key — options are resolved from resources and won't change within a session.
    LaunchedEffect(Unit) {
        while (true) {
            delay(TEXT_FIELD_PLACEHOLDER_ROTATION_DELAY_MS)
            isTransitioning = true
            delay(TEXT_FIELD_TRANSITION_DELAY_MS)
            currentIndex = (currentIndex + 1) % options.size
            isTransitioning = false
            delay(TEXT_FIELD_TRANSITION_DELAY_MS)
        }
    }

    return options[currentIndex] to alpha
}