package com.jiahan.smartcamera.domain

/**
 * A local media location, carried as the string form of the platform URI it came from.
 *
 * Repository *interfaces* and domain models take this instead of `android.net.Uri` so the
 * data-layer contracts stay free of Android types, which is what the Separation of concerns and
 * Kotlin Multiplatform readiness rules in AGENTS.md require. It mirrors how [MediaDetail] already
 * carries remote locations as plain `String` URLs.
 *
 * Conversion to and from `android.net.Uri` lives in `util/MediaUriExt.kt` and belongs either at the
 * ViewModel boundary or inside a `Default*` repository implementation — never in a contract.
 */
@JvmInline
value class MediaUri(val value: String)