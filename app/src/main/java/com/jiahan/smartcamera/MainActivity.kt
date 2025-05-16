package com.jiahan.smartcamera

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jiahan.smartcamera.home.HomeScreen
import com.jiahan.smartcamera.imagepreview.ImagePreviewScreen
import com.jiahan.smartcamera.search.SearchScreen
import com.jiahan.smartcamera.ui.theme.SmartCameraTheme
import dagger.hilt.android.AndroidEntryPoint

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmartCameraTheme {
                val navController = rememberNavController()
                val items = listOf(
                    Screen.Home,
                    Screen.Search
                )

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Scaffold(
                        bottomBar = {
                            NavigationBar {
                                val currentDestination =
                                    navController.currentBackStackEntryAsState().value?.destination
                                items.forEach { screen ->
                                    NavigationBarItem(
                                        icon = {
                                            Icon(
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
                            composable(Screen.Home.route) { HomeScreen(navController) }
                            composable(Screen.Search.route) { SearchScreen(navController) }
                            composable(
                                route = Screen.ImagePreview.route,
                                arguments = listOf(
                                    navArgument(Screen.ImagePreview.URI_ARG) {
                                        type = NavType.StringType
                                    },
                                    navArgument(Screen.ImagePreview.TEXT_ARG) {
                                        type = NavType.StringType
                                    },
                                    navArgument(Screen.ImagePreview.DETECT_IMAGE_ARG) {
                                        type = NavType.BoolType
                                        defaultValue = false
                                    }
                                )
                            ) { backStackEntry ->
                                ImagePreviewScreen(
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

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Search : Screen("search", "Search", Icons.Default.Search)
    object ImagePreview : Screen(
        "imagepreview?uri={uri}&text={text}&detectimage={detectimage}",
        "Image Preview",
        Icons.Default.Search
    ) {
        const val URI_ARG = "uri"
        const val TEXT_ARG = "text"
        const val DETECT_IMAGE_ARG = "detectimage"
        fun createRoute(imageUri: String, text: String, detectImage: Boolean = false) =
            "imagepreview?uri=$imageUri&text=$text&detectimage=$detectImage"
    }
}