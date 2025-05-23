package com.jiahan.smartcamera.data.di

import com.jiahan.smartcamera.repository.AnalyticsRepository
import com.jiahan.smartcamera.repository.FirebaseAnalyticsRepository
import com.jiahan.smartcamera.repository.FirebaseRemoteConfigRepository
import com.jiahan.smartcamera.repository.RemoteConfigRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FirebaseRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindFirebaseRemoteConfigRepository(
        firebaseRemoteConfigRepository: FirebaseRemoteConfigRepository
    ): RemoteConfigRepository

    @Binds
    @Singleton
    abstract fun bindFirebaseAnalyticsRepository(
        firebaseAnalyticsRepository: FirebaseAnalyticsRepository
    ): AnalyticsRepository
}