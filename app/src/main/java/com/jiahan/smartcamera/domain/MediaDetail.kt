package com.jiahan.smartcamera.domain

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class MediaDetail(
    val photoUrl: String? = null,
    val videoUrl: String? = null,
    val thumbnailUrl: String? = null,
    val isVideo: Boolean = false,
    val generatedText: List<String>? = null,
    val generatedObjects: List<DetectedObject>? = null,
    val generatedLabels: List<DetectedLabel>? = null
) : Parcelable

@Parcelize
data class DetectedObject(
    val objectName: String,
    val score: Double
) : Parcelable

@Parcelize
data class DetectedLabel(
    val label: String,
    val score: Double
) : Parcelable