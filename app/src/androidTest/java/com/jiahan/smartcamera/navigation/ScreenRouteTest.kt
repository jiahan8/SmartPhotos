package com.jiahan.smartcamera.navigation

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for the [Screen] route builders. These exercise [android.net.Uri.encode],
 * which is part of the Android framework and therefore must run on a device/emulator.
 */
@RunWith(AndroidJUnit4::class)
class ScreenRouteTest {

    @Test
    fun photoPreview_remoteRoute_encodesUrlArgument() {
        val url = "https://example.com/image.jpg?size=large&id=1"

        val route = Screen.PhotoPreview.createRemoteRoute(url)

        assertTrue(route.startsWith("photo/${Screen.PhotoPreview.TYPE_REMOTE}/"))
        val encoded = route.removePrefix("photo/${Screen.PhotoPreview.TYPE_REMOTE}/")
        assertEquals(url, Uri.decode(encoded))
    }

    @Test
    fun photoPreview_localRoute_encodesUriArgument() {
        val uri = "content://media/external/images/1"

        val route = Screen.PhotoPreview.createLocalRoute(uri)

        assertTrue(route.startsWith("photo/${Screen.PhotoPreview.TYPE_LOCAL}/"))
        assertEquals(
            uri,
            Uri.decode(route.removePrefix("photo/${Screen.PhotoPreview.TYPE_LOCAL}/"))
        )
    }

    @Test
    fun videoPreview_remoteRoute_encodesUrlArgument() {
        val url = "https://example.com/clip.mp4?token=a/b+c"

        val route = Screen.VideoPreview.createRemoteRoute(url)

        assertTrue(route.startsWith("video/${Screen.VideoPreview.TYPE_REMOTE}/"))
        assertEquals(
            url,
            Uri.decode(route.removePrefix("video/${Screen.VideoPreview.TYPE_REMOTE}/"))
        )
    }

    @Test
    fun videoPreview_localRoute_encodesUriArgument() {
        val uri = "content://media/external/video/42"

        val route = Screen.VideoPreview.createLocalRoute(uri)

        assertTrue(route.startsWith("video/${Screen.VideoPreview.TYPE_LOCAL}/"))
        assertEquals(
            uri,
            Uri.decode(route.removePrefix("video/${Screen.VideoPreview.TYPE_LOCAL}/"))
        )
    }

    @Test
    fun notePreview_route_embedsId() {
        val route = Screen.NotePreview.createRoute("abc123")

        assertEquals("notepreview/abc123", route)
    }

    @Test
    fun bottomNavRoutes_areStableIdentifiers() {
        assertEquals("home", Screen.Home.route)
        assertEquals("search", Screen.Search.route)
        assertEquals("note", Screen.Note.route)
        assertEquals("favorite", Screen.Favorite.route)
        assertEquals("profile", Screen.Profile.route)
    }
}