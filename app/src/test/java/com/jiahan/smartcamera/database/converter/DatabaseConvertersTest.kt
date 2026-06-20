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
    }
}