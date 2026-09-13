package com.jiahan.smartcamera.home

import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.data.repository.RemoteConfigRepository
import com.jiahan.smartcamera.note.NoteErrorReporter
import com.jiahan.smartcamera.note.NoteShareDelegate
import com.jiahan.smartcamera.util.ErrorHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * What Hilt instantiates for [HomeViewModel], adding the annotations and nothing else -- the
 * arrangement `HiltExploreViewModel` records the reasons for. The two note delegates come from
 * :core:common's `NoteDelegateModule`, one of each for this ViewModel.
 */
@HiltViewModel
class HiltHomeViewModel @Inject constructor(
    noteRepository: NoteRepository,
    noteErrorReporter: NoteErrorReporter,
    noteShare: NoteShareDelegate,
    errorHandler: ErrorHandler,
    remoteConfigRepository: RemoteConfigRepository,
) : HomeViewModel(
    noteRepository,
    noteErrorReporter,
    noteShare,
    errorHandler,
    remoteConfigRepository,
)