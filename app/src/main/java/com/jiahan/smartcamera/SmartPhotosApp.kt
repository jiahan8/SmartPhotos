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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jiahan.smartcamera.navigation.Screen
import com.jiahan.smartcamera.navigation.smartPhotosNavGraph
import com.jiahan.smartcamera.ui.theme.SmartCameraTheme
import com.jiahan.smartcamera.util.AppConstants.ANIMATION_DURATION_SHORT_MS

@Composable
fun SmartPhotosApp(
    isDarkTheme: Boolean,
    isAppReady: Boolean,
    startDestination: String,
    showBottomBar: Boolean,
    scrollToTop: Long?,
    onScrollDirectionChanged: (Boolean) -> Unit,
    onScrollToTopConsumed: () -> Unit,
    onTriggerScrollToTop: () -> Unit,
    onUpdateStartDestination: (String) -> Unit,
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
        val bottomNavItems = remember {
            listOf(Screen.Home, Screen.Search, Screen.Note, Screen.Favorite, Screen.Profile)
        }
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination
        val currentRoute = currentDestination?.route
        val isBottomBarVisible = remember(currentRoute, showBottomBar) {
            (currentRoute in bottomNavItems.map { it.route }) && showBottomBar
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                bottomBar = {
                    AnimatedVisibility(
                        visible = isBottomBarVisible,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                        exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
                    ) {
                        NavigationBar {
                            bottomNavItems.forEach { screen ->
                                val selected = currentDestination?.route == screen.route
                                NavigationBarItem(
                                    icon = {
                                        screen.icon?.let { icon ->
                                            AnimatedNavIcon(selected = selected, imageVector = icon)
                                        }
                                    },
                                    label = { Text(stringResource(screen.titleResId)) },
                                    selected = selected,
                                    onClick = {
                                        if (currentDestination?.route == screen.route) {
                                            when (screen.route) {
                                                Screen.Home.route,
                                                Screen.Search.route,
                                                Screen.Favorite.route -> onTriggerScrollToTop()
                                            }
                                        } else {
                                            navController.navigate(screen.route) {
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
                        modifier = Modifier.padding()
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