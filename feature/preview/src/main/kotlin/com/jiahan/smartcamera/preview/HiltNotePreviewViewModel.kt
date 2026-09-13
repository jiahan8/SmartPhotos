package com.jiahan.smartcamera.preview

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.note.NoteErrorReporter
import com.jiahan.smartcamera.note.NoteShareDelegate
import com.jiahan.smartcamera.util.ErrorHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * What Hilt instantiates for [NotePreviewViewModel], and where [NotePreviewRoute] is decoded -- the
 * arrangement `HiltEditNoteViewModel` records the reasons for. The two note delegates come from
 * :core:common's `NoteDelegateModule`, one of each for this ViewModel.
 *
 * Nothing on the JVM exercises this decode;
 * `SmartPhotosNavigationTest.pendingNoteId_navigatesToNotePreview_andIsConsumed` does, on a device,
 * by following a notification's note id through to the fetch.
 */
@HiltViewModel
class HiltNotePreviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    noteRepository: NoteRepository,
    noteErrorReporter: NoteErrorReporter,
    errorHandler: ErrorHandler,
    noteShare: NoteShareDelegate,
) : NotePreviewViewModel(
    savedStateHandle.toRoute<NotePreviewRoute>().noteId,
    noteRepository,
    noteErrorReporter,
    errorHandler,
    noteShare,
)