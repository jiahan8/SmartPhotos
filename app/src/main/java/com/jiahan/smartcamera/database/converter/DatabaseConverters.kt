package com.jiahan.smartcamera.database.converter

import androidx.room.TypeConverter
import com.jiahan.smartcamera.domain.MediaDetail
import kotlinx.serialization.json.Json

/**
 * Room type converters for [MediaDetail], persisted as JSON in the `notes.media_list` column.
 *
 * The keys are the property names of [MediaDetail] and its nested types, which is the same shape
 * the previous hand-rolled `org.json` implementation wrote — rows cached by older builds decode
 * unchanged. That also makes those property names a persisted format; see [MediaDetail].
 */
class DatabaseConverters {

    private companion object {
        // ignoreUnknownKeys tolerates rows written by a build that models fields this one doesn't,
        // so adding a field to MediaDetail can't make already-cached notes undecodable.
        val json = Json { ignoreUnknownKeys = true }
    }

    @TypeConverter
    fun fromMediaList(mediaList: List<MediaDetail>?): String? =
        mediaList?.let { json.encodeToString(it) }

    @TypeConverter
    fun toMediaList(value: String?): List<MediaDetail>? =
        value?.let { json.decodeFromString<List<MediaDetail>>(it) }
}