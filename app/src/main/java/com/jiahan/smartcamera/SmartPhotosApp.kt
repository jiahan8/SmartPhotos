package com.jiahan.smartcamera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jiahan.smartcamera.common.CustomSnackbarHost
import com.jiahan.smartcamera.navigation.Screen
import com.jiahan.smartcamera.navigation.bottomNavItems
import com.jiahan.smartcamera.navigation.smartPhotosNavGraph
import com.jiahan.smartcamera.ui.theme.SmartCameraTheme
import com.jiahan.smartcamera.util.AppConstants.ANIMATION_DURATION_SHORT_MS

@Composable
fun SmartPhotosApp(
    isDarkTheme: Boolean,
    isAppReady: Boolean,
    startDestination: Screen,
    showBottomBar: Boolean,
    scrollToTop: Long?,
    hasPendingShare: Boolean,
    pendingNoteId: String?,
    isUpdateReadyToInstall: Boolean,
    onScrollDirectionChanged: (Boolean) -> Unit,
    onScrollToTopConsumed: () -> Unit,
    onTriggerScrollToTop: () -> Unit,
    onUpdateStartDestination: (Screen) -> Unit,
    onPendingNoteIdConsumed: () -> Unit,
    onCompleteUpdate: () -> Unit,
) {
    SmartCameraTheme(darkTheme = isDarkTheme) {
        val view = LocalView.current
        val activity = LocalActivity.current
        if (!view.isInEditMode) {
            SideEffect {
                activity?.window?.let { window ->
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !isDarkTheme
                        isAppearanceLightNavigationBars = !isDarkTheme
                    }
                }
            }
        }

        val navController = rememberNavController()

        LaunchedEffect(isAppReady, pendingNoteId) {
            if (isAppReady && pendingNoteId != null) {
                navController.navigate(Screen.NotePreview(pendingNoteId))
                onPendingNoteIdConsumed()
            }
        }

        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination
        val isBottomBarVisible = remember(currentDestination, showBottomBar) {
            bottomNavItems.any { item ->
                currentDestination?.hasRoute(item.route::class) == true
            } && showBottomBar
        }

        LaunchedEffect(hasPendingShare, currentDestination) {
            val isOnAuthScreen = currentDestination?.hasRoute(Screen.Auth::class) == true
            if (hasPendingShare && currentDestination != null && !isOnAuthScreen) {
                navController.navigate(Screen.Note) { launchSingleTop = true }
            }
        }

        val updateSnackbarHostState = remember { SnackbarHostState() }
        val updateReadyMessage = stringResource(R.string.update_ready_message)
        val updateReadyAction = stringResource(R.string.update_ready_action)

        LaunchedEffect(isUpdateReadyToInstall) {
            if (isUpdateReadyToInstall) {
                val result = updateSnackbarHostState.showSnackbar(
                    message = updateReadyMessage,
                    actionLabel = updateReadyAction,
                    duration = SnackbarDuration.Indefinite
                )
                if (result == SnackbarResult.ActionPerformed) {
                    onCompleteUpdate()
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                snackbarHost = { CustomSnackbarHost(updateSnackbarHostState) },
                bottomBar = {
                    AnimatedVisibility(
                        visible = isBottomBarVisible,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                        exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
                    ) {
                        NavigationBar {
                            bottomNavItems.forEach { item ->
                                val selected =
                                    currentDestination?.hasRoute(item.route::class) == true
                                NavigationBarItem(
                                    icon = {
                                        AnimatedNavIcon(
                                            selected = selected,
                                            imageVector = item.icon
                                        )
                                    },
                                    label = { Text(stringResource(item.titleResId)) },
                                    selected = selected,
                                    onClick = {
                                        if (selected) {
                                            when (item.route) {
                                                Screen.Home,
                                                Screen.Search,
                                                Screen.Favorite -> onTriggerScrollToTop()

                                                else -> Unit
                                            }
                                        } else {
                                            navController.navigate(item.route) {
                                                popUpTo(navController.graph.startDestinationId) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            ) { padding ->
                if (isAppReady) {
                    NavHost(
                        navController = navController,
                        startDestination = startDestination,
                        modifier = Modifier.padding(bottom = padding.calculateBottomPadding())
                    ) {
                        smartPhotosNavGraph(
                            navController = navController,
                            scrollToTop = scrollToTop,
                            onScrollDirectionChanged = onScrollDirectionChanged,
                            onScrollToTopConsumed = onScrollToTopConsumed,
                            onUpdateStartDestination = onUpdateStartDestination,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedNavIcon(
    selected: Boolean,
    imageVector: ImageVector
) {
    val transition = updateTransition(targetState = selected, label = "IconTransition")
    val scale by transition.animateFloat(
        label = "IconScale",
        transitionSpec = {
            if (targetState) {
                spring(dampingRatio = 0.2f, stiffness = 100f)
            } else {
                tween(durationMillis = ANIMATION_DURATION_SHORT_MS)
            }
        }
    ) { isSelected -> if (isSelected) 1.2f else 1f }

    val color = if (selected)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Icon(
        imageVector = imageVector,
        contentDescription = null,
        modifier = Modifier.scale(scale),
        tint = color
    )
}