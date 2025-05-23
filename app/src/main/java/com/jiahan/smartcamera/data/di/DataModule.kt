package com.jiahan.smartcamera.data.di

import com.jiahan.smartcamera.data.repository.AnalyticsRepository
import com.jiahan.smartcamera.data.repository.DefaultNoteRepository
import com.jiahan.smartcamera.data.repository.DefaultSearchRepository
import com.jiahan.smartcamera.data.repository.FirebaseAnalyticsRepository
import com.jiahan.smartcamera.data.repository.FirebaseRemoteConfigRepository
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.data.repository.SearchRepository
import com.jiahan.smartcamera.data.repository.RemoteConfigRepository
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
    abstract fun bindSearchRepository(
        defaultSearchRepository: DefaultSearchRepository
    ): SearchRepository

    @Binds
    abstract fun bindNoteRepository(
        defaultNoteRepository: DefaultNoteRepository
    ): NoteRepository
}