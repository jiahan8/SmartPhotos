package com.jiahan.smartcamera.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.jiahan.smartcamera.database.converter.DatabaseConverters
import com.jiahan.smartcamera.database.dao.NoteDao
import com.jiahan.smartcamera.database.dao.PhotoDao
import com.jiahan.smartcamera.database.data.DatabaseNote
import com.jiahan.smartcamera.database.data.DatabasePhoto

private const val DATABASE_NAME = "photo-database"

/**
 * The Room database for this app
 */
@Database(
    entities = [DatabasePhoto::class, DatabaseNote::class], version = 1, exportSchema = false,
)
@TypeConverters(DatabaseConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun photoDao(): PhotoDao
    abstract fun noteDao(): NoteDao

    companion object {

        // Manually guarded singleton — Hilt's @Singleton in DatabaseModule delegates here
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }
        }

        // Create the database
        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME).build()
        }
    }
}