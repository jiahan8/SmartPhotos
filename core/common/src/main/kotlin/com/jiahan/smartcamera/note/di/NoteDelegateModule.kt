package com.jiahan.smartcamera.note.di

import com.jiahan.smartcamera.data.repository.MediaCacheRepository
import com.jiahan.smartcamera.note.NoteErrorReporter
import com.jiahan.smartcamera.note.NoteShareDelegate
import com.jiahan.smartcamera.util.ErrorHandler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

/**
 * Hilt's half of the two note delegates, which live in :core:domain's `commonMain` and so can carry
 * no annotations of their own.
 *
 * Installed in `ViewModelComponent` -- the one module in the app that is not in
 * `SingletonComponent` -- because the scope is the point. [NoteShareDelegate] reports share
 * failures onto the [NoteErrorReporter] its ViewModel exposes, so within one ViewModel both must
 * receive the same reporter, and across two ViewModels they must not. The scope annotations used to
 * sit on the classes themselves, on `Inject` constructors; these providers are the same bindings
 * with the annotations moved to the Android edge, the way `Hilt<Name>ViewModel` moves a ViewModel's.
 */
@Module
@InstallIn(ViewModelComponent::class)
object NoteDelegateModule {

    @Provides
    @ViewModelScoped
    fun provideNoteErrorReporter(errorHandler: ErrorHandler): NoteErrorReporter =
        NoteErrorReporter(errorHandler)

    @Provides
    @ViewModelScoped
    fun provideNoteShareDelegate(
        mediaCacheRepository: MediaCacheRepository,
        noteErrorReporter: NoteErrorReporter,
    ): NoteShareDelegate = NoteShareDelegate(mediaCacheRepository, noteErrorReporter)
}