package com.jiahan.smartcamera.database.converter

import com.jiahan.smartcamera.domain.DetectedLabel
import com.jiahan.smartcamera.domain.DetectedObject
import com.jiahan.smartcamera.domain.MediaDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseConvertersTest {

    private val converters = DatabaseConverters()

    // -------------------------------------------------------------------------
    // null / empty
    // -------------------------------------------------------------------------

    @Test
    fun `fromMediaList null returns null`() {
        assertNull(converters.fromMediaList(null))
    }

    @Test
    fun `toMediaList null returns null`() {
        assertNull(converters.toMediaList(null))
    }

    @Test
    fun `fromMediaList empty list returns empty JSON array string`() {
        val result = converters.fromMediaList(emptyList())
        assertEquals("[]", result)
    }

    @Test
    fun `toMediaList empty JSON array returns empty list`() {
        val result = converters.toMediaList("[]")
        assertTrue(requireNotNull(result).isEmpty())
    }

    // -------------------------------------------------------------------------
    // Round-trip: photo-only MediaDetail
    // -------------------------------------------------------------------------

    @Test
    fun `photo-only MediaDetail survives round-trip`() {
        val original = listOf(
            MediaDetail(photoUrl = "https://example.com/photo.jpg", isVideo = false)
        )
        val json = converters.fromMediaList(original)
        val restored = requireNotNull(converters.toMediaList(json))

        assertEquals(1, restored.size)
        assertEquals("https://example.com/photo.jpg", restored[0].photoUrl)
        assertNull(restored[0].videoUrl)
        assertEquals(false, restored[0].isVideo)
    }

    // -------------------------------------------------------------------------
    // Round-trip: video MediaDetail with thumbnail
    // -------------------------------------------------------------------------

    @Test
    fun `video MediaDetail with thumbnail survives round-trip`() {
        val original = listOf(
            MediaDetail(
                videoUrl = "https://example.com/video.mp4",
                thumbnailUrl = "https://example.com/thumb.jpg",
                isVideo = true
            )
        )
        val json = converters.fromMediaList(original)
        val restored = requireNotNull(converters.toMediaList(json))

        assertEquals(1, restored.size)
        assertEquals("https://example.com/video.mp4", restored[0].videoUrl)
        assertEquals("https://example.com/thumb.jpg", restored[0].thumbnailUrl)
        assertTrue(restored[0].isVideo)
        assertNull(restored[0].photoUrl)
    }

    // -------------------------------------------------------------------------
    // Round-trip: generatedText
    // -------------------------------------------------------------------------

    @Test
    fun `generatedText survives round-trip`() {
        val original = listOf(
            MediaDetail(
                photoUrl = "https://example.com/photo.jpg",
                generatedText = listOf("a cat", "a dog")
            )
        )
        val restored = requireNotNull(converters.toMediaList(converters.fromMediaList(original)))
        assertEquals(listOf("a cat", "a dog"), restored[0].generatedText)
    }

    // -------------------------------------------------------------------------
    // Round-trip: generatedObjects
    // -------------------------------------------------------------------------

    @Test
    fun `generatedObjects survive round-trip`() {
        val original = listOf(
            MediaDetail(
                photoUrl = "https://example.com/photo.jpg",
                generatedObjects = listOf(
                    DetectedObject("cat", 0.95),
                    DetectedObject("dog", 0.88)
                )
            )
        )
        val restored = requireNotNull(converters.toMediaList(converters.fromMediaList(original)))
        val objects = requireNotNull(restored[0].generatedObjects)

        assertEquals(2, objects.size)
        assertEquals("cat", objects[0].objectName)
        assertEquals(0.95, objects[0].score, 0.001)
        assertEquals("dog", objects[1].objectName)
        assertEquals(0.88, objects[1].score, 0.001)
    }

    // -------------------------------------------------------------------------
    // Round-trip: generatedLabels
    // -------------------------------------------------------------------------

    @Test
    fun `generatedLabels survive round-trip`() {
        val original = listOf(
            MediaDetail(
                photoUrl = "https://example.com/photo.jpg",
                generatedLabels = listOf(
                    DetectedLabel("outdoor", 0.99),
                    DetectedLabel("nature", 0.75)
                )
            )
        )
        val restored = requireNotNull(converters.toMediaList(converters.fromMediaList(original)))
        val labels = requireNotNull(restored[0].generatedLabels)

        assertEquals(2, labels.size)
        assertEquals("outdoor", labels[0].label)
        assertEquals(0.99, labels[0].score, 0.001)
    }

    // -------------------------------------------------------------------------
    // Round-trip: generatedLandmarks
    // -------------------------------------------------------------------------

    @Test
    fun `generatedLandmarks survive round-trip`() {
        val original = listOf(
            MediaDetail(
                photoUrl = "https://example.com/photo.jpg",
                generatedLandmarks = listOf(
                    DetectedLabel("Eiffel Tower", 0.92),
                    DetectedLabel("Golden Gate Bridge", 0.81)
                )
            )
        )
        val restored = requireNotNull(converters.toMediaList(converters.fromMediaList(original)))
        val landmarks = requireNotNull(restored[0].generatedLandmarks)

        assertEquals(2, landmarks.size)
        assertEquals("Eiffel Tower", landmarks[0].label)
        assertEquals(0.92, landmarks[0].score, 0.001)
        assertEquals("Golden Gate Bridge", landmarks[1].label)
        assertEquals(0.81, landmarks[1].score, 0.001)
    }

    // -------------------------------------------------------------------------
    // Round-trip: generatedLogos
    // -------------------------------------------------------------------------

    @Test
    fun `generatedLogos survive round-trip`() {
        val original = listOf(
            MediaDetail(
                photoUrl = "https://example.com/photo.jpg",
                generatedLogos = listOf(
                    DetectedLabel("Nike", 0.97),
                    DetectedLabel("Adidas", 0.7)
                )
            )
        )
        val restored = requireNotNull(converters.toMediaList(converters.fromMediaList(original)))
        val logos = requireNotNull(restored[0].generatedLogos)

        assertEquals(2, logos.size)
        assertEquals("Nike", logos[0].label)
        assertEquals(0.97, logos[0].score, 0.001)
        assertEquals("Adidas", logos[1].label)
        assertEquals(0.7, logos[1].score, 0.001)
    }

    // -------------------------------------------------------------------------
    // Round-trip: multiple MediaDetail items
    // -------------------------------------------------------------------------

    @Test
    fun `multiple MediaDetail items survive round-trip`() {
        val original = listOf(
            MediaDetail(photoUrl = "https://example.com/1.jpg", isVideo = false),
            MediaDetail(videoUrl = "https://example.com/2.mp4", isVideo = true),
            MediaDetail(
                photoUrl = "https://example.com/3.jpg",
                generatedText = listOf("sunset"),
                generatedLabels = listOf(DetectedLabel("sky", 0.9))
            )
        )
        val restored = requireNotNull(converters.toMediaList(converters.fromMediaList(original)))

        assertEquals(3, restored.size)
        assertEquals("https://example.com/1.jpg", restored[0].photoUrl)
        assertEquals("https://example.com/2.mp4", restored[1].videoUrl)
        assertEquals(listOf("sunset"), restored[2].generatedText)
        assertEquals(1, requireNotNull(restored[2].generatedLabels).size)
    }

    // -------------------------------------------------------------------------
    // Optional fields default to null when absent
    // -------------------------------------------------------------------------

    @Test
    fun `absent optional fields default to null after deserialization`() {
        val original = listOf(MediaDetail(isVideo = false))
        val restored = requireNotNull(converters.toMediaList(converters.fromMediaList(original)))

        assertNull(restored[0].photoUrl)
        assertNull(restored[0].videoUrl)
        assertNull(restored[0].thumbnailUrl)
        assertNull(restored[0].generatedText)
        assertNull(restored[0].generatedObjects)
        assertNull(restored[0].generatedLabels)
        assertNull(restored[0].generatedLandmarks)
        assertNull(restored[0].generatedLogos)
    }

    // -------------------------------------------------------------------------
    // Backward compatibility with rows written by the previous hand-rolled converter
    // -------------------------------------------------------------------------

    /**
     * The converter used to be hand-rolled with `org.json`, and rows it wrote are still on disk in
     * installs that upgrade. Every round-trip test above encodes with the *current* serializer
     * first, so none of them can catch a break in reading that older format — this one pins it by
     * decoding a literal string in exactly the shape the old encoder produced (nulls omitted,
     * `isVideo` always written).
     */
    @Test
    fun `toMediaList decodes a row written by the legacy org_json converter`() {
        val legacyJson = """
            [{"photoUrl":"https://example.com/photo.jpg","isVideo":false,
              "generatedText":["a cat","a dog"],
              "generatedObjects":[{"objectName":"cat","score":0.95}],
              "generatedLabels":[{"label":"outdoor","score":0.99}],
              "generatedLandmarks":[{"label":"Eiffel Tower","score":0.92}],
              "generatedLogos":[{"label":"Nike","score":0.97}]},
             {"videoUrl":"https://example.com/video.mp4",
              "thumbnailUrl":"https://example.com/thumb.jpg","isVideo":true}]
        """.trimIndent()

        val restored = requireNotNull(converters.toMediaList(legacyJson))

        assertEquals(2, restored.size)

        val photo = restored[0]
        assertEquals("https://example.com/photo.jpg", photo.photoUrl)
        assertNull(photo.videoUrl)
        assertNull(photo.thumbnailUrl)
        assertEquals(false, photo.isVideo)
        assertEquals(listOf("a cat", "a dog"), photo.generatedText)
        assertEquals("cat", requireNotNull(photo.generatedObjects)[0].objectName)
        assertEquals(0.95, requireNotNull(photo.generatedObjects)[0].score, 0.001)
        assertEquals("outdoor", requireNotNull(photo.generatedLabels)[0].label)
        assertEquals(0.99, requireNotNull(photo.generatedLabels)[0].score, 0.001)
        assertEquals("Eiffel Tower", requireNotNull(photo.generatedLandmarks)[0].label)
        assertEquals("Nike", requireNotNull(photo.generatedLogos)[0].label)

        val video = restored[1]
        assertEquals("https://example.com/video.mp4", video.videoUrl)
        assertEquals("https://example.com/thumb.jpg", video.thumbnailUrl)
        assertTrue(video.isVideo)
        assertNull(video.photoUrl)
        assertNull(video.generatedText)
    }

    /**
     * Locks in `ignoreUnknownKeys` so a later tweak to the `Json` instance can't silently
     * reintroduce strict parsing, which would make rows written by a newer build undecodable by
     * an older one.
     */
    @Test
    fun `toMediaList ignores keys it does not model`() {
        val jsonWithExtraKey =
            """[{"photoUrl":"https://example.com/photo.jpg","isVideo":false,"futureField":42}]"""

        val restored = requireNotNull(converters.toMediaList(jsonWithExtraKey))

        assertEquals(1, restored.size)
        assertEquals("https://example.com/photo.jpg", restored[0].photoUrl)
    }
}