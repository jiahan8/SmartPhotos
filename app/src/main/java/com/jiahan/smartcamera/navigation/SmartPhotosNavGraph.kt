package com.jiahan.smartcamera.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import com.jiahan.smartcamera.auth.AuthScreen
import com.jiahan.smartcamera.explore.ExploreScreen
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
    onUpdateStartDestination: (Screen) -> Unit,
) {
    composable<Screen.Home> {
        HomeScreen(
            onNavigateToNotePreview = { noteId ->
                navController.navigate(Screen.NotePreview(noteId))
            },
            onNavigateToPhotoPreview = { url ->
                navController.navigate(Screen.PhotoPreview(MediaSourceType.REMOTE, url))
            },
            onNavigateToVideoPreview = { url ->
                navController.navigate(Screen.VideoPreview(MediaSourceType.REMOTE, url))
            },
            onNavigateToExplore = {
                navController.navigate(Screen.Explore)
            },
            onScrollDirectionChanged = onScrollDirectionChanged,
            scrollToTop = scrollToTop,
            onScrollToTopConsumed = onScrollToTopConsumed,
        )
    }

    composable<Screen.Search>(
        deepLinks = listOf(
            navDeepLink { uriPattern = SEARCH_DEEP_LINK_URI_PATTERN }
        )
    ) {
        SearchScreen(
            onNavigateToNotePreview = { noteId ->
                navController.navigate(Screen.NotePreview(noteId))
            },
            onNavigateToPhotoPreview = { url ->
                navController.navigate(Screen.PhotoPreview(MediaSourceType.REMOTE, url))
            },
            onNavigateToVideoPreview = { url ->
                navController.navigate(Screen.VideoPreview(MediaSourceType.REMOTE, url))
            },
            onScrollDirectionChanged = onScrollDirectionChanged,
            scrollToTop = scrollToTop,
            onScrollToTopConsumed = onScrollToTopConsumed,
        )
    }

    composable<Screen.Note> {
        NoteScreen(
            onBack = { navController.popBackStack() },
            onNavigateToPhotoPreview = { uri ->
                navController.navigate(Screen.PhotoPreview(MediaSourceType.LOCAL, uri))
            },
            onNavigateToVideoPreview = { uri ->
                navController.navigate(Screen.VideoPreview(MediaSourceType.LOCAL, uri))
            }
        )
    }

    composable<Screen.Favorite> {
        FavoriteScreen(
            onNavigateToNotePreview = { noteId ->
                navController.navigate(Screen.NotePreview(noteId))
            },
            onNavigateToPhotoPreview = { url ->
                navController.navigate(Screen.PhotoPreview(MediaSourceType.REMOTE, url))
            },
            onNavigateToVideoPreview = { url ->
                navController.navigate(Screen.VideoPreview(MediaSourceType.REMOTE, url))
            },
            onScrollDirectionChanged = onScrollDirectionChanged,
            scrollToTop = scrollToTop,
            onScrollToTopConsumed = onScrollToTopConsumed,
        )
    }

    composable<Screen.Explore> {
        ExploreScreen(
            onBack = { navController.popBackStack() },
            onNavigateToPhotoPreview = { url ->
                navController.navigate(Screen.PhotoPreview(MediaSourceType.REMOTE, url))
            }
        )
    }

    composable<Screen.PhotoPreview> {
        PhotoPreviewScreen(onBack = { navController.popBackStack() })
    }

    composable<Screen.VideoPreview> {
        VideoPreviewScreen(onBack = { navController.popBackStack() })
    }

    composable<Screen.NotePreview> {
        NotePreviewScreen(
            onBack = { navController.popBackStack() },
            onNavigateToPhotoPreview = { url ->
                navController.navigate(Screen.PhotoPreview(MediaSourceType.REMOTE, url))
            },
            onNavigateToVideoPreview = { url ->
                navController.navigate(Screen.VideoPreview(MediaSourceType.REMOTE, url))
            }
        )
    }

    composable<Screen.Auth> {
        AuthScreen(
            onNavigateToHome = {
                onUpdateStartDestination(Screen.Home)
                navController.navigate(Screen.Home) {
                    popUpTo(0) { inclusive = true }
                }
            }
        )
    }

    composable<Screen.Profile> {
        ProfileScreen(
            onNavigateToSettings = {
                navController.navigate(Screen.Settings)
            },
            onNavigateToPhotoPreview = { url ->
                navController.navigate(Screen.PhotoPreview(MediaSourceType.REMOTE, url))
            }
        )
    }

    composable<Screen.Settings> {
        SettingsScreen(
            onBack = { navController.popBackStack() },
            onNavigateToAuth = {
                navController.navigate(Screen.Auth) {
                    popUpTo(0) { inclusive = true }
                }
            }
        )
    }
}