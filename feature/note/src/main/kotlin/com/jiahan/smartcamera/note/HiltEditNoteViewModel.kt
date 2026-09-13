package com.jiahan.smartcamera.note

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.jiahan.smartcamera.data.repository.AnalyticsRepository
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.util.ErrorHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * What Hilt instantiates for [EditNoteViewModel] -- the arrangement `HiltExploreViewModel` records
 * the reasons for, plus one job the shared class leaves to this edge: decoding [EditNoteRoute] from
 * the [SavedStateHandle] Navigation fills, so the ViewModel receives a plain `noteId`.
 *
 * Not a platform limit: `SavedStateHandle.toRoute` is in navigation-common's `commonMain` as of
 * 2.10. Decoding here keeps [EditNoteRoute] beside its screen, and keeps Navigation and the
 * serialization plugin off :feature:note-viewmodel for the sake of one String. Nothing on the JVM
 * exercises this decode any more -- `SmartPhotosNavigationTest` does, on a device.
 */
@HiltViewModel
class HiltEditNoteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    noteRepository: NoteRepository,
    analyticsRepository: AnalyticsRepository,
    errorHandler: ErrorHandler,
) : EditNoteViewModel(
    savedStateHandle.toRoute<EditNoteRoute>().noteId,
    noteRepository,
    analyticsRepository,
    errorHandler,
)