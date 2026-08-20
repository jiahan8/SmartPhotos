package com.jiahan.smartcamera.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe Navigation Compose routes (see
 * https://developer.android.com/guide/navigation/design/type-safety). Each destination is a
 * `@Serializable` type instead of a hand-built path string; Navigation Compose generates the
 * route pattern and argument (de)serialization from these classes, so argument encoding (URL
 * escaping, etc.) is no longer something this codebase manages by hand.
 *
 * UI-only metadata (bottom-bar icon/title) intentionally lives elsewhere (see
 * [com.jiahan.smartcamera.navigation.BottomNavItem]) rather than on these route types, since only
 * some destinations appear in the bottom bar and route types should stay plain data.
 */
sealed interface Screen {

    @Serializable
    data object Home : Screen

    @Serializable
    data object Search : Screen {
        const val SEARCH_DEEP_LINK_URI_PATTERN = "live://jiahan8.github.io/search"
    }

    @Serializable
    data object Note : Screen

    @Serializable
    data class EditNote(val noteId: String) : Screen

    @Serializable
    data object Favorite : Screen

    @Serializable
    data object Explore : Screen

    @Serializable
    data class PhotoPreview(val type: MediaSourceType, val source: String) : Screen

    @Serializable
    data class VideoPreview(val type: MediaSourceType, val source: String) : Screen

    @Serializable
    data class NotePreview(val id: String) : Screen

    @Serializable
    data object Auth : Screen

    @Serializable
    data object Profile : Screen

    @Serializable
    data object Settings : Screen
}

@Serializable
enum class MediaSourceType { LOCAL, REMOTE }