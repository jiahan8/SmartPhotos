package com.jiahan.smartcamera.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.graphics.vector.ImageVector
import com.jiahan.smartcamera.favorite.FavoriteRoute
import com.jiahan.smartcamera.home.HomeRoute
import com.jiahan.smartcamera.note.NoteRoute
import com.jiahan.smartcamera.profile.ProfileRoute
import com.jiahan.smartcamera.search.SearchRoute
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.core.ui.R as UiR
import com.jiahan.smartcamera.feature.profile.R as ProfileR

/**
 * The bottom bar's five destinations and the UI metadata each needs, kept off the route types so
 * those stay plain data -- only five of the twelve destinations appear in the bottom bar.
 *
 * **An enum rather than a data class and a list, and that is the load-bearing part.** [route] is
 * `Any` because that is what Navigation Compose takes for a destination, and the routes have no
 * common supertype since they moved into the feature packages -- a `sealed` type's subtypes must
 * live in the same module, so the old `Screen` hierarchy could not survive a `:feature:*` split
 * under any name. An `Any` on a public data class with a public list would let anything into the
 * bottom bar, and a wrong entry would fail at runtime by simply never matching. Inside an enum the
 * constructor is private, so the set is closed at these five, and a `when` over them is exhaustive
 * across module boundaries in a way a sealed route hierarchy no longer can be. Now in Android's
 * `TopLevelDestination` has the same shape for the same reason.
 *
 * It carries route *instances* where NiA carries `KClass<*>`, because none of these five take
 * arguments, so one value serves both `navigate(route)` and `hasRoute(route::class)`. A top-level
 * destination that took arguments would need NiA's split instead: the class here, and a typed
 * `navigateTo…()` extension in the feature that owns the route.
 */
enum class TopLevelDestination(
    val route: Any,
    val titleResId: Int,
    val icon: ImageVector,
    /**
     * Whether re-tapping this tab while already on it scrolls its list back to the top. A property
     * rather than a `when` at the call site: the behaviour is the same call for every tab that has
     * it, so the only thing that varies is which tabs do -- and that is metadata about the
     * destination, like [icon] and [titleResId] beside it. It also keeps the guarantee the `when`
     * would have given, since a new entry cannot compile without answering the question.
     */
    val scrollsToTop: Boolean
) {
    HOME(HomeRoute, R.string.home, Icons.Outlined.Home, scrollsToTop = true),
    SEARCH(SearchRoute, UiR.string.search, Icons.Outlined.Search, scrollsToTop = true),
    NOTE(NoteRoute, R.string.note, Icons.Outlined.Create, scrollsToTop = false),
    FAVORITE(FavoriteRoute, R.string.favorite, Icons.Outlined.FavoriteBorder, scrollsToTop = true),
    PROFILE(ProfileRoute, ProfileR.string.profile, Icons.Outlined.Person, scrollsToTop = false),
}