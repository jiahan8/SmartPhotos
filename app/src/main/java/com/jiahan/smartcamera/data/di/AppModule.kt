package com.jiahan.smartcamera.data.di

import com.jiahan.smartcamera.note.NoteHandler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideNoteHandler(): NoteHandler = NoteHandler()
}