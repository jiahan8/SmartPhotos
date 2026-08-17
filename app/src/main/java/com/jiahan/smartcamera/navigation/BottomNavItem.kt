package com.jiahan.smartcamera.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.graphics.vector.ImageVector
import com.jiahan.smartcamera.R

/** UI metadata for a bottom-bar destination. Kept separate from [Screen] so route types stay plain data. */
data class BottomNavItem(
    val route: Screen,
    val titleResId: Int,
    val icon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Home, R.string.home, Icons.Outlined.Home),
    BottomNavItem(Screen.Search, R.string.search, Icons.Outlined.Search),
    BottomNavItem(Screen.Note, R.string.note, Icons.Outlined.Create),
    BottomNavItem(Screen.Favorite, R.string.favorite, Icons.Outlined.FavoriteBorder),
    BottomNavItem(Screen.Profile, R.string.profile, Icons.Outlined.Person),
)