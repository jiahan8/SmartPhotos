package com.jiahan.smartcamera.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.jiahan.smartcamera.util.AppConstants.SHIMMER_DURATION_MS

/**
 * Paints an animated shimmer sweep behind this composable, for skeleton placeholders shown while
 * a screen's first page loads.
 *
 * The animation comes from [rememberInfiniteTransition] rather than a hand-rolled frame loop on
 * purpose: it suspends on `withInfiniteAnimationFrameNanos`, which Compose's test infrastructure
 * knows how to stop. A `while (true) { withFrameNanos { … } }` would look identical on a device
 * and then hang `waitForIdle` in every UI test that happens to catch a loading state.
 *
 * The sweep colors are drawn from `onSurface` at low alpha rather than from fixed grays, so the
 * placeholder reads correctly in both light and dark themes.
 */
@Composable
fun Modifier.shimmer(shape: Shape = RoundedCornerShape(4.dp)): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SHIMMER_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )

    val onSurface = MaterialTheme.colorScheme.onSurface
    val base = onSurface.copy(alpha = 0.10f)
    val highlight = onSurface.copy(alpha = 0.20f)

    return this
        .clip(shape)
        // `progress` is read inside the draw lambda, so a new frame invalidates drawing only —
        // it never triggers recomposition or relayout of the skeleton.
        .drawBehind {
            val sweep = size.width * 2f
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(base, highlight, base),
                    start = Offset(x = progress * sweep - size.width, y = 0f),
                    end = Offset(x = progress * sweep, y = 0f)
                )
            )
        }
}