package com.jiahan.smartcamera.preview

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * Navigation routes for the three preview screens. Routes live in the feature package that owns
 * them rather than in one central hierarchy -- see `smartPhotosNavGraph`.
 *
 * Each is read back with `savedStateHandle.toRoute<...>()` by the `Hilt*ViewModel` subclass Hilt
 * builds for its screen, which hands the shared ViewModel plain values -- a `noteId`, or a
 * `PhotoSource`/`VideoSource` resolved from `type` and `source`. So the property names below are the
 * argument names Navigation serializes: renaming one changes the generated route pattern, and the
 * key `HiltPhotoPreviewViewModelTest` and `HiltVideoPreviewViewModelTest` build their
 * `SavedStateHandle` with.
 */
@Serializable
data class PhotoPreviewRoute(val type: MediaSourceType, val source: String)

@Serializable
data class VideoPreviewRoute(val type: MediaSourceType, val source: String)

@Serializable
data class NotePreviewRoute(val noteId: String)

// Navigation Compose's type-safe routes resolve enum route arguments via Class.forName() at
// runtime (see the AndroidX Navigation NavType.EnumType source); @Keep stops R8 from renaming or
// removing this class under minification, which would otherwise break that lookup in release builds.
@Keep
@Serializable
enum class MediaSourceType { LOCAL, REMOTE }