package com.jiahan.smartcamera

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

//@Fts4
@Entity(tableName = "user")
data class UserData(
    @PrimaryKey
    @ColumnInfo(name = "imageid")
    var id: String,
//    @Bindable
    @ColumnInfo(name = "image_text")
    var imagetext: String,
    var timestamp: Long?
)