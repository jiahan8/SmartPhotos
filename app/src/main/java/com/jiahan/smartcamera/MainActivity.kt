package com.jiahan.smartcamera

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Create
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
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
import com.jiahan.smartcamera.home.HomeScreen
import com.jiahan.smartcamera.imagepreview.ImagePreviewScreen
import com.jiahan.smartcamera.note.NoteScreen
import com.jiahan.smartcamera.profile.ProfileScreen
import com.jiahan.smartcamera.search.SearchScreen
import com.jiahan.smartcamera.ui.theme.SmartCameraTheme
import dagger.hilt.android.AndroidEntryPoint

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val isDarkTheme by viewModel.isDarkTheme.collectAsState()

            SmartCameraTheme(
                darkTheme = isDarkTheme
            ) {
                val navController = rememberNavController()
                val items = listOf(
                    Screen.Home,
                    Screen.Note,
                    Screen.Search,
                    Screen.Profile
                )

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Scaffold(
                        bottomBar = {
                            NavigationBar(
                                modifier = Modifier.height(64.dp),
                                windowInsets = WindowInsets(0.dp),
                            ) {
                                val currentDestination =
                                    navController.currentBackStackEntryAsState().value?.destination
                                items.forEach { screen ->
                                    val selected = currentDestination?.route == screen.route

                                    NavigationBarItem(
                                        icon = {
                                            AnimatedIcon(
                                                selected = selected,
                                                imageVector = screen.icon,
                                                contentDescription = screen.title
                                            )
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
                    ) { innerPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = Screen.Home.route,
                            modifier = Modifier.padding(innerPadding)
                        ) {
                            composable(Screen.Home.route) {
                                HomeScreen(
                                    navController = navController
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
                                    navController = navController
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
                            composable(Screen.Profile.route) {
                                ProfileScreen()
                            }
                            composable(
                                route = Screen.Note.route
                            ) {
                                NoteScreen(
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
    val icon: ImageVector
) {
    object Home : Screen("home", "Home", Icons.Rounded.Home)
    object Note : Screen("note", "Note", Icons.Rounded.Create)
    object Search : Screen("search", "Search", Icons.Rounded.Search)
    object ImagePreview : Screen(
        route = "image?uri={uri}&text={text}&detect={detect}",
        title = "Photo",
        icon = Icons.Rounded.Search
    ) {
        const val URI_ARG = "uri"
        const val TEXT_ARG = "text"
        const val DETECT_ARG = "detect"
        fun createRoute(imageUri: String, text: String, detect: Boolean = false) =
            "image?uri=$imageUri&text=$text&detect=$detect"
    }

    object Profile : Screen("profile", "Profile", Icons.Rounded.Person)
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