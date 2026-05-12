package com.jiahan.smartcamera.database.converter

import androidx.room.TypeConverter
import com.jiahan.smartcamera.domain.DetectedLabel
import com.jiahan.smartcamera.domain.DetectedObject
import com.jiahan.smartcamera.domain.MediaDetail
import org.json.JSONArray
import org.json.JSONObject

class DatabaseConverters {

    private companion object {
        const val PHOTO_URL = "photoUrl"
        const val VIDEO_URL = "videoUrl"
        const val THUMBNAIL_URL = "thumbnailUrl"
        const val IS_VIDEO = "isVideo"
        const val GENERATED_TEXT = "generatedText"
        const val GENERATED_OBJECTS = "generatedObjects"
        const val GENERATED_LABELS = "generatedLabels"
        const val OBJECT_NAME = "objectName"
        const val LABEL = "label"
        const val SCORE = "score"
    }

    @TypeConverter
    fun fromMediaList(mediaList: List<MediaDetail>?): String? {
        mediaList ?: return null
        val array = JSONArray()
        for (media in mediaList) {
            val obj = JSONObject()
            media.photoUrl?.let { obj.put(PHOTO_URL, it) }
            media.videoUrl?.let { obj.put(VIDEO_URL, it) }
            media.thumbnailUrl?.let { obj.put(THUMBNAIL_URL, it) }
            obj.put(IS_VIDEO, media.isVideo)
            media.generatedText?.let { obj.put(GENERATED_TEXT, JSONArray(it)) }
            media.generatedObjects?.let { objects ->
                val arr = JSONArray()
                for (o in objects) {
                    arr.put(JSONObject().put(OBJECT_NAME, o.objectName).put(SCORE, o.score))
                }
                obj.put(GENERATED_OBJECTS, arr)
            }
            media.generatedLabels?.let { labels ->
                val arr = JSONArray()
                for (l in labels) {
                    arr.put(JSONObject().put(LABEL, l.label).put(SCORE, l.score))
                }
                obj.put(GENERATED_LABELS, arr)
            }
            array.put(obj)
        }
        return array.toString()
    }

    @TypeConverter
    fun toMediaList(json: String?): List<MediaDetail>? {
        json ?: return null
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            MediaDetail(
                photoUrl = obj.optString(PHOTO_URL).ifEmpty { null },
                videoUrl = obj.optString(VIDEO_URL).ifEmpty { null },
                thumbnailUrl = obj.optString(THUMBNAIL_URL).ifEmpty { null },
                isVideo = obj.optBoolean(IS_VIDEO, false),
                generatedText = obj.optJSONArray(GENERATED_TEXT)?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                },
                generatedObjects = obj.optJSONArray(GENERATED_OBJECTS)?.let { arr ->
                    (0 until arr.length()).map { j ->
                        val o = arr.getJSONObject(j)
                        DetectedObject(
                            objectName = o.getString(OBJECT_NAME),
                            score = o.getDouble(SCORE),
                        )
                    }
                },
                generatedLabels = obj.optJSONArray(GENERATED_LABELS)?.let { arr ->
                    (0 until arr.length()).map { j ->
                        val l = arr.getJSONObject(j)
                        DetectedLabel(
                            label = l.getString(LABEL),
                            score = l.getDouble(SCORE),
                        )
                    }
                },
            )
        }
    }
}
