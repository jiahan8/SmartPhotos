package com.jiahan.smartcamera.di

import com.jiahan.smartcamera.repository.FirebaseRemoteConfigRepository
import com.jiahan.smartcamera.repository.RemoteConfigRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindRemoteConfigRepository(
        repository: FirebaseRemoteConfigRepository
    ): RemoteConfigRepository
}