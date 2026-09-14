package com.jiahan.smartcamera.data.repository

import com.jiahan.smartcamera.domain.DetectedLabel
import com.jiahan.smartcamera.domain.DetectedObject
import com.jiahan.smartcamera.domain.MediaDetail
import dev.gitlive.firebase.firestore.Timestamp
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Instant

/**
 * A `user/{uid}/note/{noteId}` document, as the createNote function writes it and the Vision trigger
 * annotates it.
 *
 * GitLive reads a document only through kotlinx.serialization, so this replaces the Android SDK
 * reader's `getString`/`get(...) as? List<*>` calls. Every field is defaulted, so an absent one reads
 * as absent rather than failing the page.
 */
@Serializable
internal class FirestoreNote(
    val text: String? = null,
    @SerialName("user_id") val userId: String? = null,
    val created: Timestamp? = null,
    val favorite: Boolean? = null,
    @SerialName("media_list") val mediaList: List<FirestoreMedia>? = null,
)

/** The author fields of a `user/{uid}` document. */
@Serializable
internal class FirestoreAuthor(
    val username: String? = null,
    @SerialName("profile_picture") val profilePicture: String? = null,
)

/**
 * One `media_list` entry.
 *
 * The old reader cast each field `as?`, so a mistyped one dropped only itself. GitLive's decoder
 * reads any value as a string, but throws on a mistyped number or boolean -- which would fail the
 * note, and the page it is on, over one score. [LenientBooleanSerializer] and [LenientScoreSerializer]
 * keep a bad value's reach where it was.
 */
@Serializable
internal class FirestoreMedia(
    val photoUrl: String? = null,
    val videoUrl: String? = null,
    val thumbnailUrl: String? = null,
    @Serializable(with = LenientBooleanSerializer::class) val video: Boolean = false,
    val generatedText: List<String?>? = null,
    val generatedObjects: List<FirestoreDetection>? = null,
    val generatedLabels: List<FirestoreDetection>? = null,
    val generatedLandmarks: List<FirestoreDetection>? = null,
    val generatedLogos: List<FirestoreDetection>? = null,
)

/** A Vision detection: an object name or a label, and its score. Kept only when both are present. */
@Serializable
internal class FirestoreDetection(
    @SerialName("object") val objectName: String? = null,
    val label: String? = null,
    @Serializable(with = LenientScoreSerializer::class) val score: Double? = null,
)

/**
 * A score that reads as null -- dropping its detection -- when it is absent or not a number.
 *
 * Any number is accepted, a whole one included. Firestore stores an integral JavaScript number as an
 * integer, which the Android SDK hands over as a `Long` and the old `as? Double` cast rejected; that
 * one reading changed, and `DefaultNoteRepositoryTest` pins it.
 */
@OptIn(ExperimentalSerializationApi::class)
internal object LenientScoreSerializer : KSerializer<Double?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientScore", PrimitiveKind.DOUBLE).nullable

    override fun deserialize(decoder: Decoder): Double? =
        runCatching { decoder.decodeDouble() }.getOrNull()

    override fun serialize(encoder: Encoder, value: Double?) {
        if (value == null) encoder.encodeNull() else encoder.encodeDouble(value)
    }
}

internal fun FirestoreMedia.toMediaDetail() = MediaDetail(
    photoUrl = photoUrl,
    videoUrl = videoUrl,
    thumbnailUrl = thumbnailUrl,
    isVideo = video,
    generatedTexts = generatedText?.filterNotNull(),
    generatedObjects = generatedObjects?.mapNotNull { detection ->
        val name = detection.objectName
        val score = detection.score
        if (name != null && score != null) DetectedObject(name, score) else null
    },
    generatedLabels = generatedLabels?.toDetectedLabels(),
    generatedLandmarks = generatedLandmarks?.toDetectedLabels(),
    generatedLogos = generatedLogos?.toDetectedLabels(),
)

private fun List<FirestoreDetection>.toDetectedLabels() = mapNotNull { detection ->
    val label = detection.label
    val score = detection.score
    if (label != null && score != null) DetectedLabel(label, score) else null
}

/** Truncated to milliseconds, as the Android SDK's `Timestamp.toDate()` was -- all the mirror keeps. */
internal fun Timestamp.toInstant(): Instant =
    Instant.fromEpochMilliseconds(seconds * 1_000 + nanoseconds / 1_000_000)