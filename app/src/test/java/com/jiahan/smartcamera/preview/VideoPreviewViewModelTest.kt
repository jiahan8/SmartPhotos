package com.jiahan.smartcamera.preview

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jiahan.smartcamera.navigation.MediaSourceType
import com.jiahan.smartcamera.util.ErrorHandler
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [VideoPreviewViewModel] parses its typed nav route via [androidx.navigation.toRoute], whose
 * internal [androidx.navigation.serialization.RouteDecoder] constructs a real [android.os.Bundle]
 * — that needs Robolectric's shadow to work outside a real Android runtime, hence Robolectric here.
 */
@RunWith(AndroidJUnit4::class)
class VideoPreviewViewModelTest {

    private fun createViewModel(type: MediaSourceType, source: String): VideoPreviewViewModel {
        val savedStateHandle =
            SavedStateHandle(mapOf("type" to type, "source" to source))
        return VideoPreviewViewModel(savedStateHandle, mockk<ErrorHandler>(relaxed = true))
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
    fun `remote type with url returns RemoteUrl`() {
        val url = "https://example.com/clip.mp4?token=a/b+c"
        val vm = createViewModel(MediaSourceType.REMOTE, url)

        val source = vm.videoSource
        assertTrue(source is VideoSource.RemoteUrl)
        assertEquals(url, (source as VideoSource.RemoteUrl).url)
    }

    // -------------------------------------------------------------------------
    // Local URI
    // -------------------------------------------------------------------------

    @Test
    fun `local type with uri returns LocalUri`() {
        val uriString = "content://media/external/video/42"
        val mockUri = mockk<Uri>()
        every { Uri.parse(uriString) } returns mockUri

        val vm = createViewModel(MediaSourceType.LOCAL, uriString)

        val source = vm.videoSource
        assertTrue(source is VideoSource.LocalUri)
        assertEquals(mockUri, (source as VideoSource.LocalUri).uri)
    }
}
