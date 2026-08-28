package com.jiahan.smartcamera.domain

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

data class DetectedObject(
    val objectName: String,
    val score: Double
)

data class DetectedLabel(
    val label: String,
    val score: Double
)