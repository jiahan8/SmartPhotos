package com.jiahan.smartcamera.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import com.jiahan.smartcamera.auth.AuthRoute
import com.jiahan.smartcamera.util.AppConstants.ANIMATION_DURATION_SHORT_MS

/**
 * Shared enter/exit transitions applied once at the `NavHost` level (see
 * [com.jiahan.smartcamera.SmartPhotosApp]) rather than per `composable<>` destination.
 *
 * Navigation Compose animates a destination change with the *incoming* destination's
 * `enterTransition` and the *outgoing* destination's `exitTransition`. Declaring specs
 * per destination therefore lets the two halves disagree (e.g. Home fading out while
 * NotePreview slides in). Hoisting them here means both halves are chosen by the same
 * rule, from the same [AnimatedContentTransitionScope], so a transition can't be
 * half-fade/half-slide.
 *
 * The rule, following the Material motion patterns for navigation:
 * - Switching between top-level destinations (the bottom-bar tabs, plus [AuthRoute] as the
 *   signed-out root) is lateral movement with no hierarchy, so it *fades through*.
 * - Anything else pushes deeper into the hierarchy (a note, a preview, settings), so it slides
 *   along the X axis — toward the start on push, back toward the end on pop.
 */
private val topLevelRoutes =
    TopLevelDestination.entries.map { it.route::class } + AuthRoute::class

private fun NavBackStackEntry.isTopLevel(): Boolean =
    topLevelRoutes.any { destination.hasRoute(it) }

/** True when both sides of the transition are top-level destinations. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isLateralMove(): Boolean =
    initialState.isTopLevel() && targetState.isTopLevel()

private val fade = tween<Float>(ANIMATION_DURATION_SHORT_MS)
private val slide = tween<IntOffset>(ANIMATION_DURATION_SHORT_MS)

val navEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    if (isLateralMove()) {
        fadeIn(fade)
    } else {
        slideIntoContainer(SlideDirection.Start, slide) + fadeIn(fade)
    }
}

val navExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    if (isLateralMove()) {
        fadeOut(fade)
    } else {
        slideOutOfContainer(SlideDirection.Start, slide) + fadeOut(fade)
    }
}

val navPopEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
    {
        if (isLateralMove()) {
            fadeIn(fade)
        } else {
            slideIntoContainer(SlideDirection.End, slide) + fadeIn(fade)
        }
    }

val navPopExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    if (isLateralMove()) {
        fadeOut(fade)
    } else {
        slideOutOfContainer(SlideDirection.End, slide) + fadeOut(fade)
    }
}