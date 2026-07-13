package com.jiahan.smartcamera.di

import com.jiahan.smartcamera.note.NoteHandler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

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
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}