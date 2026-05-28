package com.jiahan.smartcamera.database.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.jiahan.smartcamera.domain.HomeNote
import com.jiahan.smartcamera.domain.MediaDetail
import java.time.Instant

/**
 * Notes Table in Room Database.
 */
@Entity(tableName = "notes")
data class DatabaseNote(
    @PrimaryKey
    @ColumnInfo(name = "document_path")
    val documentPath: String,
    @ColumnInfo(name = "text") val text: String?,
    @ColumnInfo(name = "created_date") val createdDate: Long?,
    @ColumnInfo(name = "favorite") val favorite: Boolean,
    @ColumnInfo(name = "media_list") val mediaList: List<MediaDetail>?,
    @ColumnInfo(name = "username") val username: String,
    @ColumnInfo(name = "profile_picture_url") val profilePictureUrl: String?,
)

fun DatabaseNote.toHomeNote(): HomeNote = HomeNote(
    text = text,
    createdDate = createdDate?.let { Instant.ofEpochMilli(it) },
    documentPath = documentPath,
    favorite = favorite,
    mediaList = mediaList,
    username = username,
    profilePictureUrl = profilePictureUrl,
)

fun HomeNote.toDatabaseNote(): DatabaseNote = DatabaseNote(
    documentPath = documentPath,
    text = text,
    createdDate = createdDate?.toEpochMilli(),
    favorite = favorite,
    mediaList = mediaList,
    username = username,
    profilePictureUrl = profilePictureUrl,
)
