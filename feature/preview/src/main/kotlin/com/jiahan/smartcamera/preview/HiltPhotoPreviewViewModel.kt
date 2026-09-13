package com.jiahan.smartcamera.preview

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.jiahan.smartcamera.data.repository.MediaCacheRepository
import com.jiahan.smartcamera.domain.MediaUri
import com.jiahan.smartcamera.util.ErrorHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * What Hilt instantiates for [PhotoPreviewViewModel], and where [PhotoPreviewRoute] is decoded -- the
 * arrangement `HiltEditNoteViewModel` records the reasons for. The decode goes one step further than
 * a note id: it resolves the route's [MediaSourceType] into a [PhotoSource], so neither the enum nor
 * its `Keep` annotation has to leave the route file.
 *
 * Unlike the note screens' decodes, this one still runs on the JVM. No navigation test reaches a
 * media preview, so `HiltPhotoPreviewViewModelTest` builds this class from a `SavedStateHandle` under
 * Robolectric.
 */
@HiltViewModel
class HiltPhotoPreviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    errorHandler: ErrorHandler,
    mediaCacheRepository: MediaCacheRepository,
) : PhotoPreviewViewModel(
    savedStateHandle.toRoute<PhotoPreviewRoute>().toPhotoSource(),
    errorHandler,
    mediaCacheRepository,
)

private fun PhotoPreviewRoute.toPhotoSource(): PhotoSource = when (type) {
    MediaSourceType.LOCAL -> PhotoSource.LocalUri(MediaUri(source))
    MediaSourceType.REMOTE -> PhotoSource.RemoteUrl(source)
}