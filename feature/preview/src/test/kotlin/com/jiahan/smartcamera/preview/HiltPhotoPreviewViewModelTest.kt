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
 * The route decode [HiltPhotoPreviewViewModel] does before handing [PhotoPreviewViewModel] a
 * [PhotoSource]. Everything the ViewModel does with that source is `PhotoPreviewViewModelTest`'s, in
 * :feature:preview-viewmodel's `commonTest`.
 *
 * Robolectric because [androidx.navigation.toRoute]'s internal `RouteDecoder` constructs a real
 * [android.os.Bundle]. The note screens' decodes left the JVM for `SmartPhotosNavigationTest`; this
 * one stays, because no navigation test reaches a media preview, so this is the only place the
 * route's argument names and [MediaSourceType] are read back at all.
 *
 * A plain [Application] stands in for `MyApp` (as in `BaseScreenshotTest`): the real one installs
 * the Firebase App Check provider in `onCreate()`, which throws under Robolectric because no
 * default `FirebaseApp` is initialized there.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class HiltPhotoPreviewViewModelTest {

    private fun createViewModel(type: MediaSourceType, source: String) =
        HiltPhotoPreviewViewModel(
            SavedStateHandle(mapOf("type" to type, "source" to source)),
            FakeErrorHandler(),
            FakeMediaCacheRepository()
        )

    @Test
    fun `remote type with url decodes to RemoteUrl`() {
        val url = "https://example.com/photo.jpg?size=large&id=1"

        val vm = createViewModel(MediaSourceType.REMOTE, url)

        assertEquals(PhotoSource.RemoteUrl(url), vm.photoSource)
    }

    @Test
    fun `local type with uri decodes to LocalUri`() {
        val uriString = "content://media/external/images/media/1"

        val vm = createViewModel(MediaSourceType.LOCAL, uriString)

        assertEquals(PhotoSource.LocalUri(MediaUri(uriString)), vm.photoSource)
    }

    /** A location the picker can hand over that a decoding step would mangle. */
    @Test
    fun `local type preserves a percent-encoded uri`() {
        val uriString = "content://media/external/images/media/My%20Photo%20%281%29.jpg"

        val vm = createViewModel(MediaSourceType.LOCAL, uriString)

        assertEquals(PhotoSource.LocalUri(MediaUri(uriString)), vm.photoSource)
    }
}