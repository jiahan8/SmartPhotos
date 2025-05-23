package com.jiahan.smartcamera.database.data

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

/**
 * Note Table in Remote Database
 */
@Parcelize
@Entity(tableName = "note")
data class DatabaseNote(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "created") val createdDate: Long,
) : Parcelable