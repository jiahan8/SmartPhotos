package com.jiahan.smartcamera.database.di

import android.content.Context
import com.jiahan.smartcamera.database.AppDatabase
import com.jiahan.smartcamera.database.dao.PhotoDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal class DatabaseModule {

    @Singleton
    @Provides
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    fun providePhotoDAO(appDatabase: AppDatabase): PhotoDao {
        return appDatabase.photoDao()
    }
}