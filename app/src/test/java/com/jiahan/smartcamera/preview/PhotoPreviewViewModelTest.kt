package com.jiahan.smartcamera.preview

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.jiahan.smartcamera.navigation.Screen
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

class PhotoPreviewViewModelTest {

    private fun createViewModel(type: String?, source: String?): PhotoPreviewViewModel {
        val map = mutableMapOf<String, Any?>()
        if (type != null) map[Screen.PhotoPreview.TYPE_ARG] = type
        if (source != null) map[Screen.PhotoPreview.SOURCE_ARG] = source
        return PhotoPreviewViewModel(SavedStateHandle(map))
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
        val url = "https://example.com/photo.jpg"
        val vm = createViewModel(Screen.PhotoPreview.TYPE_REMOTE, url)

        val source = vm.photoSource
        assertTrue(source is PhotoSource.RemoteUrl)
        assertEquals(url, (source as PhotoSource.RemoteUrl).url)
    }

    @Test
    fun `remote type with percent-encoded url decodes percent sign`() {
        // "%25" in the source argument should be decoded to "%"
        val encodedSource = "https://example.com/path%25foo.jpg"
        val vm = createViewModel(Screen.PhotoPreview.TYPE_REMOTE, encodedSource)

        val source = vm.photoSource as PhotoSource.RemoteUrl
        assertEquals("https://example.com/path%foo.jpg", source.url)
    }

    // -------------------------------------------------------------------------
    // Local URI
    // -------------------------------------------------------------------------

    @Test
    fun `local type with valid uri returns LocalUri`() {
        val uriString = "content://media/external/images/1"
        val mockUri = mockk<Uri>()
        every { Uri.parse(uriString) } returns mockUri

        val vm = createViewModel(Screen.PhotoPreview.TYPE_LOCAL, uriString)

        val source = vm.photoSource
        assertTrue(source is PhotoSource.LocalUri)
        assertEquals(mockUri, (source as PhotoSource.LocalUri).uri)
    }

    @Test
    fun `local type with percent-encoded uri decodes percent sign`() {
        val encodedSource = "content://media/external/images%251"
        val decodedSource = "content://media/external/images%1"
        val mockUri = mockk<Uri>()
        every { Uri.parse(decodedSource) } returns mockUri

        val vm = createViewModel(Screen.PhotoPreview.TYPE_LOCAL, encodedSource)

        val source = vm.photoSource as PhotoSource.LocalUri
        assertEquals(mockUri, source.uri)
    }

    // -------------------------------------------------------------------------
    // Null / missing arguments
    // -------------------------------------------------------------------------

    @Test
    fun `missing type arg returns null photoSource`() {
        val vm = createViewModel(type = null, source = "https://example.com/photo.jpg")
        assertNull(vm.photoSource)
    }

    @Test
    fun `missing source arg returns null photoSource`() {
        val vm = createViewModel(type = Screen.PhotoPreview.TYPE_REMOTE, source = null)
        assertNull(vm.photoSource)
    }

    @Test
    fun `both args missing returns null photoSource`() {
        val vm = createViewModel(type = null, source = null)
        assertNull(vm.photoSource)
    }

    // -------------------------------------------------------------------------
    // Unknown type
    // -------------------------------------------------------------------------

    @Test
    fun `unknown type returns null photoSource`() {
        val vm = createViewModel(type = "unknown", source = "https://example.com/photo.jpg")
        assertNull(vm.photoSource)
    }
}