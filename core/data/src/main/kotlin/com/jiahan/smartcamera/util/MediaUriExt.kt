package com.jiahan.smartcamera.util

import android.net.Uri
import androidx.core.net.toUri
import com.jiahan.smartcamera.domain.MediaUri

/**
 * Wraps a platform [Uri] as the Android-free [MediaUri] that repository contracts accept.
 * Call this at the ViewModel boundary, on the way down into the data layer.
 */
fun Uri.toMediaUri(): MediaUri = MediaUri(toString())

/**
 * Resolves a [MediaUri] back to the platform [Uri] that Android APIs need.
 * Call this inside a `Default*` implementation, or in the UI layer when handing a URI to an
 * activity-result contract or an image loader.
 */
fun MediaUri.toPlatformUri(): Uri = value.toUri()