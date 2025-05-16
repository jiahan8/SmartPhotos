package com.jiahan.smartcamera.database.data

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

/**
 * Photo Table in Room Database
 */
@Parcelize
@Entity(tableName = "photos")
data class DatabasePhoto(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "original_name") val originalName: String,
    @ColumnInfo(name = "photo_path") val path: String,
    @ColumnInfo(name = "save_date") val saveDate: Long,
) : Parcelable