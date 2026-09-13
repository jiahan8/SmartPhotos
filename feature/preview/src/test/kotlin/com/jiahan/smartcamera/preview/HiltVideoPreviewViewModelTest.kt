package com.jiahan.smartcamera.preview

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jiahan.smartcamera.domain.MediaUri
import com.jiahan.smartcamera.fake.FakeErrorHandler
import com.jiahan.smartcamera.fake.FakeMediaCacheRepository
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The route decode [HiltVideoPreviewViewModel] does before handing [VideoPreviewViewModel] a
 * [VideoSource]; everything after it is `VideoPreviewViewModelTest`'s. Robolectric, and kept on the
 * JVM at all, for the reasons `HiltPhotoPreviewViewModelTest` gives.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class HiltVideoPreviewViewModelTest {

    private fun createViewModel(type: MediaSourceType, source: String) =
        HiltVideoPreviewViewModel(
            SavedStateHandle(mapOf("type" to type, "source" to source)),
            FakeErrorHandler(),
            FakeMediaCacheRepository()
        )

    @Test
    fun `remote type with url decodes to RemoteUrl`() {
        val url = "https://example.com/clip.mp4?token=a/b+c"

        val vm = createViewModel(MediaSourceType.REMOTE, url)

        assertEquals(VideoSource.RemoteUrl(url), vm.videoSource)
    }

    @Test
    fun `local type with uri decodes to LocalUri`() {
        val uriString = "content://media/external/video/media/42"

        val vm = createViewModel(MediaSourceType.LOCAL, uriString)

        assertEquals(VideoSource.LocalUri(MediaUri(uriString)), vm.videoSource)
    }

    /** A location the picker can hand over that a decoding step would mangle. */
    @Test
    fun `local type preserves a percent-encoded uri`() {
        val uriString = "content://media/external/video/media/My%20Clip%20%281%29.mp4"

        val vm = createViewModel(MediaSourceType.LOCAL, uriString)

        assertEquals(VideoSource.LocalUri(MediaUri(uriString)), vm.videoSource)
    }
}