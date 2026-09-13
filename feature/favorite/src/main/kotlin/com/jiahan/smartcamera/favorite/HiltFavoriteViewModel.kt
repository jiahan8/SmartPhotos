package com.jiahan.smartcamera.favorite

import com.jiahan.smartcamera.data.repository.AnalyticsRepository
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.note.NoteErrorReporter
import com.jiahan.smartcamera.note.NoteShareDelegate
import com.jiahan.smartcamera.util.ErrorHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * What Hilt instantiates for [FavoriteViewModel], adding the annotations and nothing else -- the
 * arrangement `HiltExploreViewModel` records the reasons for. The two note delegates come from
 * :core:common's `NoteDelegateModule`, one of each for this ViewModel.
 */
@HiltViewModel
class HiltFavoriteViewModel @Inject constructor(
    noteRepository: NoteRepository,
    analyticsRepository: AnalyticsRepository,
    noteErrorReporter: NoteErrorReporter,
    noteShare: NoteShareDelegate,
    errorHandler: ErrorHandler,
) : FavoriteViewModel(
    noteRepository,
    analyticsRepository,
    noteErrorReporter,
    noteShare,
    errorHandler,
)