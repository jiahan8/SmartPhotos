package com.jiahan.smartcamera.domain

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.time.Instant

@Parcelize
data class HomeNote(
    val text: String? = null,
    val createdDate: Instant? = null,
    val documentPath: String,
    val favorite: Boolean = false,
    val mediaList: List<MediaDetail>? = null,
    val username: String,
    val profilePictureUrl: String? = null
) : Parcelable

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

@Parcelize
data class NoteMediaDetail(
    val photoUri: Uri? = null,
    val videoUri: Uri? = null,
    val thumbnailUri: Uri? = null,
    val isVideo: Boolean = false
) : Parcelable