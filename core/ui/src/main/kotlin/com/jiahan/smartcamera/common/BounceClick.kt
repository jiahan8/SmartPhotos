package com.jiahan.smartcamera.common

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Scales down to [scaleDown] while pressed, springs back on release, and fires a light
 * [hapticFeedbackType] tick on click. For a surface that doesn't otherwise own a click (a card,
 * thumbnail, or icon). For a component that already owns its click handling (e.g. Material3
 * [androidx.compose.material3.Button]), use [bounceScale] with that component's
 * `interactionSource` parameter instead.
 */
fun Modifier.bounceClick(
    enabled: Boolean = true,
    scaleDown: Float = 0.94f,
    hapticFeedbackType: HapticFeedbackType? = HapticFeedbackType.ContextClick,
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val hapticFeedback = LocalHapticFeedback.current
    this
        .bounceScale(interactionSource, scaleDown)
        .clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            enabled = enabled,
        ) {
            hapticFeedbackType?.let { hapticFeedback.performHapticFeedback(it) }
            onClick()
        }
}

/**
 * Scales down to [scaleDown] while [interactionSource] reports a press. For layering the same
 * bounce animation onto a component that already owns its click handling and exposes its
 * `interactionSource` (e.g. Material3 [androidx.compose.material3.Button]); use [bounceClick]
 * instead when the caller needs the click handling too.
 */
fun Modifier.bounceScale(
    interactionSource: InteractionSource,
    scaleDown: Float = 0.96f,
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bounceScale"
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}