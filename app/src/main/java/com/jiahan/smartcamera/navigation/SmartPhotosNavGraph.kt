package com.jiahan.smartcamera.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import com.jiahan.smartcamera.BuildConfig
import com.jiahan.smartcamera.auth.AuthRoute
import com.jiahan.smartcamera.auth.AuthScreen
import com.jiahan.smartcamera.explore.ExploreRoute
import com.jiahan.smartcamera.explore.ExploreScreen
import com.jiahan.smartcamera.favorite.FavoriteRoute
import com.jiahan.smartcamera.favorite.FavoriteScreen
import com.jiahan.smartcamera.home.HomeRoute
import com.jiahan.smartcamera.home.HomeScreen
import com.jiahan.smartcamera.note.EditNoteRoute
import com.jiahan.smartcamera.note.EditNoteScreen
import com.jiahan.smartcamera.note.NoteRoute
import com.jiahan.smartcamera.note.NoteScreen
import com.jiahan.smartcamera.preview.MediaSourceType
import com.jiahan.smartcamera.preview.NotePreviewRoute
import com.jiahan.smartcamera.preview.NotePreviewScreen
import com.jiahan.smartcamera.preview.PhotoPreviewRoute
import com.jiahan.smartcamera.preview.PhotoPreviewScreen
import com.jiahan.smartcamera.preview.VideoPreviewRoute
import com.jiahan.smartcamera.preview.VideoPreviewScreen
import com.jiahan.smartcamera.profile.ProfileRoute
import com.jiahan.smartcamera.profile.ProfileScreen
import com.jiahan.smartcamera.search.SearchRoute
import com.jiahan.smartcamera.search.SearchRoute.SEARCH_DEEP_LINK_URI_PATTERN
import com.jiahan.smartcamera.search.SearchScreen
import com.jiahan.smartcamera.settings.SettingsRoute
import com.jiahan.smartcamera.settings.SettingsScreen

/**
 * The whole navigation graph, in one place, wiring each `@Serializable` route to the screen that
 * owns it (see https://developer.android.com/guide/navigation/design/type-safety).
 *
 * **Routes live in the feature package, not here.** Each destination's route type sits beside its
 * screen -- `home/HomeRoute.kt`, `preview/PreviewRoutes.kt` and so on -- so a feature package can
 * become its own Gradle module without the route having to travel separately, and so a feature's
 * ViewModel can read its own arguments back with `toRoute<...>()` without importing upward. What
 * stays in `:app` is this file, the bottom bar and the deep links: the wiring is the one thing
 * that legitimately needs to see every route at once.
 *
 * That is also why the routes share no supertype. They used to be nested in a `sealed interface
 * Screen`, which a feature module could not implement -- the dependency would run the wrong way --
 * so `startDestination` is typed `Any`, as Navigation Compose itself types it.
 *
 * Screens take navigation *lambdas* rather than a [NavController], which is what keeps them
 * previewable, testable without a graph, and unaware of each other's routes: every
 * `navController.navigate(...)` in the app is in this file or in
 * [com.jiahan.smartcamera.SmartPhotosApp].
 */
fun NavGraphBuilder.smartPhotosNavGraph(
    navController: NavController,
    scrollToTop: Long?,
    onScrollDirectionChanged: (Boolean) -> Unit,
    onScrollToTopConsumed: () -> Unit,
    onUpdateStartDestination: (Any) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    composable<HomeRoute> {
        HomeScreen(
            onNavigateToNotePreview = { noteId ->
                navController.navigate(NotePreviewRoute(noteId))
            },
            onNavigateToEditNote = { noteId ->
                navController.navigate(EditNoteRoute(noteId))
            },
            onNavigateToPhotoPreview = { url ->
                navController.navigate(PhotoPreviewRoute(MediaSourceType.REMOTE, url))
            },
            onNavigateToVideoPreview = { url ->
                navController.navigate(VideoPreviewRoute(MediaSourceType.REMOTE, url))
            },
            onNavigateToExplore = {
                navController.navigate(ExploreRoute)
            },
            onScrollDirectionChanged = onScrollDirectionChanged,
            scrollToTop = scrollToTop,
            onScrollToTopConsumed = onScrollToTopConsumed,
            snackbarHostState = snackbarHostState,
        )
    }

    composable<SearchRoute>(
        deepLinks = listOf(
            navDeepLink { uriPattern = SEARCH_DEEP_LINK_URI_PATTERN }
        )
    ) {
        SearchScreen(
            onNavigateToNotePreview = { noteId ->
                navController.navigate(NotePreviewRoute(noteId))
            },
            onNavigateToEditNote = { noteId ->
                navController.navigate(EditNoteRoute(noteId))
            },
            onNavigateToPhotoPreview = { url ->
                navController.navigate(PhotoPreviewRoute(MediaSourceType.REMOTE, url))
            },
            onNavigateToVideoPreview = { url ->
                navController.navigate(VideoPreviewRoute(MediaSourceType.REMOTE, url))
            },
            onScrollDirectionChanged = onScrollDirectionChanged,
            scrollToTop = scrollToTop,
            onScrollToTopConsumed = onScrollToTopConsumed,
            snackbarHostState = snackbarHostState,
        )
    }

    composable<NoteRoute> {
        NoteScreen(
            onBack = { navController.popBackStack() },
            onNavigateToPhotoPreview = { uri ->
                navController.navigate(PhotoPreviewRoute(MediaSourceType.LOCAL, uri))
            },
            onNavigateToVideoPreview = { uri ->
                navController.navigate(VideoPreviewRoute(MediaSourceType.LOCAL, uri))
            },
            snackbarHostState = snackbarHostState,
        )
    }

    composable<EditNoteRoute> {
        EditNoteScreen(
            onBack = { navController.popBackStack() },
            onNavigateToPhotoPreview = { url ->
                navController.navigate(PhotoPreviewRoute(MediaSourceType.REMOTE, url))
            },
            onNavigateToVideoPreview = { url ->
                navController.navigate(VideoPreviewRoute(MediaSourceType.REMOTE, url))
            },
            snackbarHostState = snackbarHostState,
        )
    }

    composable<FavoriteRoute> {
        FavoriteScreen(
            onNavigateToNotePreview = { noteId ->
                navController.navigate(NotePreviewRoute(noteId))
            },
            onNavigateToEditNote = { noteId ->
                navController.navigate(EditNoteRoute(noteId))
            },
            onNavigateToPhotoPreview = { url ->
                navController.navigate(PhotoPreviewRoute(MediaSourceType.REMOTE, url))
            },
            onNavigateToVideoPreview = { url ->
                navController.navigate(VideoPreviewRoute(MediaSourceType.REMOTE, url))
            },
            onScrollDirectionChanged = onScrollDirectionChanged,
            scrollToTop = scrollToTop,
            onScrollToTopConsumed = onScrollToTopConsumed,
            snackbarHostState = snackbarHostState,
        )
    }

    composable<ExploreRoute> {
        ExploreScreen(
            onBack = { navController.popBackStack() },
            onNavigateToPhotoPreview = { url ->
                navController.navigate(PhotoPreviewRoute(MediaSourceType.REMOTE, url))
            },
        )
    }

    composable<PhotoPreviewRoute> {
        PhotoPreviewScreen(
            onBack = { navController.popBackStack() },
            snackbarHostState = snackbarHostState
        )
    }

    composable<VideoPreviewRoute> {
        VideoPreviewScreen(
            onBack = { navController.popBackStack() },
            snackbarHostState = snackbarHostState
        )
    }

    composable<NotePreviewRoute> {
        NotePreviewScreen(
            onBack = { navController.popBackStack() },
            onNavigateToPhotoPreview = { url ->
                navController.navigate(PhotoPreviewRoute(MediaSourceType.REMOTE, url))
            },
            onNavigateToVideoPreview = { url ->
                navController.navigate(VideoPreviewRoute(MediaSourceType.REMOTE, url))
            },
            onNavigateToEdit = { noteId ->
                navController.navigate(EditNoteRoute(noteId))
            },
            snackbarHostState = snackbarHostState,
        )
    }

    composable<AuthRoute> {
        AuthScreen(
            onNavigateToHome = {
                onUpdateStartDestination(HomeRoute)
                navController.navigate(HomeRoute) {
                    popUpTo(0) { inclusive = true }
                }
            }
        )
    }

    composable<ProfileRoute> {
        ProfileScreen(
            onNavigateToSettings = {
                navController.navigate(SettingsRoute)
            },
            onNavigateToPhotoPreview = { url ->
                navController.navigate(PhotoPreviewRoute(MediaSourceType.REMOTE, url))
            },
            snackbarHostState = snackbarHostState,
        )
    }

    composable<SettingsRoute> {
        SettingsScreen(
            onBack = { navController.popBackStack() },
            onNavigateToAuth = {
                navController.navigate(AuthRoute) {
                    popUpTo(0) { inclusive = true }
                }
            },
            // :feature:settings is a library, so it has no application BuildConfig to read. Passed
            // from here rather than injected: a version string is display data, not a branch
            // condition, so there is no R8 constant-folding to preserve by keeping the static read
            // inside the screen -- and hoisting it lets the screenshot test pin a fixed value.
            versionName = BuildConfig.VERSION_NAME,
            snackbarHostState = snackbarHostState,
        )
    }
}