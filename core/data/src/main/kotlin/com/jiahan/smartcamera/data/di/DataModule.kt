package com.jiahan.smartcamera.data.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.jiahan.smartcamera.data.repository.AnalyticsRepository
import com.jiahan.smartcamera.data.repository.AppUpdateRepository
import com.jiahan.smartcamera.data.repository.AuthRepository
import com.jiahan.smartcamera.data.repository.DefaultAppUpdateRepository
import com.jiahan.smartcamera.data.repository.DefaultAuthRepository
import com.jiahan.smartcamera.data.repository.DefaultMediaFileRepository
import com.jiahan.smartcamera.data.repository.DefaultMediaUploadRepository
import com.jiahan.smartcamera.data.repository.DefaultNoteRepository
import com.jiahan.smartcamera.data.repository.DefaultPhotoRepository
import com.jiahan.smartcamera.data.repository.DefaultUserRepository
import com.jiahan.smartcamera.data.repository.FirebaseAnalyticsRepository
import com.jiahan.smartcamera.data.repository.FirebaseRemoteConfigRepository
import com.jiahan.smartcamera.data.repository.MediaCacheRepository
import com.jiahan.smartcamera.data.repository.MediaCaptureRepository
import com.jiahan.smartcamera.data.repository.MediaFileRepository
import com.jiahan.smartcamera.data.repository.MediaUploadRepository
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.data.repository.PhotoRepository
import com.jiahan.smartcamera.data.repository.RemoteConfigRepository
import com.jiahan.smartcamera.data.repository.UserRepository
import com.jiahan.smartcamera.data.datastore.DefaultUserPreferencesRepository
import com.jiahan.smartcamera.data.datastore.UserPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
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
    abstract fun bindMediaFileRepository(
        defaultMediaFileRepository: DefaultMediaFileRepository
    ): MediaFileRepository

    // The same class again, twice. DefaultMediaFileRepository implements MediaCacheRepository and
    // MediaCaptureRepository too: the parts of its contract that return a MediaUri, and so the parts
    // that could move to :core:domain.
    @Binds
    @Singleton
    abstract fun bindMediaCacheRepository(
        defaultMediaFileRepository: DefaultMediaFileRepository
    ): MediaCacheRepository

    @Binds
    @Singleton
    abstract fun bindMediaCaptureRepository(
        defaultMediaFileRepository: DefaultMediaFileRepository
    ): MediaCaptureRepository

    @Binds
    @Singleton
    abstract fun bindMediaUploadRepository(
        defaultMediaUploadRepository: DefaultMediaUploadRepository
    ): MediaUploadRepository

    @Binds
    @Singleton
    abstract fun bindPhotoRepository(
        defaultPhotoRepository: DefaultPhotoRepository
    ): PhotoRepository

    @Binds
    @Singleton
    abstract fun bindAppUpdateRepository(
        defaultAppUpdateRepository: DefaultAppUpdateRepository
    ): AppUpdateRepository

    companion object {
        // Provided rather than bound: DefaultUserPreferencesRepository is in :core:datastore's
        // commonMain, where no Inject annotation resolves, so Hilt cannot build it from its
        // constructor. Declared here rather than beside the DataStore in DataStoreModule so that
        // SmartPhotosNavigationTest's UninstallModules(DataModule::class) still removes it with
        // every other repository binding it replaces with a fake.
        @Provides
        @Singleton
        fun provideUserPreferencesRepository(
            dataStore: DataStore<Preferences>
        ): UserPreferencesRepository = DefaultUserPreferencesRepository(dataStore)
    }
}