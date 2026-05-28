package com.jiahan.smartcamera.navigation

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.graphics.vector.ImageVector
import com.jiahan.smartcamera.R

sealed class Screen(
    val route: String,
    val titleResId: Int,
    val icon: ImageVector?
) {
    object Home : Screen("home", R.string.home, Icons.Outlined.Home)
    object Search : Screen("search", R.string.search, Icons.Outlined.Search) {
        const val SEARCH_DEEP_LINK_URI_PATTERN = "live://jiahan8.github.io/search"
    }

    object Note : Screen("note", R.string.note, Icons.Outlined.Create)
    object Favorite : Screen("favorite", R.string.favorite, Icons.Outlined.FavoriteBorder)

    object PhotoPreview : Screen("photo/{type}/{source}", R.string.photo, null) {
        const val TYPE_ARG = "type"
        const val SOURCE_ARG = "source"

        const val TYPE_LOCAL = "local"
        const val TYPE_REMOTE = "remote"

        fun createLocalRoute(uri: String) = "photo/$TYPE_LOCAL/${Uri.encode(uri)}"
        fun createRemoteRoute(url: String) = "photo/$TYPE_REMOTE/${Uri.encode(url)}"
    }

    object VideoPreview : Screen("video/{type}/{source}", R.string.video, null) {
        const val TYPE_ARG = "type"
        const val SOURCE_ARG = "source"

        const val TYPE_LOCAL = "local"
        const val TYPE_REMOTE = "remote"

        fun createLocalRoute(uri: String) = "video/$TYPE_LOCAL/${Uri.encode(uri)}"
        fun createRemoteRoute(url: String) = "video/$TYPE_REMOTE/${Uri.encode(url)}"
    }

    object NotePreview : Screen("notepreview/{id}", R.string.note_preview, null) {
        const val ID_ARG = "id"

        fun createRoute(id: String) = "notepreview/$id"
    }

    object Auth : Screen("auth", R.string.authentication, null)
    object Profile : Screen("profile", R.string.profile, Icons.Outlined.Person)
    object Settings : Screen("settings", R.string.settings, null)
}