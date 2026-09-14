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
import com.jiahan.smartcamera.data.DefaultLocalUserDataCleaner
import com.jiahan.smartcamera.data.LocalUserDataCleaner
import com.jiahan.smartcamera.database.dao.NoteDao
import com.jiahan.smartcamera.di.ApplicationScope
import com.jiahan.smartcamera.di.DebugBuild
import com.jiahan.smartcamera.util.ErrorHandler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.gitlive.firebase.analytics.FirebaseAnalytics
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.functions.FirebaseFunctions
import dev.gitlive.firebase.messaging.FirebaseMessaging
import dev.gitlive.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    // Unscoped, as the Inject constructor it replaced was: the class holds no state.
    @Binds
    abstract fun bindLocalUserDataCleaner(
        defaultLocalUserDataCleaner: DefaultLocalUserDataCleaner
    ): LocalUserDataCleaner

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

        // Provided for the same reason: DefaultPhotoRepository is in :core:firebase's commonMain,
        // on GitLive's multiplatform FirebaseFunctions rather than the Android SDK's.
        @Provides
        @Singleton
        fun providePhotoRepository(
            functions: FirebaseFunctions
        ): PhotoRepository = DefaultPhotoRepository(functions)

        // Also in :core:firebase. The application scope runs the settings and defaults the Android
        // SDK used to apply asynchronously from the class's init block.
        @Provides
        @Singleton
        fun provideRemoteConfigRepository(
            remoteConfig: FirebaseRemoteConfig,
            errorHandler: ErrorHandler,
            @DebugBuild isDebugBuild: Boolean,
            @ApplicationScope scope: CoroutineScope,
        ): RemoteConfigRepository =
            FirebaseRemoteConfigRepository(remoteConfig, errorHandler, isDebugBuild, scope)

        // Also in :core:firebase.
        @Provides
        @Singleton
        fun provideAnalyticsRepository(
            analytics: FirebaseAnalytics
        ): AnalyticsRepository = FirebaseAnalyticsRepository(analytics)

        // Also in :core:firebase, on GitLive's FirebaseAuth; it reaches the local stores only
        // through the LocalUserDataCleaner interface bound above.
        @Provides
        @Singleton
        fun provideAuthRepository(
            auth: FirebaseAuth,
            functions: FirebaseFunctions,
            userRepository: UserRepository,
            localUserDataCleaner: LocalUserDataCleaner,
            errorHandler: ErrorHandler,
        ): AuthRepository = DefaultAuthRepository(
            auth = auth,
            functions = functions,
            userRepository = userRepository,
            localUserDataCleaner = localUserDataCleaner,
            errorHandler = errorHandler,
        )

        // Also in :core:firebase, on GitLive's Auth, Firestore, Functions and Messaging; it builds its
        // Storage instance itself, from the bucket Remote Config names.
        @Provides
        @Singleton
        fun provideUserRepository(
            auth: FirebaseAuth,
            firestore: FirebaseFirestore,
            functions: FirebaseFunctions,
            messaging: FirebaseMessaging,
            remoteConfigRepository: RemoteConfigRepository,
        ): UserRepository = DefaultUserRepository(
            auth = auth,
            firestore = firestore,
            functions = functions,
            messaging = messaging,
            remoteConfigRepository = remoteConfigRepository,
        )

        // Also in :core:firebase, on GitLive's Firestore and Functions. It writes every fetch into
        // the Room mirror through NoteDao, which DatabaseModule provides from :core:database.
        @Provides
        @Singleton
        fun provideNoteRepository(
            authRepository: AuthRepository,
            firestore: FirebaseFirestore,
            functions: FirebaseFunctions,
            noteDao: NoteDao,
            errorHandler: ErrorHandler,
        ): NoteRepository = DefaultNoteRepository(
            authRepository = authRepository,
            firestore = firestore,
            functions = functions,
            noteDao = noteDao,
            errorHandler = errorHandler,
        )
    }
}