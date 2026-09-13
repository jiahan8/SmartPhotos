package com.jiahan.smartcamera.util.di

import com.jiahan.smartcamera.util.DefaultErrorHandler
import com.jiahan.smartcamera.util.ErrorHandler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UtilModule {

    @Provides
    @Singleton
    fun provideErrorHandler(): ErrorHandler {
        return DefaultErrorHandler()
    }
}