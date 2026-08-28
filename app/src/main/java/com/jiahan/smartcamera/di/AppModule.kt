package com.jiahan.smartcamera.di

import android.content.Context
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.jiahan.smartcamera.note.IncomingShareHandler
import com.jiahan.smartcamera.note.NoteHandler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.time.Clock

/**
 * Qualifier for an application-scoped [CoroutineScope] backed by a [SupervisorJob].
 * Use this instead of [kotlinx.coroutines.GlobalScope] for fire-and-forget work that
 * must outlive any single ViewModel or Screen but still be tied to the process lifetime.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * Qualifier for the IO [CoroutineDispatcher]. Inject this instead of referencing
 * [Dispatchers.IO] directly so tests can substitute a `TestDispatcher`.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideNoteHandler(): NoteHandler = NoteHandler()

    @Provides
    @Singleton
    fun provideIncomingShareHandler(): IncomingShareHandler = IncomingShareHandler()

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    /**
     * The system clock. Inject this wherever "now" feeds a decision a test should be able to
     * pin — see [com.jiahan.smartcamera.MainViewModel], which derives from it the activity day
     * the streak backend keys on. Incidental defaults, such as a fallback for a missing server
     * timestamp, can still read [Clock.System] directly. Unscoped because [Clock.System] is
     * already an object.
     */
    @Provides
    fun provideClock(): Clock = Clock.System

    @Provides
    @Singleton
    fun provideAppUpdateManager(@ApplicationContext context: Context): AppUpdateManager =
        AppUpdateManagerFactory.create(context)
}