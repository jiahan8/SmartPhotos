package com.jiahan.smartcamera.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.jiahan.smartcamera.database.converter.DatabaseConverters
import com.jiahan.smartcamera.database.dao.NoteDao
import com.jiahan.smartcamera.database.dao.PhotoDao
import com.jiahan.smartcamera.database.data.DatabaseNote
import com.jiahan.smartcamera.database.data.DatabasePhoto

/**
 * The Room database for this app
 */
@Database(
    entities = [DatabasePhoto::class, DatabaseNote::class], version = 1, exportSchema = true,
)
@TypeConverters(DatabaseConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun photoDao(): PhotoDao
    abstract fun noteDao(): NoteDao
}