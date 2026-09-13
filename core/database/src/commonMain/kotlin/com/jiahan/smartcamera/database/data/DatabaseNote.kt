package com.jiahan.smartcamera.database.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.jiahan.smartcamera.domain.MediaDetail
import com.jiahan.smartcamera.domain.Note
import kotlin.time.Instant

/**
 * Notes Table in Room Database.
 */
@Entity(tableName = "notes")
data class DatabaseNote(
    @PrimaryKey
    @ColumnInfo(name = "note_id") val noteId: String,
    @ColumnInfo(name = "text") val text: String?,
    @ColumnInfo(name = "created_date") val createdDate: Long?,
    @ColumnInfo(name = "favorite") val isFavorite: Boolean,
    @ColumnInfo(name = "media_list") val mediaList: List<MediaDetail>?,
    @ColumnInfo(name = "username") val username: String,
    @ColumnInfo(name = "profile_picture_url") val profilePictureUrl: String?,
)

fun DatabaseNote.toNote(): Note = Note(
    noteId = noteId,
    text = text,
    createdDate = createdDate?.let { Instant.fromEpochMilliseconds(it) },
    isFavorite = isFavorite,
    mediaList = mediaList,
    username = username,
    profilePictureUrl = profilePictureUrl,
)

fun Note.toDatabaseNote(): DatabaseNote = DatabaseNote(
    noteId = noteId,
    text = text,
    createdDate = createdDate?.toEpochMilliseconds(),
    isFavorite = isFavorite,
    mediaList = mediaList,
    username = username,
    profilePictureUrl = profilePictureUrl,
)