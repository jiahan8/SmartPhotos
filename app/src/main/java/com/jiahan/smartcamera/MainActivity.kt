package com.jiahan.smartcamera

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.jiahan.smartcamera.Screen.ImagePreview.DETECT_ARG
import com.jiahan.smartcamera.Screen.ImagePreview.TEXT_ARG
import com.jiahan.smartcamera.Screen.ImagePreview.URI_ARG
import com.jiahan.smartcamera.auth.AuthScreen
import com.jiahan.smartcamera.favorite.FavoriteScreen
import com.jiahan.smartcamera.home.HomeScreen
import com.jiahan.smartcamera.note.NoteScreen
import com.jiahan.smartcamera.preview.ImagePreviewScreen
import com.jiahan.smartcamera.preview.NotePreviewScreen
import com.jiahan.smartcamera.preview.PhotoPreviewScreen
import com.jiahan.smartcamera.preview.PhotoSource
import com.jiahan.smartcamera.preview.VideoPreviewScreen
import com.jiahan.smartcamera.preview.VideoSource
import com.jiahan.smartcamera.profile.ProfileScreen
import com.jiahan.smartcamera.search.SearchScreen
import com.jiahan.smartcamera.ui.theme.SmartCameraTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setOnExitAnimationListener { splashScreenView ->
            val fadeOutDuration = 500L
            val scaleAnim = splashScreenView.iconView.animate()
                .scaleX(1.5f)
                .scaleY(1.5f)
                .alpha(0f)
                .setDuration(fadeOutDuration)

            val fadeOut = splashScreenView.view.animate()
                .alpha(0f)
                .setDuration(fadeOutDuration)
                .withEndAction {
                    splashScreenView.remove()
                }

            // Start animations together
            scaleAnim.start()
            fadeOut.start()
        }

        super.onCreate(savedInstanceState)

        var isAppReady = false
        splashScreen.setKeepOnScreenCondition {
            !isAppReady
        }
        lifecycleScope.launch {
            delay(1000)
            isAppReady = true
        }

        setContent {
            val isDarkTheme by viewModel.isDarkTheme.collectAsState()
            val showBottomBar = remember { mutableStateOf(true) }

            SmartCameraTheme(
                darkTheme = isDarkTheme
            ) {
                val navController = rememberNavController()
                val items = listOf(
                    Screen.Home,
                    Screen.Search,
                    Screen.Note,
                    Screen.Favorite,
                    Screen.Profile
                )
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                val currentRoute = currentDestination?.route
                val isBottomBarVisible = remember(currentRoute, showBottomBar.value) {
                    (currentRoute in items.map { it.route }) && showBottomBar.value
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
                                NavigationBar(
                                    modifier = Modifier.height(64.dp),
                                    windowInsets = WindowInsets(0.dp),
                                ) {
                                    items.forEach { screen ->
                                        val selected = currentDestination?.route == screen.route

                                        NavigationBarItem(
                                            icon = {
                                                screen.icon?.let { icon ->
                                                    AnimatedIcon(
                                                        selected = selected,
                                                        imageVector = icon,
                                                        contentDescription = screen.title
                                                    )
                                                }
                                            },
                                            label = { Text(screen.title) },
                                            selected = currentDestination?.route == screen.route,
                                            onClick = {
                                                navController.navigate(screen.route) {
                                                    popUpTo(navController.graph.startDestinationId) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = viewModel.startDestination,
                            modifier = Modifier.padding(innerPadding)
                        ) {
                            composable(Screen.Home.route) {
                                HomeScreen(
                                    navController = navController,
                                    onScrollDirectionChanged = { isScrollingUp ->
                                        showBottomBar.value = isScrollingUp
                                    }
                                )
                            }
                            composable(
                                route = Screen.Search.route,
                                deepLinks = listOf(
                                    navDeepLink {
                                        uriPattern = SEARCH_DEEP_LINK_URI_PATTERN
                                    }
                                )
                            ) {
                                SearchScreen(
                                    navController = navController,
                                    onScrollDirectionChanged = { isScrollingUp ->
                                        showBottomBar.value = isScrollingUp
                                    }
                                )
                            }
                            composable(
                                route = Screen.ImagePreview.route,
                                arguments = listOf(
                                    navArgument(URI_ARG) {
                                        type = NavType.StringType
                                    },
                                    navArgument(TEXT_ARG) {
                                        type = NavType.StringType
                                    },
                                    navArgument(DETECT_ARG) {
                                        type = NavType.BoolType
                                    }
                                ),
                                deepLinks = listOf(
                                    navDeepLink {
                                        uriPattern = IMAGE_DEEP_LINK_URI_PATTERN
                                    }
                                )
                            ) { backStackEntry ->
                                ImagePreviewScreen(
                                    navController = navController
                                )
                            }
                            composable(route = Screen.Note.route) {
                                NoteScreen(
                                    navController = navController
                                )
                            }
                            composable(route = Screen.Favorite.route) {
                                FavoriteScreen(
                                    navController = navController,
                                    onScrollDirectionChanged = { isScrollingUp ->
                                        showBottomBar.value = isScrollingUp
                                    }
                                )
                            }
                            composable(
                                route = Screen.PhotoPreview.route,
                                arguments = listOf(
                                    navArgument(Screen.PhotoPreview.TYPE_ARG) {
                                        type = NavType.StringType
                                    },
                                    navArgument(Screen.PhotoPreview.SOURCE_ARG) {
                                        type = NavType.StringType
                                    }
                                )
                            ) { backStackEntry ->
                                val type =
                                    backStackEntry.arguments?.getString(Screen.PhotoPreview.TYPE_ARG)
                                val source =
                                    backStackEntry.arguments?.getString(Screen.PhotoPreview.SOURCE_ARG)
                                        ?.replace("%25", "%")

                                if (type != null && source != null) {
                                    val photoSource = when (type) {
                                        Screen.PhotoPreview.TYPE_LOCAL -> PhotoSource.LocalUri(
                                            source.toUri()
                                        )

                                        Screen.PhotoPreview.TYPE_REMOTE -> PhotoSource.RemoteUrl(
                                            source
                                        )

                                        else -> null
                                    }

                                    photoSource?.let {
                                        PhotoPreviewScreen(
                                            photoSource = it,
                                            navController = navController
                                        )
                                    }
                                }
                            }
                            composable(
                                route = Screen.VideoPreview.route,
                                arguments = listOf(
                                    navArgument(Screen.VideoPreview.TYPE_ARG) {
                                        type = NavType.StringType
                                    },
                                    navArgument(Screen.VideoPreview.SOURCE_ARG) {
                                        type = NavType.StringType
                                    }
                                )
                            ) { backStackEntry ->
                                val type =
                                    backStackEntry.arguments?.getString(Screen.VideoPreview.TYPE_ARG)
                                val source =
                                    backStackEntry.arguments?.getString(Screen.VideoPreview.SOURCE_ARG)
                                        ?.replace("%25", "%")

                                if (type != null && source != null) {
                                    val videoSource = when (type) {
                                        Screen.VideoPreview.TYPE_LOCAL -> VideoSource.LocalUri(
                                            source.toUri()
                                        )

                                        Screen.VideoPreview.TYPE_REMOTE -> VideoSource.RemoteUrl(
                                            source
                                        )

                                        else -> null
                                    }

                                    videoSource?.let {
                                        VideoPreviewScreen(
                                            videoSource = it,
                                            navController = navController
                                        )
                                    }
                                }
                            }
                            composable(
                                route = Screen.NotePreview.route,
                                arguments = listOf(
                                    navArgument(Screen.NotePreview.ID_ARG) {
                                        type = NavType.StringType
                                    }
                                )
                            ) { backStackEntry ->
                                NotePreviewScreen(
                                    navController = navController
                                )
                            }
                            composable(route = Screen.Auth.route) {
                                AuthScreen(
                                    navController = navController
                                )
                            }
                            composable(route = Screen.Profile.route) {
                                ProfileScreen(
                                    navController = navController
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val SEARCH_DEEP_LINK_URI_PATTERN = "live://jiahan8.github.io/search"
private const val IMAGE_DEEP_LINK_URI_PATTERN =
    "live://jiahan8.github.io/image?uri={$URI_ARG}&text={$TEXT_ARG}&detect={$DETECT_ARG}"

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector?
) {
    object Home : Screen("home", "Home", Icons.Outlined.Home)
    object Search : Screen("search", "Search", Icons.Outlined.Search)
    object Note : Screen("note", "Note", Icons.Outlined.Create)
    object Favorite : Screen("favorite", "Favorite", Icons.Outlined.FavoriteBorder)
    object ImagePreview : Screen(
        route = "image?uri={uri}&text={text}&detect={detect}",
        title = "Photo",
        icon = Icons.Outlined.Search
    ) {
        const val URI_ARG = "uri"
        const val TEXT_ARG = "text"
        const val DETECT_ARG = "detect"
        fun createRoute(imageUri: String, text: String, detect: Boolean = false) =
            "image?uri=$imageUri&text=$text&detect=$detect"
    }

    object PhotoPreview : Screen("photo/{type}/{source}", "Photo", null) {
        const val TYPE_ARG = "type"
        const val SOURCE_ARG = "source"

        const val TYPE_LOCAL = "local"
        const val TYPE_REMOTE = "remote"

        fun createLocalRoute(uri: String) = "photo/$TYPE_LOCAL/${Uri.encode(uri)}"
        fun createRemoteRoute(url: String) = "photo/$TYPE_REMOTE/${Uri.encode(url)}"
    }

    object VideoPreview : Screen("video/{type}/{source}", "Video", null) {
        const val TYPE_ARG = "type"
        const val SOURCE_ARG = "source"

        const val TYPE_LOCAL = "local"
        const val TYPE_REMOTE = "remote"

        fun createLocalRoute(uri: String) = "video/$TYPE_LOCAL/${Uri.encode(uri)}"
        fun createRemoteRoute(url: String) = "video/$TYPE_REMOTE/${Uri.encode(url)}"
    }

    object NotePreview : Screen("notepreview/{id}", "Note Preview", null) {
        const val ID_ARG = "id"

        fun createRoute(id: String) = "notepreview/$id"
    }

    object Auth : Screen("auth", "Auth", null)
    object Profile : Screen("profile", "Profile", Icons.Outlined.Person)
}

@Composable
fun AnimatedIcon(
    selected: Boolean,
    imageVector: ImageVector,
    contentDescription: String
) {
    val transition = updateTransition(targetState = selected, label = "IconTransition")
    val scale by transition.animateFloat(
        label = "IconScale",
        transitionSpec = {
            if (targetState) {
                spring(dampingRatio = 0.2f, stiffness = 100f)
            } else {
                tween(durationMillis = 300)
            }
        }
    ) { isSelected ->
        if (isSelected) 1.2f else 1f
    }

    val color = if (selected)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = Modifier.scale(scale),
        tint = color
    )
}