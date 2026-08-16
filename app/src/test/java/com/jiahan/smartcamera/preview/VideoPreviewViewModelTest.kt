package com.jiahan.smartcamera.preview

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.jiahan.smartcamera.navigation.Screen
import com.jiahan.smartcamera.util.ErrorHandler
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VideoPreviewViewModelTest {

    private fun createViewModel(type: String?, source: String?): VideoPreviewViewModel {
        val map = mutableMapOf<String, Any?>()
        if (type != null) map[Screen.VideoPreview.TYPE_ARG] = type
        if (source != null) map[Screen.VideoPreview.SOURCE_ARG] = source
        return VideoPreviewViewModel(SavedStateHandle(map), mockk<ErrorHandler>(relaxed = true))
    }

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    // -------------------------------------------------------------------------
    // Remote URL
    // -------------------------------------------------------------------------

    @Test
    fun `remote type with valid url returns RemoteUrl`() {
        val url = "https://example.com/video.mp4"
        val vm = createViewModel(Screen.VideoPreview.TYPE_REMOTE, url)

        val source = vm.videoSource
        assertTrue(source is VideoSource.RemoteUrl)
        assertEquals(url, (source as VideoSource.RemoteUrl).url)
    }

    @Test
    fun `remote type with percent-encoded url decodes percent sign`() {
        // "%25" in the source argument should be decoded to "%"
        val encodedSource = "https://example.com/path%25video.mp4"
        val vm = createViewModel(Screen.VideoPreview.TYPE_REMOTE, encodedSource)

        val source = vm.videoSource as VideoSource.RemoteUrl
        assertEquals("https://example.com/path%video.mp4", source.url)
    }

    // -------------------------------------------------------------------------
    // Local URI
    // -------------------------------------------------------------------------

    @Test
    fun `local type with valid uri returns LocalUri`() {
        val uriString = "content://media/external/video/1"
        val mockUri = mockk<Uri>()
        every { Uri.parse(uriString) } returns mockUri

        val vm = createViewModel(Screen.VideoPreview.TYPE_LOCAL, uriString)

        val source = vm.videoSource
        assertTrue(source is VideoSource.LocalUri)
        assertEquals(mockUri, (source as VideoSource.LocalUri).uri)
    }

    @Test
    fun `local type with percent-encoded uri decodes percent sign`() {
        val encodedSource = "content://media/external/video%251"
        val decodedSource = "content://media/external/video%1"
        val mockUri = mockk<Uri>()
        every { Uri.parse(decodedSource) } returns mockUri

        val vm = createViewModel(Screen.VideoPreview.TYPE_LOCAL, encodedSource)

        val source = vm.videoSource as VideoSource.LocalUri
        assertEquals(mockUri, source.uri)
    }

    // -------------------------------------------------------------------------
    // Null / missing arguments
    // -------------------------------------------------------------------------

    @Test
    fun `missing type arg returns null videoSource`() {
        val vm = createViewModel(type = null, source = "https://example.com/video.mp4")
        assertNull(vm.videoSource)
    }

    @Test
    fun `missing source arg returns null videoSource`() {
        val vm = createViewModel(type = Screen.VideoPreview.TYPE_REMOTE, source = null)
        assertNull(vm.videoSource)
    }

    @Test
    fun `both args missing returns null videoSource`() {
        val vm = createViewModel(type = null, source = null)
        assertNull(vm.videoSource)
    }

    // -------------------------------------------------------------------------
    // Unknown type
    // -------------------------------------------------------------------------

    @Test
    fun `unknown type returns null videoSource`() {
        val vm = createViewModel(type = "unknown", source = "https://example.com/video.mp4")
        assertNull(vm.videoSource)
    }
}