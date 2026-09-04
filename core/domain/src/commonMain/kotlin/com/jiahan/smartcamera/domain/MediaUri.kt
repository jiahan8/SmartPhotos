package com.jiahan.smartcamera.domain

// Explicit, unlike on the JVM: `kotlin.jvm.*` is a default import only for a JVM compilation, and
// commonMain does not get it. The annotation itself is in the common stdlib and is required on
// every common value class, so this is an import to add rather than a JVM detail to remove.
import kotlin.jvm.JvmInline

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