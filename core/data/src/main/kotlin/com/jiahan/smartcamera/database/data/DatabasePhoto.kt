package com.jiahan.smartcamera.database.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Photo Table in Room Database
 */
@Entity(tableName = "photos")
data class DatabasePhoto(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "original_name") val originalName: String,
    @ColumnInfo(name = "photo_path") val path: String,
    @ColumnInfo(name = "save_date") val saveDate: Long,
)