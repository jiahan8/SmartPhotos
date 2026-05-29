package com.jiahan.smartcamera.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.jiahan.smartcamera.auth.AuthScreen
import com.jiahan.smartcamera.favorite.FavoriteScreen
import com.jiahan.smartcamera.home.HomeScreen
import com.jiahan.smartcamera.navigation.Screen.Search.SEARCH_DEEP_LINK_URI_PATTERN
import com.jiahan.smartcamera.note.NoteScreen
import com.jiahan.smartcamera.preview.NotePreviewScreen
import com.jiahan.smartcamera.preview.PhotoPreviewScreen
import com.jiahan.smartcamera.preview.VideoPreviewScreen
import com.jiahan.smartcamera.profile.ProfileScreen
import com.jiahan.smartcamera.search.SearchScreen
import com.jiahan.smartcamera.settings.SettingsScreen

fun NavGraphBuilder.smartPhotosNavGraph(
    navController: NavController,
    scrollToTop: Long?,
    onScrollDirectionChanged: (Boolean) -> Unit,
    onScrollToTopConsumed: () -> Unit,
    onUpdateStartDestination: (String) -> Unit,
) {
    composable(Screen.Home.route) {
        HomeScreen(
            onNavigateToNotePreview = { documentPath ->
                navController.navigate(Screen.NotePreview.createRoute(documentPath))
            },
            onNavigateToPhotoPreview = { url ->
                navController.navigate(Screen.PhotoPreview.createRemoteRoute(url))
            },
            onNavigateToVideoPreview = { url ->
                navController.navigate(Screen.VideoPreview.createRemoteRoute(url))
            },
            onScrollDirectionChanged = onScrollDirectionChanged,
            scrollToTop = scrollToTop,
            onScrollToTopConsumed = onScrollToTopConsumed,
        )
    }

    composable(
        route = Screen.Search.route,
        deepLinks = listOf(
            navDeepLink { uriPattern = SEARCH_DEEP_LINK_URI_PATTERN }
        )
    ) {
        SearchScreen(
            onNavigateToNotePreview = { documentPath ->
                navController.navigate(Screen.NotePreview.createRoute(documentPath))
            },
            onNavigateToPhotoPreview = { url ->
                navController.navigate(Screen.PhotoPreview.createRemoteRoute(url))
            },
            onNavigateToVideoPreview = { url ->
                navController.navigate(Screen.VideoPreview.createRemoteRoute(url))
            },
            onScrollDirectionChanged = onScrollDirectionChanged,
            scrollToTop = scrollToTop,
            onScrollToTopConsumed = onScrollToTopConsumed,
        )
    }

    composable(route = Screen.Note.route) {
        NoteScreen(
            onBack = { navController.popBackStack() },
            onNavigateToPhotoPreview = { uri ->
                navController.navigate(Screen.PhotoPreview.createLocalRoute(uri))
            },
            onNavigateToVideoPreview = { uri ->
                navController.navigate(Screen.VideoPreview.createLocalRoute(uri))
            }
        )
    }

    composable(route = Screen.Favorite.route) {
        FavoriteScreen(
            onNavigateToNotePreview = { documentPath ->
                navController.navigate(Screen.NotePreview.createRoute(documentPath))
            },
            onNavigateToPhotoPreview = { url ->
                navController.navigate(Screen.PhotoPreview.createRemoteRoute(url))
            },
            onNavigateToVideoPreview = { url ->
                navController.navigate(Screen.VideoPreview.createRemoteRoute(url))
            },
            onScrollDirectionChanged = onScrollDirectionChanged,
            scrollToTop = scrollToTop,
            onScrollToTopConsumed = onScrollToTopConsumed,
        )
    }

    composable(
        route = Screen.PhotoPreview.route,
        arguments = listOf(
            navArgument(Screen.PhotoPreview.TYPE_ARG) { type = NavType.StringType },
            navArgument(Screen.PhotoPreview.SOURCE_ARG) { type = NavType.StringType }
        )
    ) {
        PhotoPreviewScreen(onBack = { navController.popBackStack() })
    }

    composable(
        route = Screen.VideoPreview.route,
        arguments = listOf(
            navArgument(Screen.VideoPreview.TYPE_ARG) { type = NavType.StringType },
            navArgument(Screen.VideoPreview.SOURCE_ARG) { type = NavType.StringType }
        )
    ) {
        VideoPreviewScreen(onBack = { navController.popBackStack() })
    }

    composable(
        route = Screen.NotePreview.route,
        arguments = listOf(
            navArgument(Screen.NotePreview.ID_ARG) { type = NavType.StringType }
        )
    ) {
        NotePreviewScreen(
            onBack = { navController.popBackStack() },
            onNavigateToPhotoPreview = { url ->
                navController.navigate(Screen.PhotoPreview.createRemoteRoute(url))
            },
            onNavigateToVideoPreview = { url ->
                navController.navigate(Screen.VideoPreview.createRemoteRoute(url))
            }
        )
    }

    composable(route = Screen.Auth.route) {
        AuthScreen(
            onNavigateToHome = {
                onUpdateStartDestination(Screen.Home.route)
                navController.navigate(Screen.Home.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
        )
    }

    composable(route = Screen.Profile.route) {
        ProfileScreen(
            onNavigateToSettings = {
                navController.navigate(Screen.Settings.route)
            },
            onNavigateToPhotoPreview = { url ->
                navController.navigate(Screen.PhotoPreview.createRemoteRoute(url))
            }
        )
    }

    composable(route = Screen.Settings.route) {
        SettingsScreen(
            onBack = { navController.popBackStack() },
            onNavigateToAuth = {
                navController.navigate(Screen.Auth.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
        )
    }
}