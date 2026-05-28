package com.jiahan.smartcamera.data.di

import com.jiahan.smartcamera.data.repository.AnalyticsRepository
import com.jiahan.smartcamera.data.repository.AuthRepository
import com.jiahan.smartcamera.data.repository.DefaultAuthRepository
import com.jiahan.smartcamera.data.repository.DefaultNoteRepository
import com.jiahan.smartcamera.data.repository.DefaultUserRepository
import com.jiahan.smartcamera.data.repository.FirebaseAnalyticsRepository
import com.jiahan.smartcamera.data.repository.FirebaseRemoteConfigRepository
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.data.repository.RemoteConfigRepository
import com.jiahan.smartcamera.data.repository.UserRepository
import com.jiahan.smartcamera.data.datastore.DefaultUserPreferencesRepository
import com.jiahan.smartcamera.data.datastore.UserPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindRemoteConfigRepository(
        firebaseRemoteConfigRepository: FirebaseRemoteConfigRepository
    ): RemoteConfigRepository

    @Binds
    @Singleton
    abstract fun bindAnalyticsRepository(
        firebaseAnalyticsRepository: FirebaseAnalyticsRepository
    ): AnalyticsRepository

    @Binds
    @Singleton
    abstract fun bindNoteRepository(
        defaultNoteRepository: DefaultNoteRepository
    ): NoteRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        defaultAuthRepository: DefaultAuthRepository
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(
        defaultUserRepository: DefaultUserRepository
    ): UserRepository

    @Binds
    @Singleton
    abstract fun bindUserPreferencesRepository(
        defaultUserPreferencesRepository: DefaultUserPreferencesRepository
    ): UserPreferencesRepository
}