package com.jiahan.smartcamera

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [UserData::class], version = 1, exportSchema = false)
abstract class UserDatabase : RoomDatabase() {

    abstract fun userDAO(): UserDAO

    companion object {
        private val LOG_TAG = UserDatabase::class.java.simpleName
        private val LOCK = Any()
        private const val DATABASE_NAME = "user"
        private var sInstance: UserDatabase? = null
        @JvmStatic
        fun getInstance(context: Context): UserDatabase? {
            if (sInstance == null) {
                synchronized(LOCK) {
                    sInstance = Room.databaseBuilder(context.applicationContext, UserDatabase::class.java, DATABASE_NAME)
                            .build()
                }
            }
            return sInstance
        }
    }
}