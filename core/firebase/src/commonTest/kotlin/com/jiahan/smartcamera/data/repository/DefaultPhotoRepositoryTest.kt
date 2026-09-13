package com.jiahan.smartcamera.data.repository

import com.jiahan.smartcamera.domain.PhotoPage
import dev.gitlive.firebase.internal.decode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.nullable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers [DefaultPhotoRepository], which is entirely a reader of loosely-shaped Cloud Function
 * payloads -- so this file is mostly a table of payload shapes.
 *
 * The fake answers with a *raw* payload of nested maps and lists, the shape the platform SDK hands
 * GitLive, and runs it through GitLive's own `decode` -- the function `HttpsCallableResult.data`
 * calls. So each case exercises the real decoder and the mapping together, and only the network
 * call is faked.
 *
 * Numbers arrive as `Double` as often as `Int`, which is why the width/height/likes cases test both.
 *
 * The `hasMore` tests are the load-bearing ones. It is derived from the *raw row count*, not from
 * the parsed list, and the difference only shows when a row fails to parse -- at which point a
 * `photos.size` implementation quietly ends the feed for the rest of the session.
 */
class DefaultPhotoRepositoryTest {

    private class FakeUnsplashCallable : UnsplashCallable {
        val names = mutableListOf<String>()
        var payload: Any? = null
        var failure: Throwable? = null

        override suspend fun call(name: String, args: UnsplashArgs): UnsplashPayload? {
            names += name
            failure?.let { throw it }
            return decode(UnsplashPayload.serializer().nullable, payload)
        }
    }

    private val callable = FakeUnsplashCallable()
    private val repository = DefaultPhotoRepository(callable)

    /** A payload row in the shape the Unsplash-backed callable returns. */
    private fun photoRow(
        id: String? = "photo-1",
        urls: Map<String, Any?>? = mapOf("regular" to "https://img/regular.jpg"),
        user: Map<String, Any?>? = mapOf("name" to "Ansel"),
        extra: Map<String, Any?> = emptyMap(),
    ): Map<String, Any?> = buildMap {
        id?.let { put("id", it) }
        urls?.let { put("urls", it) }
        user?.let { put("user", it) }
        putAll(extra)
    }

    private fun payload(vararg rows: Any?): Map<String, Any?> = mapOf("photos" to rows.toList())

    private suspend fun listWith(data: Any?, pageSize: Int = 10): PhotoPage {
        callable.payload = data
        return repository.listPhotos(page = 1, pageSize = pageSize).getOrThrow()
    }

    // -------------------------------------------------------------------------
    // Which callable each read actually invokes
    // -------------------------------------------------------------------------

    @Test
    fun `listPhotos calls the listUnsplashPhotos function`() = runTest {
        callable.payload = payload()

        repository.listPhotos(page = 1, pageSize = 10)

        assertEquals(listOf("listUnsplashPhotos"), callable.names)
    }

    @Test
    fun `searchPhotos calls the searchUnsplashPhotos function`() = runTest {
        callable.payload = payload()

        repository.searchPhotos("mountains", page = 1, pageSize = 10)

        assertEquals(listOf("searchUnsplashPhotos"), callable.names)
    }

    // -------------------------------------------------------------------------
    // hasMore comes from the raw rows, not the parsed photos
    // -------------------------------------------------------------------------

    @Test
    fun `hasMore counts raw rows even when some fail to parse`() = runTest {
        val page = listWith(
            payload(
                photoRow(id = "a"),
                photoRow(id = null),
                photoRow(id = "b"),
                "not a map",
                photoRow(urls = null),
            ),
            pageSize = 5,
        )

        assertEquals(2, page.photos.size)
        assertTrue(page.hasMore)
    }

    @Test
    fun `hasMore is true when the page is full`() = runTest {
        val page = listWith(payload(photoRow(id = "a"), photoRow(id = "b")), pageSize = 2)

        assertTrue(page.hasMore)
    }

    @Test
    fun `hasMore is false when the page is short`() = runTest {
        val page = listWith(payload(photoRow(id = "a")), pageSize = 2)

        assertFalse(page.hasMore)
    }

    // -------------------------------------------------------------------------
    // Malformed envelopes
    // -------------------------------------------------------------------------

    @Test
    fun `an empty page is returned when the payload is null`() = runTest {
        val page = listWith(null)

        assertTrue(page.photos.isEmpty())
        assertFalse(page.hasMore)
    }

    @Test
    fun `an empty page is returned when the payload is not a map`() = runTest {
        assertTrue(listWith("photos").photos.isEmpty())
    }

    @Test
    fun `an empty page is returned when the photos key is missing`() = runTest {
        assertTrue(listWith(mapOf("results" to emptyList<Any>())).photos.isEmpty())
    }

    @Test
    fun `an empty page is returned when photos is not a list`() = runTest {
        assertTrue(listWith(mapOf("photos" to "none")).photos.isEmpty())
    }

    @Test
    fun `a failing callable fails the result`() = runTest {
        callable.failure = IllegalStateException("offline")

        assertTrue(repository.listPhotos(page = 1, pageSize = 10).isFailure)
    }

    // -------------------------------------------------------------------------
    // Rows that must be dropped
    // -------------------------------------------------------------------------

    @Test
    fun `a row without an id is dropped`() = runTest {
        assertTrue(listWith(payload(photoRow(id = null))).photos.isEmpty())
    }

    @Test
    fun `a row without urls is dropped`() = runTest {
        assertTrue(listWith(payload(photoRow(urls = null))).photos.isEmpty())
    }

    @Test
    fun `a row whose urls is not a map is dropped`() = runTest {
        val row = mapOf("id" to "a", "urls" to "https://img/regular.jpg")

        assertTrue(listWith(payload(row)).photos.isEmpty())
    }

    @Test
    fun `a row with no usable image url is dropped`() = runTest {
        val row = photoRow(urls = mapOf("small" to "https://img/small.jpg"))

        assertTrue(listWith(payload(row)).photos.isEmpty())
    }

    // -------------------------------------------------------------------------
    // photoUrl / thumbnailUrl fallback chains
    // -------------------------------------------------------------------------

    @Test
    fun `photoUrl prefers regular then full then raw`() = runTest {
        val all = photoRow(
            urls = mapOf(
                "regular" to "https://img/regular.jpg",
                "full" to "https://img/full.jpg",
                "raw" to "https://img/raw.jpg",
            )
        )
        val noRegular = photoRow(
            id = "b",
            urls = mapOf("full" to "https://img/full.jpg", "raw" to "https://img/raw.jpg"),
        )
        val rawOnly = photoRow(id = "c", urls = mapOf("raw" to "https://img/raw.jpg"))

        val photos = listWith(payload(all, noRegular, rawOnly)).photos

        assertEquals("https://img/regular.jpg", photos[0].photoUrl)
        assertEquals("https://img/full.jpg", photos[1].photoUrl)
        assertEquals("https://img/raw.jpg", photos[2].photoUrl)
    }

    @Test
    fun `thumbnailUrl prefers small then thumb`() = runTest {
        val both = photoRow(
            urls = mapOf(
                "regular" to "https://img/regular.jpg",
                "small" to "https://img/small.jpg",
                "thumb" to "https://img/thumb.jpg",
            )
        )
        val thumbOnly = photoRow(
            id = "b",
            urls = mapOf(
                "regular" to "https://img/regular.jpg",
                "thumb" to "https://img/thumb.jpg"
            ),
        )

        val photos = listWith(payload(both, thumbOnly)).photos

        assertEquals("https://img/small.jpg", photos[0].thumbnailUrl)
        assertEquals("https://img/thumb.jpg", photos[1].thumbnailUrl)
    }

    /** With no small or thumb, the full-size image is the thumbnail rather than nothing. */
    @Test
    fun `thumbnailUrl falls back to the photo url`() = runTest {
        val photo = listWith(payload(photoRow())).photos.single()

        assertEquals("https://img/regular.jpg", photo.thumbnailUrl)
    }

    // -------------------------------------------------------------------------
    // Optional fields
    // -------------------------------------------------------------------------

    @Test
    fun `description prefers description then alt_description`() = runTest {
        val both = photoRow(extra = mapOf("description" to "A", "alt_description" to "B"))
        val altOnly = photoRow(id = "b", extra = mapOf("alt_description" to "B"))
        val neither = photoRow(id = "c")

        val photos = listWith(payload(both, altOnly, neither)).photos

        assertEquals("A", photos[0].description)
        assertEquals("B", photos[1].description)
        assertNull(photos[2].description)
    }

    @Test
    fun `dimensions and likes read integers`() = runTest {
        val row = photoRow(extra = mapOf("width" to 1920, "height" to 1080, "likes" to 42))

        val photo = listWith(payload(row)).photos.single()

        assertEquals(1920, photo.width)
        assertEquals(1080, photo.height)
        assertEquals(42, photo.likes)
    }

    /** A JSON payload routinely delivers these as doubles. */
    @Test
    fun `dimensions and likes read doubles`() = runTest {
        val row = photoRow(extra = mapOf("width" to 1920.0, "height" to 1080.0, "likes" to 42.0))

        val photo = listWith(payload(row)).photos.single()

        assertEquals(1920, photo.width)
        assertEquals(1080, photo.height)
        assertEquals(42, photo.likes)
    }

    @Test
    fun `dimensions and likes default to zero when absent or malformed`() = runTest {
        val absent = photoRow()
        val malformed = photoRow(
            id = "b",
            extra = mapOf("width" to "wide", "height" to null, "likes" to "many"),
        )

        val photos = listWith(payload(absent, malformed)).photos

        photos.forEach { photo ->
            assertEquals(0, photo.width)
            assertEquals(0, photo.height)
            assertEquals(0, photo.likes)
        }
    }

    /**
     * The one case the move to GitLive changed. Its decoder reads a numeric *string* as the number
     * -- `"1920"` is 1920 -- where the Android SDK reader's `as? Number` cast read it as 0. Pinned
     * so the behaviour is a known one rather than an accident either way.
     */
    @Test
    fun `dimensions and likes parse numeric strings`() = runTest {
        val row = photoRow(extra = mapOf("width" to "1920", "height" to "1080", "likes" to "42"))

        val photo = listWith(payload(row)).photos.single()

        assertEquals(1920, photo.width)
        assertEquals(1080, photo.height)
        assertEquals(42, photo.likes)
    }

    @Test
    fun `color is carried through and null when absent`() = runTest {
        val withColor = photoRow(extra = mapOf("color" to "#RRGGBB"))
        val without = photoRow(id = "b")

        val photos = listWith(payload(withColor, without)).photos

        assertEquals("#RRGGBB", photos[0].color)
        assertNull(photos[1].color)
    }

    // -------------------------------------------------------------------------
    // Author
    // -------------------------------------------------------------------------

    @Test
    fun `username prefers name then username then empty`() = runTest {
        val named = photoRow(user = mapOf("name" to "Ansel", "username" to "ansel_a"))
        val handleOnly = photoRow(id = "b", user = mapOf("username" to "ansel_a"))
        val noUser = photoRow(id = "c", user = null)

        val photos = listWith(payload(named, handleOnly, noUser)).photos

        assertEquals("Ansel", photos[0].username)
        assertEquals("ansel_a", photos[1].username)
        assertEquals("", photos[2].username)
    }

    @Test
    fun `profile picture is read from the nested profile_image`() = runTest {
        val row = photoRow(
            user = mapOf(
                "name" to "Ansel",
                "profile_image" to mapOf("small" to "https://img/avatar.jpg"),
            )
        )

        assertEquals(
            "https://img/avatar.jpg",
            listWith(payload(row)).photos.single().profilePictureUrl
        )
    }

    @Test
    fun `profile picture is null when the user has none`() = runTest {
        assertNull(listWith(payload(photoRow())).photos.single().profilePictureUrl)
    }

    // -------------------------------------------------------------------------
    // searchPhotos shares the reader
    // -------------------------------------------------------------------------

    @Test
    fun `searchPhotos parses the same envelope`() = runTest {
        callable.payload = payload(photoRow(id = "a"), photoRow(id = "b"))

        val page = repository.searchPhotos("mountains", page = 1, pageSize = 2).getOrThrow()

        assertEquals(listOf("a", "b"), page.photos.map { it.id })
        assertTrue(page.hasMore)
    }

    @Test
    fun `searchPhotos fails when the callable fails`() = runTest {
        callable.failure = IllegalStateException("offline")

        assertTrue(repository.searchPhotos("mountains", page = 1, pageSize = 10).isFailure)
    }
}