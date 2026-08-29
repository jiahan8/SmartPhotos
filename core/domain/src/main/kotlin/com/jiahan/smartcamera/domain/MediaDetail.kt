package com.jiahan.smartcamera.domain

import kotlinx.serialization.Serializable

/**
 * These types are persisted: `DatabaseConverters` stores a note's media list as
 * `kotlinx.serialization` JSON in the `notes.media_list` column, keyed by property name. Renaming a
 * property therefore changes the on-disk format and makes already-cached rows undecodable — use
 * `@SerialName` to keep the stored key stable if one ever has to be renamed.
 */
@Serializable
data class MediaDetail(
    val photoUrl: String? = null,
    val videoUrl: String? = null,
    val thumbnailUrl: String? = null,
    val isVideo: Boolean = false,
    val generatedText: List<String>? = null,
    val generatedObjects: List<DetectedObject>? = null,
    val generatedLabels: List<DetectedLabel>? = null,
    val generatedLandmarks: List<DetectedLabel>? = null,
    val generatedLogos: List<DetectedLabel>? = null
)

@Serializable
data class DetectedObject(
    val objectName: String,
    val score: Double
)

@Serializable
data class DetectedLabel(
    val label: String,
    val score: Double
)